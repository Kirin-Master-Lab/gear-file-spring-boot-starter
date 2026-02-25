package com.gear.file.strategy;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

public interface FileParser {


    boolean support(String suffix);


    // 新增：支持自定义校验异常处理
    <T> void parse(InputStream is, Class<T> clazz, Consumer<List<T>> consumer, ExcelValidationHandler<T> validationHandler);
}
