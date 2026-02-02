package com.gear.file.util;

import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.enums.CellExtraTypeEnum;
import com.alibaba.excel.metadata.CellExtra;
import com.gear.file.exception.GearFileException;
import com.gear.file.model.SheetRowDTO;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EasyExcelReaderUtil {


    private EasyExcelReaderUtil() {
    }

    // 反射缓存：Class -> (Index -> Field)
    private static final Map<Class<?>, Map<Integer, Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    public static <T extends SheetRowDTO> List<T> readList(InputStream is, Class<T> clazz, Integer headRow) {
        int headRowNumber = (headRow == null) ? 1 : headRow;
        EasyExcelDataListener<T> listener = new EasyExcelDataListener<>();

        try {
            EasyExcelFactory.read(is, clazz, listener)
                    .extraRead(CellExtraTypeEnum.MERGE) // 必须开启以获取合并单元格信息
                    .headRowNumber(headRowNumber)
                    .sheet()
                    .autoTrim(true)
                    .doRead();
        } catch (Exception e) {
            throw new GearFileException("Excel解析失败: " + e.getMessage(), e);
        }

        List<T> dataList = listener.getDataList();
        if (CollectionUtils.isNotEmpty(listener.getCellExtras()) && CollectionUtils.isNotEmpty(dataList)) {
            flatMergeData(dataList, listener.getCellExtras(), clazz);
        }
        return dataList;
    }

    private static <T extends SheetRowDTO> void flatMergeData(List<T> data, List<CellExtra> cellExtras, Class<T> clazz) {
        // 构建行号索引映射
        Map<Integer, T> dataMap = data.stream()
                .collect(Collectors.toMap(SheetRowDTO::getLineNumber, Function.identity(), (o1, o2) -> o2));

        Map<Integer, Field> indexedFields = getIndexedFields(clazz);

        for (CellExtra cellExtra : cellExtras) {
            int firstRow = cellExtra.getFirstRowIndex();
            int lastRow = cellExtra.getLastRowIndex();
            int firstCol = cellExtra.getFirstColumnIndex();

            Field field = indexedFields.get(firstCol);
            if (field == null) continue;

            // 获取合并单元格首行首列的值
            T firstRowData = dataMap.get(firstRow);
            if (firstRowData == null) continue;

            Object initValue = ReflectionUtils.getField(field, firstRowData);

            // 填充合并范围内的所有行
            for (int i = firstRow; i <= lastRow; i++) {
                T currentRow = dataMap.get(i);
                if (currentRow != null) {
                    ReflectionUtils.setField(field, currentRow, initValue);
                }
            }
        }
    }

    private static Map<Integer, Field> getIndexedFields(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, k -> {
            Map<Integer, Field> map = new HashMap<>();
            ReflectionUtils.doWithFields(k, field -> {
                ExcelProperty anno = field.getAnnotation(ExcelProperty.class);
                if (anno != null && anno.index() >= 0) {
                    ReflectionUtils.makeAccessible(field);
                    map.put(anno.index(), field);
                }
            });
            return Collections.unmodifiableMap(map);
        });
    }
}