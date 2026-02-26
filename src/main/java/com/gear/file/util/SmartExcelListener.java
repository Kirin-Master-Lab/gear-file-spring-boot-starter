package com.gear.file.util;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.CellExtra;
import com.alibaba.excel.metadata.Head;
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

    private static final int BATCH_COUNT = 1000;
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private List<T> cachedDataList = new ArrayList<>(BATCH_COUNT);
    private final Consumer<List<T>> consumer;
    private final ExcelValidationHandler<T> validationHandler;

    // 变更为接收带 Sheet 隔离的合并规则 Map
    private final Map<Integer, List<CellExtra>> sheetMergeRegions;

    private final Map<Integer, Object> mergeDataCache = new HashMap<>();
    private Map<Integer, Field> colIndexToFieldMap;

    // 新增：记录当前正在解析的 SheetNo，用于感知 Sheet 切换
    private Integer currentSheetNo;

    public SmartExcelListener(Consumer<List<T>> consumer,
                              Map<Integer, List<CellExtra>> sheetMergeRegions,
                              ExcelValidationHandler<T> validationHandler) {
        this.consumer = consumer;
        this.sheetMergeRegions = sheetMergeRegions != null ? sheetMergeRegions : new HashMap<>();
        this.validationHandler = validationHandler;
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        int rowIndex = context.readRowHolder().getRowIndex();
        Integer sheetNo = context.readSheetHolder().getSheetNo();

        // 【关键防串数据逻辑】如果切换了 Sheet，必须清空上一页的合并单元格缓存与表头缓存
        if (currentSheetNo == null || !currentSheetNo.equals(sheetNo)) {
            mergeDataCache.clear();
            colIndexToFieldMap = null; // 让新 Sheet 重新加载表头映射
            currentSheetNo = sheetNo;
        }

        if (colIndexToFieldMap == null) {
            initColIndexToFieldMap(context);
        }

        // 1. 实时打平当前 Sheet 的合并单元格数据
        fillMergeData(data, rowIndex, sheetNo);

        // 2. JSR-303 数据校验
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(data);
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

        // 3. 防 OOM 批处理
        if (cachedDataList.size() >= BATCH_COUNT) {
            consumer.accept(cachedDataList);
            cachedDataList = new ArrayList<>(BATCH_COUNT);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // doAfterAllAnalysed 会在每个 Sheet 解析结束时调用一次
        if (!cachedDataList.isEmpty()) {
            consumer.accept(cachedDataList);
            // 务必清空，防止把前一个 Sheet 剩余的数据带到下一个 Sheet
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
        // 获取当前 Sheet 专属的合并规则
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
                if (cachedValue != null) setFieldValue(data, colIndex, cachedValue);
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