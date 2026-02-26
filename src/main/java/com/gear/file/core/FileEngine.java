package com.gear.file.core;

import com.gear.file.annotation.FileModel;
import com.gear.file.exception.GearFileException;
import com.gear.file.strategy.ExcelValidationHandler;
import com.gear.file.strategy.FileParser;
import jakarta.servlet.http.HttpServletResponse;
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

    /**
     * 动态下载模板
     */
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

    // ================= 以下为重载的极简 API，方便业务线使用 =================

    /**
     * 1. 极致简便：同步返回所有数据（适用于几百条的小配置表导入，遇错直接抛异常）
     */
    public <T> List<T> importFileSync(InputStream is, Class<T> clazz, String fileName) {
        List<T> allData = new ArrayList<>();
        this.importFile(is, clazz, fileName, allData::addAll, null);
        return allData;
    }

    /**
     * 2. 中等灵活：异步分批回调，防 OOM，遇错直接抛异常中断（适用于大文件，但不关心收集错误）
     */
    public <T> void importFile(InputStream is, Class<T> clazz, String fileName, Consumer<List<T>> consumer) {
        this.importFile(is, clazz, fileName, consumer, null);
    }

    /**
     * 3. 最强功能：异步分批回调 + 自定义校验策略（大厂标准，用于需要拦截脏数据并反馈给前端的场景）
     */
    public <T> void importFile(InputStream is, Class<T> clazz, String fileName,
                               Consumer<List<T>> consumer,
                               ExcelValidationHandler<T> validationHandler) {
        this.getParser(fileName).parse(is, clazz, consumer, validationHandler);
    }

    private FileParser getParser(String fileName) {
        String suffix = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return parsers.stream()
                .filter(p -> p.support(suffix))
                .findFirst()
                .orElseThrow(() -> new GearFileException("不支持的文件格式: " + suffix));
    }
}