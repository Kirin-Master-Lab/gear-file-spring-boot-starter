package com.gear.file.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.enums.CellExtraTypeEnum;
import com.alibaba.excel.metadata.CellExtra;
import com.alibaba.excel.read.listener.ReadListener;
import com.gear.file.exception.GearFileException;
import com.gear.file.strategy.ExcelValidationHandler;
import jakarta.validation.Validator;
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
                                            ExcelValidationHandler<T> validationHandler,
                                            Validator validator) {
        int headRowNumber = (headRow == null) ? 1 : headRow;
        File tempFile = null;

        // 优化点 1：使用 try-with-resources 接管 InputStream，确保流一定会关闭，防止文件句柄泄露 (Too many open files)
        try (InputStream autoCloseIs = is) {
            tempFile = File.createTempFile("gear-excel-", ".tmp");
            // 优化点 2：挂载 JVM 钩子，应对 kill -9 或 OOM 宕机，防止磁盘爆满
            tempFile.deleteOnExit();

            Files.copy(autoCloseIs, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            Map<Integer, List<CellExtra>> sheetMergeRegions = new HashMap<>();

            EasyExcel.read(tempFile, clazz, new ReadListener<T>() {
                @Override
                public void invoke(T data, AnalysisContext context) {}
                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {}
                @Override
                public void extra(CellExtra extra, AnalysisContext context) {
                    if (extra.getType() == CellExtraTypeEnum.MERGE) {
                        Integer sheetNo = context.readSheetHolder().getSheetNo();
                        sheetMergeRegions.computeIfAbsent(sheetNo, k -> new ArrayList<>()).add(extra);
                    }
                }
            }).extraRead(CellExtraTypeEnum.MERGE).doReadAll();

            // 将 Validator 传给 Listener
            SmartExcelListener<T> listener = new SmartExcelListener<>(consumer, sheetMergeRegions, validationHandler, validator);
            EasyExcel.read(tempFile, clazz, listener)
                    .headRowNumber(headRowNumber)
                    .doReadAll();

        } catch (Exception e) {
            throw new GearFileException("Excel 解析失败: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                if (!tempFile.delete()) {
                    log.warn("Excel 临时文件常规删除失败: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }
}