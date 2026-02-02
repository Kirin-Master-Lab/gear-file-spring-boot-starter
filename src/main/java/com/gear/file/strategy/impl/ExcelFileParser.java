package com.gear.file.strategy.impl;

import com.gear.file.model.SheetRowDTO;
import com.gear.file.strategy.FileParser;
import com.gear.file.util.EasyExcelReaderUtil;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class ExcelFileParser implements FileParser {


    @Override
    public boolean support(String suffix) {
        return suffix.equalsIgnoreCase("xlsx") || suffix.equalsIgnoreCase("xls");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> parse(InputStream is, Class<T> clazz) {
        return (List<T>) EasyExcelReaderUtil.readList(is, (Class<? extends SheetRowDTO>) clazz, 1);
    }
}
