package com.gear.file.biz;


import com.gear.file.core.FileEngine;
import com.gear.file.dto.ImportVin;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;

@Slf4j
@RestController
@RequestMapping("/test")
public class TestBiz {


    @Resource
    private FileEngine fileEngine;

    /**
     * 方式一：全量导入（适合数据量较小，如 1000 条以内）
     */
    @PostMapping("/import-sync")
    public List<ImportVin> importSync(@RequestParam("file") MultipartFile file) throws Exception {
        return fileEngine.importFile(
                file.getInputStream(),
                ImportVin.class,
                file.getOriginalFilename()
        );
    }

    /**
     * 方式二：分批回调导入（高性能推荐，直接对接 Service 层）
     */
    @PostMapping("/import-async")
    public String importAsync(@RequestParam("file") MultipartFile file) throws Exception {
        fileEngine.importFileWithCallback(
                file.getInputStream(),
                ImportVin.class,
                file.getOriginalFilename(),
                dataList -> {
                    // 这里的 dataList 已经是打平合并单元格后的“完美数据”
                    log.info("接收到一批数据，行数：{}", dataList.size());

                    // 示例：在这里调用你的业务 Service 进行批量保存
                    // vinService.saveBatch(dataList);
                }
        );
        return "导入解析完成";
    }

    /**
     * 下载模板
     */
    @GetMapping("/template")
    public void downloadTemplate(jakarta.servlet.http.HttpServletResponse response) {
        fileEngine.downloadTemplate(response, ImportVin.class);
    }
}
