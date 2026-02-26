package com.gear.file.core;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public interface DownloadService {

    default void downloadTemplateDynamic(HttpServletResponse resp, String showFileName, Class<?> clazz) throws IOException {
        setExcelResponseHeader(resp, showFileName);
        EasyExcel.write(resp.getOutputStream(), clazz)
                .sheet("Sheet1")
                .doWrite(new ArrayList<>());
    }

    default <T> void exportExcel(HttpServletResponse resp, String showFileName, Class<T> clazz, List<T> data) throws IOException {
        setExcelResponseHeader(resp, showFileName);
        EasyExcel.write(resp.getOutputStream(), clazz)
                .sheet("Sheet1")
                .doWrite(data);
    }

    default <T> void exportBigExcel(HttpServletResponse resp, String showFileName, Class<T> clazz,
                                    Function<Integer, List<T>> pageDataSupplier) throws IOException {
        setExcelResponseHeader(resp, showFileName);
        try (ExcelWriter excelWriter = EasyExcel.write(resp.getOutputStream(), clazz).build()) {
            WriteSheet writeSheet = EasyExcel.writerSheet("Sheet1").build();
            int page = 1;
            while (true) {
                List<T> dataList = pageDataSupplier.apply(page);
                if (dataList == null || dataList.isEmpty()) {
                    break;
                }
                excelWriter.write(dataList, writeSheet);
                page++;
            }
        }
    }

    /**
     * 纯动态表头导出 (无需 DTO，适用于自定义台账、大盘数据导出)
     */
    default void exportDynamicExcel(HttpServletResponse resp, String showFileName,
                                    List<List<String>> headers, List<List<Object>> data) throws IOException {
        setExcelResponseHeader(resp, showFileName);
        EasyExcel.write(resp.getOutputStream())
                .head(headers)
                .sheet("Sheet1")
                .doWrite(data);
    }

    private void setExcelResponseHeader(HttpServletResponse resp, String showFileName) throws IOException {
        // 替换 "+" 为 "%20"，彻底解决文件名带空格变成加号的 Web 历史遗留 Bug
        String encodedFileName = URLEncoder.encode(showFileName, StandardCharsets.UTF_8).replace("+", "%20");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + ".xlsx\"");
        resp.setHeader("filename", encodedFileName);
    }
}