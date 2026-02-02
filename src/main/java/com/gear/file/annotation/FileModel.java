package com.gear.file.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FileModel {

    /**
     * 模板在 resources 下的路径
     *
     * @return
     */
    String path() default "";      //

    /**
     * 载时显示的文件名
     *
     * @return
     */
    String showName() default "";


    /**
     * 解析时的表头行数[表头行数默认是从0开始的]
     * 默认1行。如果是复杂表头，业务类上可指定为2、3等
     */
    int headRowNumber() default 1;// 下
}
