package com.gear.file.util;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
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
import java.util.function.Consumer;

@Slf4j
public class SmartExcelListener<T> extends AnalysisEventListener<T> {

    private static final int BATCH_COUNT = 1000;

    private List<T> cachedDataList = new ArrayList<>(BATCH_COUNT);
    private final Class<T> clazz;
    private final Consumer<List<T>> consumer;
    private final ExcelValidationHandler<T> validationHandler;
    private final Validator validator;
    private final Class<?>[] groups; // 保存校验分组

    private final Map<Integer, List<CellExtra>> sheetMergeRegions;
    private final Map<Integer, Object> mergeDataCache = new HashMap<>();
    private Map<Integer, Field> colIndexToFieldMap;

    private Integer currentSheetNo;

    // Sheet 上下文注入字段缓存
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

        initSheetContextFields(); // 初始化上下文注解字段
    }

    private void initSheetContextFields() {
        ReflectionUtils.doWithFields(clazz, field -> {
            if (field.isAnnotationPresent(ExcelSheetNo.class)) {
                ReflectionUtils.makeAccessible(field);
                sheetNoField = field;
            }
            if (field.isAnnotationPresent(ExcelSheetName.class)) {
                ReflectionUtils.makeAccessible(field);
                sheetNameField = field;
            }
        });
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

        // 1. 注入 Sheet 上下文信息
        if (sheetNoField != null) {
            ReflectionUtils.setField(sheetNoField, data, sheetNo);
        }
        if (sheetNameField != null) {
            ReflectionUtils.setField(sheetNameField, data, sheetName);
        }

        // 2. 填充合并数据
        fillMergeData(data, rowIndex, sheetNo);

        // 3. JSR-303 分组校验
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