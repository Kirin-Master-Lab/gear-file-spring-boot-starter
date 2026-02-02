package com.gear.file.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
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
    String showName() default "";  // 下
}
