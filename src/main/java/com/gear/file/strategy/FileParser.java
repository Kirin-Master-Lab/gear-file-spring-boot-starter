package com.gear.file.strategy;

import java.io.InputStream;
import java.util.List;

public interface FileParser {


    boolean support(String suffix);


    <T> List<T> parse(InputStream is, Class<T> clazz);
}
