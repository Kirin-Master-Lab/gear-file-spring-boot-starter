package com.gear.file.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.enums.CellExtraTypeEnum;
import com.alibaba.excel.metadata.CellExtra;
import com.alibaba.excel.read.listener.ReadListener;
import com.gear.file.exception.GearFileException;
import com.gear.file.strategy.ExcelValidationHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class EasyExcelReaderUtil {

    private EasyExcelReaderUtil() {}

    /**
     * 高级流式解析：支持防OOM + 合并单元格打平 + JSR303校验
     */
    public static <T> void readWithCallback(InputStream is, Class<T> clazz,
                                            Consumer<List<T>> consumer, Integer headRow,
                                            ExcelValidationHandler<T> validationHandler) {
        int headRowNumber = (headRow == null) ? 1 : headRow;

        try {
            // 将流转为字节数组以支持两次读取 (注：如果预期单文件超过 500MB，建议入参改为 java.io.File 避免吃内存)
            byte[] streamBytes = toByteArray(is);

            // Pass 1: 极速读取，只缓存合并规则
            List<CellExtra> mergeRegions = new ArrayList<>();
            try (InputStream pass1Stream = new ByteArrayInputStream(streamBytes)) {
                EasyExcel.read(pass1Stream, clazz, new ReadListener<T>() {
                    @Override
                    public void invoke(T data, AnalysisContext context) {}
                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {}
                    @Override
                    public void extra(CellExtra extra, AnalysisContext context) {
                        if (extra.getType() == CellExtraTypeEnum.MERGE) {
                            mergeRegions.add(extra);
                        }
                    }
                }).extraRead(CellExtraTypeEnum.MERGE).sheet().headRowNumber(headRowNumber).doRead();
            }

            // Pass 2: 正式数据流式读取
            try (InputStream pass2Stream = new ByteArrayInputStream(streamBytes)) {
                SmartExcelListener<T> listener = new SmartExcelListener<>(consumer, mergeRegions,validationHandler);
                EasyExcel.read(pass2Stream, clazz, listener)
                        .sheet()
                        .headRowNumber(headRowNumber)
                        .doRead();
            }

        } catch (Exception e) {
            throw new GearFileException("Excel 解析失败: " + e.getMessage(), e);
        }
    }

    private static byte[] toByteArray(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}