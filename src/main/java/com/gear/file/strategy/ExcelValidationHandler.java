package com.gear.file.strategy;

import jakarta.validation.ConstraintViolation;
import java.util.Set;

/**
 * Excel 数据校验异常处理策略
 */
@FunctionalInterface
public interface ExcelValidationHandler<T> {

    /**
     * 当数据行校验失败时触发
     *
     * @param data       当前校验失败的完整数据对象
     * @param rowIndex   当前的 Excel 行号 (注意：从0开始，包含表头)
     * @param violations 具体的校验错误信息集合
     * @return boolean   是否继续保留该条数据？
     * 返回 true: 即使校验失败，依然将该数据加入结果集传递给 Consumer。
     * 返回 false: 丢弃该条数据。
     * (也可在此方法内部直接抛出 RuntimeException 中断整个解析过程)
     */
    boolean onValidateFail(T data, int rowIndex, Set<ConstraintViolation<T>> violations);
}