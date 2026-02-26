🚀 gear-file-spring-boot-starter 快速上手指南
本组件极大地简化了 Excel 的导入、导出、模板下载以及合并单元格解析，并天然支持 JSR-303 数据校验、多 Sheet 隔离防串数据、防 OOM 批处理。

📦 1. 引入依赖
在业务项目的 pom.xml 中引入组件（版本号以实际发布为准）：

XML
<dependency>
    <groupId>com.gear.infra</groupId>
    <artifactId>gear-file-spring-boot-starter</artifactId>
    <version>1.0.0-RELEASE</version>
</dependency>
📝 2. 核心：定义你的 DTO (数据即模板)
告别维护静态物理 Excel 模板的烦恼。现在，你只需要写好 DTO，模板会自动生成，合并单元格会自动打平！

Java
import com.alibaba.excel.annotation.ExcelProperty;
import com.gear.file.annotation.ExcelSheetName;
import com.gear.file.annotation.FileModel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
// @FileModel 是组件核心注解：
// showName: 下载模板或导出时的默认文件名
// enableMerge = true: 开启合并单元格智能打平 (默认 false，普通表千万别开，不开性能翻倍)
// batchSize = 2000: 达到 2000 条才触发一次入库回调，防 OOM 且榨干数据库性能
@FileModel(showName = "员工台账导入模板", enableMerge = true, batchSize = 2000)
public class UserImportDTO {

    // 魔法注解：自动将当前数据所在的 Sheet 页名称注入到这个字段里
    @ExcelSheetName
    private String sheetName;

    @NotBlank(message = "所属部门不能为空")
    @ExcelProperty("所属部门") // 如果 Excel 里部门是跨行合并的，组件会自动给每一行填充这个部门名！
    private String deptName;

    @NotBlank(message = "员工姓名不能为空")
    @ExcelProperty("员工姓名")
    private String empName;

    @Min(value = 18, message = "未成年人禁止录入")
    @ExcelProperty("年龄")
    private Integer age;
}
🎮 3. 业务调用示例 (Controller / Service)
在任何需要处理 Excel 的地方，直接 @Resource 或构造器注入 FileEngine 即可。

场景一：下载动态模板
自动根据 DTO 上的 @ExcelProperty 实时生成带有表头的 Excel 模板文件给前端下载。

Java
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final FileEngine fileEngine;

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        // 一行代码搞定，连文件名都自动从 @FileModel 里拿了
        fileEngine.downloadTemplate(response, UserImportDTO.class);
    }
}
场景二：极简全量导入 (适合 1万条以内的小配置表)
不需要分批，不需要收集复杂错误，错了直接抛异常中断前端。

Java
@PostMapping("/import-sync")
public List<UserImportDTO> importSync(@RequestParam("file") MultipartFile file) throws Exception {
    // 同步等待，直接返回解析并校验好的所有数据！
    List<UserImportDTO> dataList = fileEngine.importFileSync(
            file.getInputStream(),
            UserImportDTO.class,
            file.getOriginalFilename()
    );
    // userService.saveBatch(dataList);
    return dataList;
}
场景三：🌟 企业级大文件分批导入 + 脏数据收集 (强推)
适合几十万条数据的超大 Excel，绝不 OOM。遇到错误数据不中断，收集错误原因最后统一返回给前端展示。

Java
@PostMapping("/import-async")
public Map<String, Object> importAsync(@RequestParam("file") MultipartFile file) throws Exception {
    List<String> errorLogs = new ArrayList<>(); // 错误收集桶

    fileEngine.importFile(
            file.getInputStream(),
            UserImportDTO.class,
            file.getOriginalFilename(),

            // 1. 成功回调 (每满 2000 条触发一次，dataList 里的都是校验完美的净数据)
            validDataList -> {
                if (errorLogs.isEmpty()) {
                    userService.saveBatch(validDataList); // 没发现脏数据才真实落库
                }
            },

            // 2. 异常策略回调 (JSR-303 校验失败、或格式填错时触发)
            (data, rowIndex, violations) -> {
                // 组件已经把提示翻译成了人话，比如："Sheet[华南区] 第 3 行数据校验失败: 【年龄】未成年人禁止录入"
                String errorMsg = violations.iterator().next().getMessage();
                errorLogs.add(errorMsg);
                return false; // 返回 false 表示丢弃这条脏数据，不让它进入 validDataList
            }
    );

    // 3. 解析完毕，如果有错，把错误清单扔给前端弹窗
    if (!errorLogs.isEmpty()) {
        throw new BusinessException("导入失败，发现脏数据：\n" + String.join("\n", errorLogs));
    }
    return Map.of("msg", "导入成功");
}
场景四：全能数据导出
提供了应对百万级数据量导出的终极方案。

Java
// 1. 同步全量导出 (适合几万条以内)
@GetMapping("/export")
public void exportData(HttpServletResponse response) {
    List<UserImportDTO> allData = userService.listAll();
    fileEngine.exportData(response, UserImportDTO.class, allData);
}

// 2. 🌟 分页异步导出 (防 OOM，适合百万级数据)
@GetMapping("/export-big")
public void exportBigData(HttpServletResponse response) {
    fileEngine.exportBigData(response, UserImportDTO.class, pageIndex -> {
        // pageIndex 从 1 开始，每次回调你只需要去数据库查一页数据返回即可，直到返回空集合结束
        Page<UserImportDTO> page = userService.page(new Page<>(pageIndex, 5000));
        return page.getRecords();
    });
}

// 3. 纯动态导出 (连 DTO 都不用写，直接塞表头数组和数据数组，适合做自定义报表)
@GetMapping("/export-dynamic")
public void exportDynamic(HttpServletResponse response) {
    List<List<String>> headers = List.of(List.of("动态列1"), List.of("动态列2"));
    List<List<Object>> data = List.of(List.of("张三", 18), List.of("李四", 20));
    fileEngine.exportDynamicData(response, "动态大盘报表", headers, data);
}
