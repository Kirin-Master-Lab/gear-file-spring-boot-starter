package com.gear.file.core;

import com.alibaba.excel.EasyExcel;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public interface DownloadService {


//    default void setDownloadProperty(HttpServletResponse resp, String showFileName) throws IOException {
//        String encodedFileName = URLEncoder.encode(showFileName, StandardCharsets.UTF_8);
//        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
//        resp.setContentType("application/octet-stream");
//        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"");
//        resp.setHeader("filename", encodedFileName);
//    }
//
//    default void download(HttpServletResponse resp,InputStream is) throws IOException {
//        ServletOutputStream os = resp.getOutputStream();
//        FileCopyUtils.copy(is, os);
//        os.flush();
//    }
//    /**
//     * 动态多表头与数据导出
//     *
//     * @param resp          HttpServletResponse
//     * @param showFileName  导出文件名
//     * @param dynamicHeaders 动态多表头数据，格式如: [["表头1"], ["表头2", "子表头"]]
//     * @param data           动态数据列表
//     */
//    default void downloadDynamicExcel(HttpServletResponse resp, String showFileName,
//                                      List<List<String>> dynamicHeaders,
//                                      List<List<Object>> data) throws IOException {
//
//        String encodedFileName = URLEncoder.encode(showFileName, StandardCharsets.UTF_8);
//        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
//        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
//        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + ".xlsx\"");
//
//        // 利用 EasyExcel 直接写出，无需建立对应 DTO
//        EasyExcel.write(resp.getOutputStream())
//                .head(dynamicHeaders)
//                .sheet("Sheet1")
//                .doWrite(data);
//    }

    /**
     * 动态生成模板并下载 (完全依赖 DTO)
     *
     * @param resp         响应
     * @param showFileName 显示的文件名
     * @param clazz        DTO的Class类型
     */
    default void downloadTemplateDynamic(HttpServletResponse resp, String showFileName, Class<?> clazz) throws IOException {
        String encodedFileName = URLEncoder.encode(showFileName, StandardCharsets.UTF_8);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 设置为 Excel 内容类型
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + ".xlsx\"");
        resp.setHeader("filename", encodedFileName);

        // 核心：基于 Class 动态生成表头，写入一个空 List 代表没有数据
        EasyExcel.write(resp.getOutputStream(), clazz)
                .sheet("Sheet1")
                .doWrite(new ArrayList<>());
    }
}
