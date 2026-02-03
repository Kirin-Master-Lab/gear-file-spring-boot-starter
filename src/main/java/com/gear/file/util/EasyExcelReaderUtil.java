package com.gear.file.util;

import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.enums.CellExtraTypeEnum;
import com.alibaba.excel.metadata.CellExtra;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.gear.file.exception.GearFileException;
import com.gear.file.model.SheetRowDTO;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EasyExcelReaderUtil {

    private static final Map<Class<?>, Map<Integer, Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private EasyExcelReaderUtil() {}

    public static <T extends SheetRowDTO> void readWithCallback(InputStream is, Class<T> clazz, Consumer<List<T>> consumer, Integer headRow) {
        int headRowNumber = (headRow == null) ? 1 : headRow;
        EasyExcelDataListener<T> listener = new EasyExcelDataListener<>();
        List<T> allSheetDataList = new ArrayList<>();

        // 1. 在 Workbook 级别开启 ExtraRead
        // 3.3.3 版本中，extraRead 是在这里开启的，它会作用于所有 Sheet
        try (ExcelReader excelReader = EasyExcelFactory.read(is, clazz, listener)
                .extraRead(CellExtraTypeEnum.MERGE)
                .autoTrim(true)
                .build()) {

            List<ReadSheet> sheets = excelReader.excelExecutor().sheetList();

            for (ReadSheet sheet : sheets) {
                // 2. 构建 ReadSheet，这里只配置 Sheet 特有的参数
                ReadSheet readSheet = EasyExcelFactory.readSheet(sheet.getSheetNo())
                        .headRowNumber(headRowNumber)
                        .build();

                // 3. 执行当前 Sheet 读取
                excelReader.read(readSheet);

                List<T> currentData = listener.getDataList();
                List<CellExtra> currentSheetExtras = listener.getCellExtras();

                if (CollectionUtils.isNotEmpty(currentData)) {
                    // 打平逻辑
                    if (CollectionUtils.isNotEmpty(currentSheetExtras)) {
                        flatMergeData(currentData, currentSheetExtras, clazz);
                    }
                    allSheetDataList.addAll(currentData);
                    listener.clear();
                }
            }

            if (CollectionUtils.isNotEmpty(allSheetDataList)) {
                consumer.accept(allSheetDataList);
            }

        } catch (Exception e) {
            throw new GearFileException("Excel多Sheet解析失败: " + e.getMessage(), e);
        } finally {
            allSheetDataList.clear();
        }
    }

    private static <T extends SheetRowDTO> void flatMergeData(List<T> data, List<CellExtra> cellExtras, Class<T> clazz) {
        Map<Integer, T> dataMap = data.stream()
                .collect(Collectors.toMap(SheetRowDTO::getLineNumber, Function.identity(), (o1, o2) -> o1));

        Map<Integer, Field> indexedFields = getIndexedFields(clazz);

        for (CellExtra cellExtra : cellExtras) {
            int firstRow = cellExtra.getFirstRowIndex();
            int lastRow = cellExtra.getLastRowIndex();
            int firstCol = cellExtra.getFirstColumnIndex();

            Field field = indexedFields.get(firstCol);
            if (field == null) continue;

            T firstRowData = dataMap.get(firstRow);
            if (firstRowData == null) continue;

            Object initValue = ReflectionUtils.getField(field, firstRowData);
            if (initValue == null) continue;

            for (int i = firstRow + 1; i <= lastRow; i++) {
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
            Field[] fields = k.getDeclaredFields();
            int indexCounter = 0;
            for (Field field : fields) {
                ExcelProperty anno = field.getAnnotation(ExcelProperty.class);
                int index = (anno != null && anno.index() != -1) ? anno.index() : indexCounter;

                ReflectionUtils.makeAccessible(field);
                map.put(index, field);
                indexCounter++;
            }
            return Collections.unmodifiableMap(map);
        });
    }
}