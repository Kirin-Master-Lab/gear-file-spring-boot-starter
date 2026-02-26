package com.gear.file.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.enums.CellExtraTypeEnum;
import com.alibaba.excel.metadata.CellExtra;
import com.alibaba.excel.read.listener.ReadListener;
import com.gear.file.exception.GearFileException;
import com.gear.file.strategy.ExcelValidationHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public class EasyExcelReaderUtil {

    private EasyExcelReaderUtil() {}

    public static <T> void readWithCallback(InputStream is, Class<T> clazz,
                                            Consumer<List<T>> consumer, Integer headRow,
                                            ExcelValidationHandler<T> validationHandler) {
        int headRowNumber = (headRow == null) ? 1 : headRow;
        File tempFile = null;

        try {
            tempFile = File.createTempFile("gear-excel-", ".tmp");
            Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Pass 1: 极速读取临时文件，按 SheetNo 缓存合并规则
            // 数据结构变更为：Map<SheetNo, 合并规则列表>
            Map<Integer, List<CellExtra>> sheetMergeRegions = new HashMap<>();

            EasyExcel.read(tempFile, clazz, new ReadListener<T>() {
                @Override
                public void invoke(T data, AnalysisContext context) {}
                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {}
                @Override
                public void extra(CellExtra extra, AnalysisContext context) {
                    if (extra.getType() == CellExtraTypeEnum.MERGE) {
                        // 获取当前合并规则属于哪个 Sheet
                        Integer sheetNo = context.readSheetHolder().getSheetNo();
                        sheetMergeRegions.computeIfAbsent(sheetNo, k -> new ArrayList<>()).add(extra);
                    }
                }
            }).extraRead(CellExtraTypeEnum.MERGE).doReadAll(); // 改为 doReadAll()，扫描所有 Sheet

            // Pass 2: 正式流式读取所有 Sheet 数据
            SmartExcelListener<T> listener = new SmartExcelListener<>(consumer, sheetMergeRegions, validationHandler);
            EasyExcel.read(tempFile, clazz, listener)
                    .headRowNumber(headRowNumber)
                    .doReadAll(); // 改为 doReadAll()

        } catch (Exception e) {
            throw new GearFileException("Excel 解析失败: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                if (!tempFile.delete()) {
                    log.warn("Excel 临时文件删除失败: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }
}