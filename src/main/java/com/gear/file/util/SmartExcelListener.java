package com.gear.file.util;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.CellExtra;
import com.gear.file.exception.GearFileException;
import com.gear.file.strategy.ExcelValidationHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
public class SmartExcelListener<T> extends AnalysisEventListener<T> {

    private static final int BATCH_COUNT = 1000; // 批处理阈值
    private List<T> cachedDataList = new ArrayList<>(BATCH_COUNT);

    private final Consumer<List<T>> consumer;
    private final List<CellExtra> mergeRegions;
    private final Validator validator;

    // 缓存合并单元格首行的值
    private final Map<Integer, Object> mergeDataCache = new HashMap<>();
    private Field[] declaredFields;

    private final ExcelValidationHandler<T> validationHandler;

    public SmartExcelListener(Consumer<List<T>> consumer,
                              List<CellExtra> mergeRegions,
                              ExcelValidationHandler<T> validationHandler) {
        this.consumer = consumer;
        this.mergeRegions = mergeRegions != null ? mergeRegions : new ArrayList<>();
        this.validationHandler = validationHandler;
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        int rowIndex = context.readRowHolder().getRowIndex();

        if (declaredFields == null) {
            declaredFields = data.getClass().getDeclaredFields();
        }

        // 1. 实时打平合并单元格数据
        fillMergeData(data, rowIndex);

        // 2. JSR-303 数据校验
        Set<ConstraintViolation<T>> violations = validator.validate(data);
        if (!violations.isEmpty()) {
            if (validationHandler != null) {
                // 将决定权交给外部调用方
                boolean keep = validationHandler.onValidateFail(data, rowIndex, violations);
                if (!keep) {
                    return; // 外部决定丢弃该条数据，直接 return 结束当前行的处理
                }
            } else {
                // 组件默认行为：严格模式，快速失败，抛出异常中断解析
                String errorMsg = violations.iterator().next().getMessage();
                throw new GearFileException("第 " + (rowIndex + 1) + " 行数据校验失败: " + errorMsg);
            }
        }
        cachedDataList.add(data);

        // 3. 防 OOM 批处理
        if (cachedDataList.size() >= BATCH_COUNT) {
            consumer.accept(cachedDataList);
            cachedDataList = new ArrayList<>(BATCH_COUNT);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        if (!cachedDataList.isEmpty()) {
            consumer.accept(cachedDataList);
        }
    }

    private void fillMergeData(T data, int rowIndex) {
        if (mergeRegions.isEmpty()) return;

        for (CellExtra extra : mergeRegions) {
            int firstRow = extra.getFirstRowIndex();
            int lastRow = extra.getLastRowIndex();
            int colIndex = extra.getFirstColumnIndex();

            if (rowIndex == firstRow) {
                Object value = getFieldValue(data, colIndex);
                if (value != null) mergeDataCache.put(colIndex, value);
            } else if (rowIndex > firstRow && rowIndex <= lastRow) {
                Object cachedValue = mergeDataCache.get(colIndex);
                if (cachedValue != null) setFieldValue(data, colIndex, cachedValue);
            }
        }
    }

    private Object getFieldValue(T data, int colIndex) {
        if (colIndex < declaredFields.length) {
            Field field = declaredFields[colIndex];
            ReflectionUtils.makeAccessible(field);
            return ReflectionUtils.getField(field, data);
        }
        return null;
    }

    private void setFieldValue(T data, int colIndex, Object value) {
        if (colIndex < declaredFields.length) {
            Field field = declaredFields[colIndex];
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, data, value);
        }
    }
}