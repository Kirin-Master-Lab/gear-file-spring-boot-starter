package com.gear.file.strategy;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

public interface FileParser {

    boolean support(String suffix);

    <T> void parse(InputStream is, Class<T> clazz, Consumer<List<T>> consumer, ExcelValidationHandler<T> validationHandler);
}