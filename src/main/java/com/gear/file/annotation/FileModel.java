package com.gear.file.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FileModel {

    /**
     * 从该路径下获取文件资源响应给前端
     */
    String path() default "";      //

    /**
     * 前端下载时显示的文件名设置在响应头里
     */
    String showName() default "";


    /**
     * 解析时的表头行数索引数从0开始
     * 一般索引0都是表头,默认从索引1开始读取数据
     */
    int headRowNumber() default 1;// 下
}
