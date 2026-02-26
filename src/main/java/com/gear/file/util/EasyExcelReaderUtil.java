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
                                            boolean enableMerge, // 接收合并开关
                                            ExcelValidationHandler<T> validationHandler,
                                            Validator validator,
                                            Class<?>... groups) {
        int headRowNumber = (headRow == null) ? 1 : headRow;
        File tempFile = null;

        try (InputStream autoCloseIs = is) {
            tempFile = File.createTempFile("gear-excel-", ".tmp");
            // 移除了 deleteOnExit()，避免长生命周期服务内存泄漏，依靠 finally 清理即可
            Files.copy(autoCloseIs, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            Map<Integer, List<CellExtra>> sheetMergeRegions = new HashMap<>();

            // 仅当业务实体声明需要合并单元格时，才执行耗时的 Pass 1 扫描，大幅提升普通文件解析性能
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
                }).extraRead(CellExtraTypeEnum.MERGE).doReadAll();
            }

            // Pass 2: 正式数据读取，透传 groups 和 clazz
            SmartExcelListener<T> listener = new SmartExcelListener<>(clazz, consumer, sheetMergeRegions, validationHandler, validator, groups);
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