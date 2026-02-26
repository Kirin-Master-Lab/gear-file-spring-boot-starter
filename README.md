# gear-file-spring-boot-starter 全场景使用演示 (Demo)

这份指南提供了 `gear-file-spring-boot-starter` 组件在日常业务中最常见的 6 大核心场景的完整代码示例。

通过引入本组件，业务研发团队无需再维护物理 Excel 模板，所有解析、合并单元格打平、JSR-303 校验、分页防 OOM 处理等底层逻辑均已被框架完全接管。

---

## 1. 规范先行：定义实体类 (DTO)

业务同学只需配置好 DTO，真正做到“代码即模板”。

**`src/main/java/com/gear/file/demo/dto/UserVehicleDTO.java`**

```java
package com.gear.file.demo.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.gear.file.annotation.ExcelSheetName;
import com.gear.file.annotation.ExcelSheetNo;
import com.gear.file.annotation.FileModel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
// 核心配置：定义下载模板名、开启合并单元格智能打平、设置批处理大小榨干 DB 性能
@FileModel(showName = "车辆与员工导入模板", enableMerge = true, batchSize = 3000)
public class UserVehicleDTO {

    // --- 组件专属：上下文自动注入 ---
    @ExcelSheetNo
    private Integer sheetNo;      // 自动注入：当前数据在第几个 Sheet (从 0 开始)

    @ExcelSheetName
    private String sheetName;     // 自动注入：当前数据的 Sheet 名称

    // --- 业务数据：支持分组校验 (Validation Group) ---
    @NotBlank(message = "所属部门不能为空", groups = {InsertGroup.class, UpdateGroup.class})
    @ExcelProperty("所属部门")     // 假设部门在 Excel 里是合并单元格，组件会自动向下打平填充
    private String deptName;

    @NotBlank(message = "员工姓名不能为空", groups = InsertGroup.class)
    @ExcelProperty("员工姓名")
    private String empName;

    @Min(value = 18, message = "年龄不能小于18岁", groups = {InsertGroup.class, UpdateGroup.class})
    @ExcelProperty("年龄")
    private Integer age;

    // 校验分组标识接口（普通项目可写在一个全局常量类里）
    public interface InsertGroup {}
    public interface UpdateGroup {}
}


package com.gear.file.demo.controller;

import com.gear.file.core.FileEngine;
import com.gear.file.demo.dto.UserVehicleDTO;
import com.gear.file.strategy.ExcelValidationHandler;
import jakarta.validation.ConstraintViolation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/demo/file")
@RequiredArgsConstructor
public class FileDemoController {

    // 唯一需要注入的组件引擎
    private final FileEngine fileEngine;

    // ==========================================
    // 场景一：动态模板下载 (无需物理文件)
    // ==========================================
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        fileEngine.downloadTemplate(response, UserVehicleDTO.class);
    }

    // ==========================================
    // 场景二：极简同步导入 (适合万条以内、错了直接抛异常的小配置表)
    // ==========================================
    @PostMapping("/import-sync")
    public List<UserVehicleDTO> importSync(@RequestParam("file") MultipartFile file) throws Exception {
        // 一行代码拿回所有清洗、校验过的完美数据
        List<UserVehicleDTO> dataList = fileEngine.importFileSync(
                file.getInputStream(),
                UserVehicleDTO.class,
                file.getOriginalFilename()
        );
        // 伪代码：userService.saveBatch(dataList);
        return dataList;
    }

    // ==========================================
    // 场景三: 高可用异步导入 (强推！防 OOM + 全量错误收集 + 分组校验)
    // ==========================================
    @PostMapping("/import-async")
    public Map<String, Object> importAsync(@RequestParam("file") MultipartFile file) throws Exception {
        List<String> errorLogs = new ArrayList<>(); // 错误收集桶

        fileEngine.importFile(
                file.getInputStream(),
                UserVehicleDTO.class,
                file.getOriginalFilename(),

                // 1. 成功回调 (每满 batchSize 触发一次，完全防 OOM)
                validDataList -> {
                    if (errorLogs.isEmpty()) {
                        log.info("成功读取 {} 条优质数据，准备入库...", validDataList.size());
                        // 伪代码：userService.saveBatch(validDataList);
                    }
                },

                // 2. 异常接管策略 (使用匿名内部类接管所有异常底线)
                new ExcelValidationHandler<UserVehicleDTO>() {
                    @Override
                    public boolean onValidateFail(UserVehicleDTO data, int rowIndex, Set<ConstraintViolation<UserVehicleDTO>> violations) {
                        // 收集 JSR-303 业务规则校验错误
                        String msg = violations.iterator().next().getMessage();
                        errorLogs.add("第 " + (rowIndex + 1) + " 行，规则冲突: " + msg);
                        return false; // 丢弃该条脏数据
                    }

                    @Override
                    public void onConvertException(int rowIndex, int colIndex, String sheetName, String headName, String badData, Exception ex) {
                        // 收集底层的类型乱填错误 (比如年龄填了"二十")
                        errorLogs.add(String.format("Sheet[%s] 第 %d 行，【%s】填写了无法识别的 '%s'",
                                sheetName, (rowIndex + 1), headName, badData));
                    }
                },

                // 3. 传入校验分组 (比如只触发 InsertGroup 相关的校验规则)
                UserVehicleDTO.InsertGroup.class
        );

        if (!errorLogs.isEmpty()) {
            return Map.of("code", 500, "msg", "发现脏数据，已终止导入", "errors", errorLogs);
        }
        return Map.of("code", 200, "msg", "导入全部成功！");
    }

    // ==========================================
    // 场景四：常规全量导出
    // ==========================================
    @GetMapping("/export-sync")
    public void exportSync(HttpServletResponse response) {
        // 伪代码：List<UserVehicleDTO> dbData = userService.list();
        List<UserVehicleDTO> mockData = List.of(new UserVehicleDTO());

        fileEngine.exportData(response, UserVehicleDTO.class, mockData);
    }

    // ==========================================
    // 场景五：🌟 百万级分页防 OOM 导出
    // ==========================================
    @GetMapping("/export-big")
    public void exportBig(HttpServletResponse response) {
        fileEngine.exportBigData(response, UserVehicleDTO.class, pageIndex -> {
            // pageIndex 会从 1 开始不断递增，直到你返回空的 List 为止
            log.info("组件请求提供第 {} 页数据写入流...", pageIndex);

            // 伪代码：Page<UserVehicleDTO> page = userService.page(new Page<>(pageIndex, 5000));
            // return page.getRecords();

            // 模拟：导出 3 页后结束
            return pageIndex > 3 ? new ArrayList<>() : List.of(new UserVehicleDTO());
        });
    }

    // ==========================================
    // 场景六：🌟 纯动态表头 + 动态数据分页导出 (无需写 DTO，应对大盘自定义报表)
    // ==========================================
    @GetMapping("/export-dynamic")
    public void exportDynamic(HttpServletResponse response) {
        // 1. 动态构造复杂的表头 (哪怕是多级表头也能直接构造)
        List<List<String>> dynamicHeaders = List.of(
                List.of("统计维度", "省份"),
                List.of("统计维度", "城市"),
                List.of("核心指标", "总销售额")
        );

        fileEngine.exportBigDynamicData(response, "全国销售动态大盘.xlsx", dynamicHeaders, pageIndex -> {
            // 同样支持分页，从数据库拉取 List<Map> 或自定义结构后，转为 List<Object>
            if (pageIndex > 2) return new ArrayList<>(); // 模拟 2 页结束

            return List.of(
                    List.of("广东", "深圳", 99999.99),
                    List.of("浙江", "杭州", 88888.88)
            );
        });
    }
}