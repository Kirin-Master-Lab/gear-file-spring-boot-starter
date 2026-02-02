package com.gear.file.core;

import com.gear.file.annotation.FileModel;
import com.gear.file.exception.GearFileException;
import com.gear.file.strategy.FileParser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class FileEngine implements DownloadService {

    private final ResourceLoader resourceLoader;
    private final List<FileParser> parsers;

    public <T> List<T> importFile(InputStream is, Class<T> clazz, String fileName) {
        log.info("开始解析文件: {}", fileName);
        String suffix = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

        FileParser parser = parsers.stream()
                .filter(p -> p.support(suffix))
                .findFirst()
                .orElseThrow(() -> new GearFileException("不支持的解析格式: " + suffix));

        return parser.parse(is, clazz);
    }

    public void downloadTemplate(HttpServletResponse response, Class<?> clazz) {
        FileModel anno = clazz.getAnnotation(FileModel.class);
        if (anno == null) throw new GearFileException("实体类缺少 @FileModel 注解");

        try {
            Resource res = resourceLoader.getResource("classpath:" + anno.path());
            try (InputStream is = res.getInputStream()) {
                download(response, anno.showName(), is);
            }
        } catch (Exception e) {
            throw new GearFileException("模板下载失败: " + e.getMessage(), e);
        }
    }
}
