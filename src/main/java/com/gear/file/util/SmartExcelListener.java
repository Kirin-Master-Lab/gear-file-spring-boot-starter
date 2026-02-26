package com.gear.file.util;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.CellExtra;
import com.alibaba.excel.metadata.Head;
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
    private final Consumer<List<T>> consumer;
    private final ExcelValidationHandler<T> validationHandler;

    // 优化点 3：接收 Spring 容器管理的 Validator，完美支持自定义注解中的依赖注入
    private final Validator validator;

    private final Map<Integer, List<CellExtra>> sheetMergeRegions;
    private final Map<Integer, Object> mergeDataCache = new HashMap<>();
    private Map<Integer, Field> colIndexToFieldMap;

    private Integer currentSheetNo;

    public SmartExcelListener(Consumer<List<T>> consumer,
                              Map<Integer, List<CellExtra>> sheetMergeRegions,
                              ExcelValidationHandler<T> validationHandler,
                              Validator validator) {
        this.consumer = consumer;
        this.sheetMergeRegions = sheetMergeRegions != null ? sheetMergeRegions : new HashMap<>();
        this.validationHandler = validationHandler;
        this.validator = validator;
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        int rowIndex = context.readRowHolder().getRowIndex();
        Integer sheetNo = context.readSheetHolder().getSheetNo();

        if (currentSheetNo == null || !currentSheetNo.equals(sheetNo)) {
            mergeDataCache.clear();
            colIndexToFieldMap = null;
            currentSheetNo = sheetNo;
        }

        if (colIndexToFieldMap == null) {
            initColIndexToFieldMap(context);
        }

        fillMergeData(data, rowIndex, sheetNo);

        Set<ConstraintViolation<T>> violations = validator.validate(data);
        if (!violations.isEmpty()) {
            if (validationHandler != null) {
                boolean keep = validationHandler.onValidateFail(data, rowIndex, violations);
                if (!keep) {
                    return;
                }
            } else {
                String errorMsg = violations.iterator().next().getMessage();
                throw new GearFileException("Sheet[" + sheetNo + "] 第 " + (rowIndex + 1) + " 行数据校验失败: " + errorMsg);
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
                    // 优化点 4：极端场景兜底。如果未拿到缓存(可能首行跨越了表头，或者首行是空值被EasyExcel跳过)
                    // 则将当前触碰到的第一行非空值作为该合并区域的基准值放入缓存。
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