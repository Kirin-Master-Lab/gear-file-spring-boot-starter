package com.gear.file.core;

import com.gear.file.annotation.FileModel;
import com.gear.file.exception.GearFileException;
import com.gear.file.strategy.ExcelValidationHandler;
import com.gear.file.strategy.FileParser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class FileEngine implements DownloadService {

    private final List<FileParser> parsers;

    /**
     * 动态下载模板
     */
    public void downloadTemplate(HttpServletResponse response, Class<?> clazz) {
        String showName = "导入模板"; // 默认文件名
        FileModel anno = clazz.getAnnotation(FileModel.class);
        if (anno != null && !anno.showName().isEmpty()) {
            showName = anno.showName();
        }

        try {
            // 调用接口中的默认动态下载方法
            downloadTemplateDynamic(response, showName, clazz);
        } catch (Exception e) {
            throw new GearFileException("动态模板生成失败: " + e.getMessage(), e);
        }
    }
    /**
     * 回调分批导入 (带数据校验策略)
     */
    public <T> void importFile(InputStream is, Class<T> clazz, String fileName,
                                           Consumer<List<T>> consumer,
                                           ExcelValidationHandler<T> validationHandler) {
        getParser(fileName).parse(is, clazz, consumer, validationHandler);
    }

    private FileParser getParser(String fileName) {
        String suffix = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return parsers.stream()
                .filter(p -> p.support(suffix))
                .findFirst()
                .orElseThrow(() -> new GearFileException("不支持的文件格式: " + suffix));
    }


}