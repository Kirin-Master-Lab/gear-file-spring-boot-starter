package com.gear.file.util;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelDataConvertException;
import com.alibaba.excel.metadata.CellExtra;
import com.alibaba.excel.metadata.Head;
import com.gear.file.annotation.ExcelSheetName;
import com.gear.file.annotation.ExcelSheetNo;
import com.gear.file.exception.GearFileException;
import com.gear.file.strategy.ExcelValidationHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
public class SmartExcelListener<T> extends AnalysisEventListener<T> {

    // ✨ 终极优化 1：使用 Spring 底层的 ConcurrentReferenceHashMap (弱引用/软引用)
    // 彻底解决由于静态 Map 强引用 Class 对象导致的 JVM Metaspace 内存泄漏问题！
    private static final Map<Class<?>, SheetContextFields> FIELD_CACHE = new ConcurrentReferenceHashMap<>();

    private static class SheetContextFields {
        Field sheetNoField;
        Field sheetNameField;
    }

    private List<T> cachedDataList;
    private final int batchSize;
    private final Class<T> clazz;
    private final Consumer<List<T>> consumer;
    private final ExcelValidationHandler<T> validationHandler;
    private final Validator validator;
    private final Class<?>[] groups;

    private final Map<Integer, List<CellExtra>> sheetMergeRegions;
    private final Map<Integer, Object> mergeDataCache = new HashMap<>();
    private Map<Integer, Field> colIndexToFieldMap;

    private Integer currentSheetNo;
    private Field sheetNoField;
    private Field sheetNameField;

    public SmartExcelListener(Class<T> clazz, Consumer<List<T>> consumer,
                              Map<Integer, List<CellExtra>> sheetMergeRegions,
                              int batchSize,
                              ExcelValidationHandler<T> validationHandler,
                              Validator validator, Class<?>... groups) {
        this.clazz = clazz;
        this.consumer = consumer;
        this.sheetMergeRegions = sheetMergeRegions != null ? sheetMergeRegions : new HashMap<>();
        this.batchSize = batchSize > 0 ? batchSize : 1000;
        this.cachedDataList = new ArrayList<>(this.batchSize);
        this.validationHandler = validationHandler;
        this.validator = validator;
        this.groups = groups;

        initSheetContextFields();
    }

    private void initSheetContextFields() {
        SheetContextFields contextFields = FIELD_CACHE.computeIfAbsent(clazz, k -> {
            SheetContextFields fields = new SheetContextFields();
            ReflectionUtils.doWithFields(k, field -> {
                if (field.isAnnotationPresent(ExcelSheetNo.class)) {
                    ReflectionUtils.makeAccessible(field);
                    fields.sheetNoField = field;
                }
                if (field.isAnnotationPresent(ExcelSheetName.class)) {
                    ReflectionUtils.makeAccessible(field);
                    fields.sheetNameField = field;
                }
            });
            return fields;
        });

        this.sheetNoField = contextFields.sheetNoField;
        this.sheetNameField = contextFields.sheetNameField;
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        int rowIndex = context.readRowHolder().getRowIndex();
        Integer sheetNo = context.readSheetHolder().getSheetNo();
        String sheetName = context.readSheetHolder().getSheetName();

        if (currentSheetNo == null || !currentSheetNo.equals(sheetNo)) {
            mergeDataCache.clear();
            colIndexToFieldMap = null;
            currentSheetNo = sheetNo;
        }

        if (colIndexToFieldMap == null) {
            initColIndexToFieldMap(context);
        }

        if (sheetNoField != null) {
            ReflectionUtils.setField(sheetNoField, data, sheetNo);
        }
        if (sheetNameField != null) {
            ReflectionUtils.setField(sheetNameField, data, sheetName);
        }

        fillMergeData(data, rowIndex, sheetNo);

        Set<ConstraintViolation<T>> violations = validator.validate(data, groups);
        if (!violations.isEmpty()) {
            if (validationHandler != null) {
                boolean keep = validationHandler.onValidateFail(data, rowIndex, violations);
                if (!keep) {
                    return;
                }
            } else {
                ConstraintViolation<T> firstViolation = violations.iterator().next();
                String errorMsg = firstViolation.getMessage();

                // ✨ 终极优化 2：根据错误字段名反推 Excel 中文表头名，让提示“说人话”
                String propertyPath = firstViolation.getPropertyPath().toString();
                String headName = propertyPath; // 默认使用英文字段名兜底
                if (colIndexToFieldMap != null) {
                    for (Map.Entry<Integer, Field> entry : colIndexToFieldMap.entrySet()) {
                        if (entry.getValue().getName().equals(propertyPath)) {
                            Map<Integer, Head> headMap = context.currentReadHolder().excelReadHeadProperty().getHeadMap();
                            Head head = headMap != null ? headMap.get(entry.getKey()) : null;
                            if (head != null && !head.getHeadNameList().isEmpty()) {
                                headName = head.getHeadNameList().get(head.getHeadNameList().size() - 1);
                            }
                            break;
                        }
                    }
                }
                throw new GearFileException("Sheet[" + sheetName + "] 第 " + (rowIndex + 1) + " 行数据校验失败: 【" + headName + "】" + errorMsg);
            }
        }
        cachedDataList.add(data);

        if (cachedDataList.size() >= batchSize) {
            consumer.accept(cachedDataList);
            cachedDataList = new ArrayList<>(batchSize);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        if (!cachedDataList.isEmpty()) {
            consumer.accept(cachedDataList);
            cachedDataList = new ArrayList<>(batchSize);
        }
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        if (exception instanceof ExcelDataConvertException) {
            ExcelDataConvertException ex = (ExcelDataConvertException) exception;
            int rowIndex = ex.getRowIndex();
            int colIndex = ex.getColumnIndex();
            String sheetName = context.readSheetHolder().getSheetName();
            String badData = ex.getCellData().getStringValue();

            // 提取真正的中文表头名称
            Map<Integer, Head> headMap = context.currentReadHolder().excelReadHeadProperty().getHeadMap();
            Head head = headMap != null ? headMap.get(colIndex) : null;
            String headName = (head != null && !head.getHeadNameList().isEmpty())
                    ? head.getHeadNameList().get(head.getHeadNameList().size() - 1)
                    : "第 " + (colIndex + 1) + " 列";

            // ✨ 将异常处理权交还给业务方
            if (validationHandler != null) {
                validationHandler.onConvertException(rowIndex, colIndex, sheetName, headName, badData, ex);
            } else {
                // 如果调用方没有传 Handler，走默认的兜底抛错逻辑
                String errorMsg = String.format("【%s】 数据格式不正确，无法解析输入的内容: '%s'", headName, badData);
                throw new GearFileException("Sheet[" + sheetName + "] 第 " + (rowIndex + 1) + " 行，" + errorMsg);
            }
        } else {
            // 其他未知底层异常，直接抛出
            throw exception;
        }
    }

    private void initColIndexToFieldMap(AnalysisContext context) {
        colIndexToFieldMap = new HashMap<>();
        Map<Integer, Head> headMap = context.currentReadHolder().excelReadHeadProperty().getHeadMap();
        for (Map.Entry<Integer, Head> entry : headMap.entrySet()) {
            Field field = entry.getValue().getField();
            if (field != null) {
                ReflectionUtils.makeAccessible(field);
                colIndexToFieldMap.put(entry.getKey(), field);
            }
        }
    }

    private void fillMergeData(T data, int rowIndex, Integer sheetNo) {
        List<CellExtra> currentSheetMerges = sheetMergeRegions.get(sheetNo);
        if (currentSheetMerges == null || currentSheetMerges.isEmpty()) return;

        for (CellExtra extra : currentSheetMerges) {
            int firstRow = extra.getFirstRowIndex();
            int lastRow = extra.getLastRowIndex();
            int colIndex = extra.getFirstColumnIndex();

            if (rowIndex == firstRow) {
                Object value = getFieldValue(data, colIndex);
                if (value != null) mergeDataCache.put(colIndex, value);
            } else if (rowIndex > firstRow && rowIndex <= lastRow) {
                Object cachedValue = mergeDataCache.get(colIndex);
                if (cachedValue != null) {
                    setFieldValue(data, colIndex, cachedValue);
                } else {
                    Object currentValue = getFieldValue(data, colIndex);
                    if (currentValue != null) {
                        mergeDataCache.put(colIndex, currentValue);
                    }
                }
            }
        }
    }

    private Object getFieldValue(T data, int colIndex) {
        Field field = colIndexToFieldMap.get(colIndex);
        if (field != null) {
            return ReflectionUtils.getField(field, data);
        }
        return null;
    }

    private void setFieldValue(T data, int colIndex, Object value) {
        Field field = colIndexToFieldMap.get(colIndex);
        if (field != null) {
            ReflectionUtils.setField(field, data, value);
        }
    }
}