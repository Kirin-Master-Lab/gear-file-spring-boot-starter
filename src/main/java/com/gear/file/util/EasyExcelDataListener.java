package com.gear.file.util;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.CellExtra;
import com.gear.file.model.SheetRowDTO;
import lombok.Getter;
import java.util.ArrayList;
import java.util.List;

@Getter
public class EasyExcelDataListener<T extends SheetRowDTO> extends AnalysisEventListener<T> {
    private final List<T> dataList = new ArrayList<>();
    private final List<CellExtra> cellExtras = new ArrayList<>();

    @Override
    public void invoke(T data, AnalysisContext context) {
        // 关键点：记录原始行号，打平逻辑必须依赖它
        data.setLineNumber(context.readRowHolder().getRowIndex());
        dataList.add(data);
    }

    @Override
    public void extra(CellExtra extra, AnalysisContext context) {
        cellExtras.add(extra);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {}
}