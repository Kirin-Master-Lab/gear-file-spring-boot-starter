package com.gear.file.strategy;

import com.gear.file.exception.GearFileException;
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


    /**
     * 当底层发生数据类型转换异常时触发 (例如 Excel 里填了 "二十" 导致无法转为 Integer)
     * * @param rowIndex  行号 (从0开始)
     * @param colIndex  列号 (从0开始)
     * @param sheetName Sheet名称
     * @param headName  中文表头名称
     * @param badData   用户填写的错误数据字符串
     * @param ex        EasyExcel 底层异常对象
     * * 注意：如果业务方重写此方法并且【不抛出异常】（即吞掉异常），EasyExcel 会将该单元格的值置为 null，并继续解析该行的后续列，最终依然会进入 onValidateFail 或 Consumer。
     */
    default void onConvertException(int rowIndex, int colIndex, String sheetName, String headName, String badData, Exception ex) {
        // 组件默认行为：抛出人话异常，中断解析
        String errorMsg = String.format("【%s】 数据格式不正确，无法解析输入的内容: '%s'", headName, badData);
        throw new GearFileException("Sheet[" + sheetName + "] 第 " + (rowIndex + 1) + " 行，" + errorMsg);
    }
}