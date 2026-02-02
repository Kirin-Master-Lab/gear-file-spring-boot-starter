package com.gear.file.strategy.impl;

import com.gear.file.annotation.FileModel;
import com.gear.file.model.SheetRowDTO;
import com.gear.file.strategy.FileParser;
import com.gear.file.util.EasyExcelReaderUtil;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
public class ExcelFileParser implements FileParser {

    @Override
    public boolean support(String suffix) {
        return "xlsx".equalsIgnoreCase(suffix) || "xls".equalsIgnoreCase(suffix);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void parse(InputStream is, Class<T> clazz, Consumer<List<T>> consumer) {
        // 1. 类型预检：防止 ClassCastException 的运行时滞后
        if (!SheetRowDTO.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("解析类 " + clazz.getSimpleName() + " 必须继承 com.gear.file.model.SheetRowDTO");
        }

        int headRow = 1;
        FileModel anno = clazz.getAnnotation(FileModel.class);
        if (anno != null) {
            headRow = anno.headRowNumber();
        }

        // 2. 委托给工具类，强制转为基类类型进行处理
        EasyExcelReaderUtil.readWithCallback(
                is,
                (Class<? extends SheetRowDTO>) clazz,
                (Consumer) consumer,
                headRow
        );
    }

    @Override
    public <T> List<T> parse(InputStream is, Class<T> clazz) {
        List<T> allData = new ArrayList<>();
        // 复用回调模式，将数据收集到全量 List
        this.parse(is, clazz, allData::addAll);
        return allData;
    }
}
