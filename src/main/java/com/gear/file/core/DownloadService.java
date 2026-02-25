package com.gear.file.core;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public interface DownloadService {


    default void setDownloadProperty(HttpServletResponse resp, String showFileName) throws IOException {
        String encodedFileName = URLEncoder.encode(showFileName, StandardCharsets.UTF_8);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType("application/octet-stream");
        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"");
        resp.setHeader("filename", encodedFileName);
    }

    default void download(HttpServletResponse resp,InputStream is) throws IOException {
        ServletOutputStream os = resp.getOutputStream();
        FileCopyUtils.copy(is, os);
        os.flush();
    }
}
