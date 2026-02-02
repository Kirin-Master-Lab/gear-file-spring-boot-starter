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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EasyExcelReaderUtil {

    // 反射缓存：Class -> (Index -> Field)
    private static final Map<Class<?>, Map<Integer, Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private EasyExcelReaderUtil() {}

    /**
     * 读取并处理合并单元格数据
     * @param is 输入流
     * @param clazz 实体类类型
     * @param consumer 业务处理逻辑（如批量入库）
     * @param headRow 表头行数
     */
    public static <T extends SheetRowDTO> void readWithCallback(InputStream is, Class<T> clazz, Consumer<List<T>> consumer, Integer headRow) {
        int headRowNumber = (headRow == null) ? 1 : headRow;
        EasyExcelDataListener<T> listener = new EasyExcelDataListener<>();

        try {
            // 1. 执行读取
            EasyExcelFactory.read(is, clazz, listener)
                    .extraRead(CellExtraTypeEnum.MERGE) // 开启合并单元格读取
                    .headRowNumber(headRowNumber)
                    .sheet()
                    .autoTrim(true)
                    .doRead();

            List<T> dataList = listener.getDataList();
            List<CellExtra> cellExtras = listener.getCellExtras();

            // 2. 统一处理合并单元格打平
            if (CollectionUtils.isNotEmpty(dataList)) {
                if (CollectionUtils.isNotEmpty(cellExtras)) {
                    flatMergeData(dataList, cellExtras, clazz);
                }

                // 3. 消费打平后的完美数据
                consumer.accept(dataList);

                // 4. 显式清理，加速 GC
                dataList.clear();
                cellExtras.clear();
            }
        } catch (Exception e) {
            throw new GearFileException("Excel解析失败: " + e.getMessage(), e);
        }
    }

    private static <T extends SheetRowDTO> void flatMergeData(List<T> data, List<CellExtra> cellExtras, Class<T> clazz) {
        // 构建行号索引映射，便于快速查找对象
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

            // 获取合并区域首行首列的值
            Object initValue = ReflectionUtils.getField(field, firstRowData);
            if (initValue == null) continue;

            // 填充合并范围内的后续行
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
            // 即使没有写 index，也按照字段定义的自然顺序编号
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
