package com.gear.file.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FileModel {

    /**
     * 下载时显示的文件名
     */
    String showName() default "导入模板";

    /**
     * 解析时的表头行数[表头行数默认是从0开始的]
     * 默认1行。如果是复杂表头，业务类上可指定为2、3等
     */
    int headRowNumber() default 1;

    /**
     * 是否开启合并单元格解析
     * 默认 false。只有明确知道模板中有合并单元格时才设为 true，可使普通文件解析性能翻倍。
     */
    boolean enableMerge() default false;

    /**
     * 批处理阈值 (防 OOM 和 批量入库性能调优)
     * 默认 1000。窄表可调大至 5000 榨干 DB 性能，超大宽表可调小至 500 防止内存抖动。
     */
    int batchSize() default 1000;
}