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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

        // ✨ 终极优化 4：全面拥抱 NIO.2 临时文件，堵住越权读取安全漏洞
        Path tempFile = null;

        try (InputStream autoCloseIs = is) {
            tempFile = Files.createTempFile("gear-excel-", ".tmp");
            Files.copy(autoCloseIs, tempFile, StandardCopyOption.REPLACE_EXISTING);

            Map<Integer, List<CellExtra>> sheetMergeRegions = new HashMap<>();

            if (enableMerge) {
                EasyExcel.read(tempFile.toFile(), clazz, new ReadListener<T>() {
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
                            @Override
                            public void onException(Exception exception, AnalysisContext context) throws Exception {
                                // 如果是类型转换异常，预扫描阶段直接吞掉，保证能把所有的合并规则扫完。
                                // 真正的业务脏数据拦截，留给 Pass 2 的 SmartExcelListener 去处理。
                                if (exception instanceof com.alibaba.excel.exception.ExcelDataConvertException) {
                                    return;
                                }
                                // 如果是其他底层致命异常，则抛出
                                throw exception;
                            }
                        }).extraRead(CellExtraTypeEnum.MERGE)
                        .headRowNumber(headRowNumber)
                        .doReadAll();
            }

            SmartExcelListener<T> listener = new SmartExcelListener<>(clazz, consumer, sheetMergeRegions, batchSize, validationHandler, validator, groups);
            EasyExcel.read(tempFile.toFile(), clazz, listener)
                    .headRowNumber(headRowNumber)
                    .doReadAll();

        } catch (Exception e) {
            if (e instanceof GearFileException || e.getCause() instanceof GearFileException) {
                throw (RuntimeException) (e instanceof GearFileException ? e : e.getCause());
            }
            throw new GearFileException("Excel 解析失败: " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    // 安全的 NIO 删除操作
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    log.warn("Excel NIO临时文件删除失败: {}", tempFile.toAbsolutePath());
                }
            }
        }
    }
}