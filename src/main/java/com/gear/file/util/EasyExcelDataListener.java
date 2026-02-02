package com.gear.file.util;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.CellExtra;
import com.gear.file.model.SheetRowDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Getter
public class EasyExcelDataListener<T extends SheetRowDTO> extends AnalysisEventListener<T> {

    private final List<T> dataList = new ArrayList<>();
    private final List<CellExtra> cellExtras = new ArrayList<>();

    @Override
    public void invoke(T data, AnalysisContext context) {
        // 关键：记录当前行号，用于后续与合并元数据匹配
        data.setLineNumber(context.readRowHolder().getRowIndex());
        dataList.add(data);
    }

    @Override
    public void extra(CellExtra extra, AnalysisContext context) {
        // 只处理合并单元格信息
        if (extra.getType() == com.alibaba.excel.enums.CellExtraTypeEnum.MERGE) {
            cellExtras.add(extra);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("Excel原始数据解析完毕，共读取 {} 行，包含 {} 处合并信息", dataList.size(), cellExtras.size());
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) {
        log.error("解析异常: {}", exception.getMessage());
        throw new RuntimeException(exception);
    }
}