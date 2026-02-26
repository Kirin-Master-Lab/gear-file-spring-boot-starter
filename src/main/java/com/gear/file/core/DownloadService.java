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

    /**
     * ✨ 优化点 2：新增同步全量导出 (适用于几万条以内的小批量数据)
     */
    default <T> void exportExcel(HttpServletResponse resp, String showFileName, Class<T> clazz, List<T> data) throws IOException {
        setExcelResponseHeader(resp, showFileName);
        EasyExcel.write(resp.getOutputStream(), clazz)
                .sheet("Sheet1")
                .doWrite(data);
    }

    /**
     * ✨ 优化点 2：新增分页异步导出 (防 OOM，适用于十万/百万级大数据量导出)
     *
     * @param pageDataSupplier 业务侧提供分页数据的回调函数，入参为 page(页码，从1开始)，返回当页数据。返回空集合时结束。
     */
    default <T> void exportBigExcel(HttpServletResponse resp, String showFileName, Class<T> clazz,
                                    Function<Integer, List<T>> pageDataSupplier) throws IOException {
        setExcelResponseHeader(resp, showFileName);

        // 使用 try-with-resources 确保 ExcelWriter 自动 finish 释放内存
        try (ExcelWriter excelWriter = EasyExcel.write(resp.getOutputStream(), clazz).build()) {
            WriteSheet writeSheet = EasyExcel.writerSheet("Sheet1").build();
            int page = 1;
            while (true) {
                List<T> dataList = pageDataSupplier.apply(page);
                if (dataList == null || dataList.isEmpty()) {
                    break; // 数据读取完毕，跳出循环
                }
                excelWriter.write(dataList, writeSheet);
                page++;
            }
        }
    }

    private void setExcelResponseHeader(HttpServletResponse resp, String showFileName) throws IOException {
        String encodedFileName = URLEncoder.encode(showFileName, StandardCharsets.UTF_8);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + ".xlsx\"");
        resp.setHeader("filename", encodedFileName);
    }
}