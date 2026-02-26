package com.gear.file.core;

import com.gear.file.annotation.FileModel;
import com.gear.file.exception.GearFileException;
import com.gear.file.strategy.ExcelValidationHandler;
import com.gear.file.strategy.FileParser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
public class FileEngine implements DownloadService {

    private final List<FileParser> parsers;
    private final Validator validator;

    public void downloadTemplate(HttpServletResponse response, Class<?> clazz) {
        String showName = getShowName(clazz);
        try {
            downloadTemplateDynamic(response, showName, clazz);
        } catch (Exception e) {
            throw new GearFileException("动态模板生成失败: " + e.getMessage(), e);
        }
    }

    // ================== 新增的导出 API ==================

    public <T> void exportData(HttpServletResponse response, Class<T> clazz, List<T> data) {
        try {
            exportExcel(response, getShowName(clazz), clazz, data);
        } catch (Exception e) {
            throw new GearFileException("数据导出失败: " + e.getMessage(), e);
        }
    }

    public <T> void exportBigData(HttpServletResponse response, Class<T> clazz, Function<Integer, List<T>> pageDataSupplier) {
        try {
            exportBigExcel(response, getShowName(clazz), clazz, pageDataSupplier);
        } catch (Exception e) {
            throw new GearFileException("分页数据导出失败: " + e.getMessage(), e);
        }
    }

    private String getShowName(Class<?> clazz) {
        FileModel anno = clazz.getAnnotation(FileModel.class);
        return (anno != null && !anno.showName().isEmpty()) ? anno.showName() : "导出数据";
    }

    // ================== 导入 API 保持不变 ==================

    public <T> List<T> importFileSync(InputStream is, Class<T> clazz, String fileName) {
        List<T> allData = new ArrayList<>();
        this.importFile(is, clazz, fileName, allData::addAll, null);
        return allData;
    }

    public <T> void importFile(InputStream is, Class<T> clazz, String fileName, Consumer<List<T>> consumer) {
        this.importFile(is, clazz, fileName, consumer, null);
    }

    public <T> void importFile(InputStream is, Class<T> clazz, String fileName,
                               Consumer<List<T>> consumer,
                               ExcelValidationHandler<T> validationHandler,
                               Class<?>... groups) {
        this.getParser(fileName).parse(is, clazz, consumer, validationHandler, validator, groups);
    }

    private FileParser getParser(String fileName) {
        String suffix = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return parsers.stream()
                .filter(p -> p.support(suffix))
                .findFirst()
                .orElseThrow(() -> new GearFileException("不支持的文件格式: " + suffix));
    }
}