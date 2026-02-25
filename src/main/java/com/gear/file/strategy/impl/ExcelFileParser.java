package com.gear.file.strategy.impl;

import com.gear.file.annotation.FileModel;
import com.gear.file.strategy.ExcelValidationHandler;
import com.gear.file.strategy.FileParser;
import com.gear.file.util.EasyExcelReaderUtil;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

@Component
public class ExcelFileParser implements FileParser {

    @Override
    public boolean support(String suffix) {
        return "xlsx".equalsIgnoreCase(suffix) || "xls".equalsIgnoreCase(suffix);
    }

    @Override
    public <T> void parse(InputStream is, Class<T> clazz, Consumer<List<T>> consumer, ExcelValidationHandler<T> validationHandler) {
        int headRow = 1;
        FileModel anno = clazz.getAnnotation(FileModel.class);
        if (anno != null) {
            headRow = anno.headRowNumber();
        }
        EasyExcelReaderUtil.readWithCallback(is, clazz, consumer, headRow, validationHandler);
    }
}