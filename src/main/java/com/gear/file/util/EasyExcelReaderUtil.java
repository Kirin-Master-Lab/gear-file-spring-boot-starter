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
                                            boolean enableMerge, int batchSize,
                                            ExcelValidationHandler<T> validationHandler,
                                            Validator validator,
                                            Class<?>... groups) {
        int headRowNumber = (headRow == null) ? 1 : headRow;
        File tempFile = null;

        try (InputStream autoCloseIs = is) {
            tempFile = File.createTempFile("gear-excel-", ".tmp");
            Files.copy(autoCloseIs, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            Map<Integer, List<CellExtra>> sheetMergeRegions = new HashMap<>();

            if (enableMerge) {
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
                        }).extraRead(CellExtraTypeEnum.MERGE)
                        .headRowNumber(headRowNumber)
                        .doReadAll();
            }

            SmartExcelListener<T> listener = new SmartExcelListener<>(clazz, consumer, sheetMergeRegions, batchSize, validationHandler, validator, groups);
            EasyExcel.read(tempFile, clazz, listener)
                    .headRowNumber(headRowNumber)
                    .doReadAll();

        } catch (Exception e) {
            if (e instanceof GearFileException || e.getCause() instanceof GearFileException) {
                throw (RuntimeException) (e instanceof GearFileException ? e : e.getCause());
            }
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