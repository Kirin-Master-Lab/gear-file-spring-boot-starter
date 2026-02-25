package com.gear.file.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.gear.file.annotation.FileModel;
import com.gear.file.model.SheetRowDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@FileModel(
        path = "template/车辆模板.xlsx",
        showName = "VIN批量导入模板.xlsx",
        headRowNumber = 2
)
public class ImportVin extends SheetRowDTO {


    @ExcelProperty(value = "所属部门",index = 0) // 假设这是合并单元格，组件会自动打平
    private String deptName;


    @ExcelProperty(value = "VIN",index = 1)
    private String vin;


}
