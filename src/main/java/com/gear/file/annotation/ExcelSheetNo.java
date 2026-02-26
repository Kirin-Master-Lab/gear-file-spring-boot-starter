package com.gear.file.annotation;

import java.lang.annotation.*;

/**
 * 标记在 DTO 字段上，解析时自动注入当前数据所在的 Sheet 序号 (从 0 开始)
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExcelSheetNo {
}