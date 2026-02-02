package com.gear.file.strategy;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

public interface FileParser {


    boolean support(String suffix);

    /**
     * 回调解析
     */
    <T> void parse(InputStream is, Class<T> clazz, Consumer<List<T>> consumer);

    /**
     * 全量解析
     */
    <T> List<T> parse(InputStream is, Class<T> clazz);
}
