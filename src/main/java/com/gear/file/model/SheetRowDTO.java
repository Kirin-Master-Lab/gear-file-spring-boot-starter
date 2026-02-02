package com.gear.file.model;

import com.alibaba.excel.annotation.ExcelIgnore;
import lombok.Data;

@Data
public abstract class SheetRowDTO {


    @ExcelIgnore
    private Integer lineNumber;
}
