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

@Slf4j
@RequiredArgsConstructor
public class FileEngine implements DownloadService {

    private final List<FileParser> parsers;
    private final Validator validator;

    public void downloadTemplate(HttpServletResponse response, Class<?> clazz) {
        String showName = "导入模板";
        FileModel anno = clazz.getAnnotation(FileModel.class);
        if (anno != null && !anno.showName().isEmpty()) {
            showName = anno.showName();
        }

        try {
            downloadTemplateDynamic(response, showName, clazz);
        } catch (Exception e) {
            throw new GearFileException("动态模板生成失败: " + e.getMessage(), e);
        }
    }

    public <T> List<T> importFileSync(InputStream is, Class<T> clazz, String fileName) {
        List<T> allData = new ArrayList<>();
        this.importFile(is, clazz, fileName, allData::addAll, null);
        return allData;
    }

    public <T> void importFile(InputStream is, Class<T> clazz, String fileName, Consumer<List<T>> consumer) {
        this.importFile(is, clazz, fileName, consumer, null);
    }

    /**
     * 支持传入自定义校验策略 和 分组校验(groups) 的终极方法
     */
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