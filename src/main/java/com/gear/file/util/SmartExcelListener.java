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
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
public class SmartExcelListener<T> extends AnalysisEventListener<T> {

    private static final int BATCH_COUNT = 1000;

    // ✨ 优化点 3：引入全局并发字典缓存，消除每次 new Listener 时的重复反射损耗
    private static final Map<Class<?>, SheetContextFields> FIELD_CACHE = new ConcurrentHashMap<>();

    private static class SheetContextFields {
        Field sheetNoField;
        Field sheetNameField;
    }

    private List<T> cachedDataList = new ArrayList<>(BATCH_COUNT);
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
                              ExcelValidationHandler<T> validationHandler,
                              Validator validator, Class<?>... groups) {
        this.clazz = clazz;
        this.consumer = consumer;
        this.sheetMergeRegions = sheetMergeRegions != null ? sheetMergeRegions : new HashMap<>();
        this.validationHandler = validationHandler;
        this.validator = validator;
        this.groups = groups;

        initSheetContextFields();
    }

    /**
     * O(1) 极速获取上下文注入字段
     */
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
                String errorMsg = violations.iterator().next().getMessage();
                throw new GearFileException("Sheet[" + sheetName + "] 第 " + (rowIndex + 1) + " 行数据校验失败: " + errorMsg);
            }
        }
        cachedDataList.add(data);

        if (cachedDataList.size() >= BATCH_COUNT) {
            consumer.accept(cachedDataList);
            cachedDataList = new ArrayList<>(BATCH_COUNT);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        if (!cachedDataList.isEmpty()) {
            consumer.accept(cachedDataList);
            cachedDataList = new ArrayList<>(BATCH_COUNT);
        }
    }

    /**
     * ✨ 优化点 1：重写 onException 捕获类型转换死角
     * 当 Excel 中的内容 (例如："二十") 无法转换为 DTO 字段类型 (例如：Integer) 时触发。
     */
    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        if (exception instanceof ExcelDataConvertException) {
            ExcelDataConvertException ex = (ExcelDataConvertException) exception;
            int rowIndex = ex.getRowIndex();
            int colIndex = ex.getColumnIndex();
            String sheetName = context.readSheetHolder().getSheetName();
            String badData = ex.getCellData().getStringValue();

            // 翻译底层异常为人话
            String errorMsg = String.format("数据类型转换失败！输入的内容 '%s' 格式不正确", badData);

            // 抛出友好的业务异常中断解析
            throw new GearFileException("Sheet[" + sheetName + "] 第 " + (rowIndex + 1) + " 行，第 " + (colIndex + 1) + " 列" + errorMsg);
        }
        // 如果是其他底层致命异常，继续往外抛出
        throw exception;
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