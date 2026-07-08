package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.job.bootstrap.mapper.JobCompanyMapper;
import com.job.bootstrap.service.JobCompanyService;
import com.job.common.dto.company.CompanySaveDTO;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.company.JobCompany;
import com.job.common.vo.company.CompanyImportVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 公司业务服务实现类。
 *
 * <p>核心职责：负责公司（JobCompany）全生命周期管理，包括分页查询、新增/修改、逻辑删除
 * 以及基于 Excel 的批量导入。提供公司名称唯一性校验与多维度模糊搜索能力。</p>
 *
 * <p>所属业务模块：公司管理模块（Company Management）</p>
 *
 * <p>主要调用链：
 * <pre>
 * JobCompanyController -&gt; JobCompanyService -&gt; JobCompanyServiceImpl
 *                                    |
 *                                    v
 *                              JobCompanyMapper
 * </pre></p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>继承 {@link ServiceImpl}，依赖 {@link JobCompanyMapper} 进行公司持久化操作</li>
 *   <li>被 {@link JobPositionServiceImpl} 依赖，用于岗位保存/发布时的公司存在性校验</li>
 *   <li>通过 {@link MultipartFile} 接收前端 Excel 文件，使用 Apache POI 进行解析</li>
 * </ul></p>
 *
 * <p>设计说明：
 * <ul>
 *   <li>所有写操作均使用 {@link Transactional} 保证事务一致性</li>
 *   <li>Excel 导入采用“按公司名称 upsert”策略，避免管理端出现同名重复数据</li>
 *   <li>公司名称全局唯一，新增和修改时均做重复性校验</li>
 *   <li>逻辑删除时同步禁用公司状态，保证数据一致性</li>
 * </ul></p>
 *
 * @author hfj
 * @since 2026/6/6
 */
@Service
@RequiredArgsConstructor
public class JobCompanyServiceImpl extends ServiceImpl<JobCompanyMapper, JobCompany> implements JobCompanyService {

    /**
     * 正常状态。
     */
    private static final int STATUS_NORMAL = 1;

    /**
     * 禁用状态。
     */
    private static final int STATUS_DISABLED = 0;

    /**
     * 逻辑未删除。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 逻辑已删除。
     */
    private static final int DELETED = 1;

    /**
     * Excel 最大导入行数，防止误传超大文件把接口拖慢。
     */
    private static final int MAX_IMPORT_ROWS = 5000;

    /**
     * 导入文件允许的扩展名。
     */
    private static final Set<String> EXCEL_EXTENSIONS = Set.of(".xls", ".xlsx");

    /**
     * 分页查询公司列表。
     * P表示参数描述，keyword 为空时查询全部未删除公司。
     *
     * @param pageNo 当前页码
     * @param pageSize 每页条数
     * @param keyword 搜索关键词
     * @param status 公司状态
     * @return 返回公司分页数据
     */
    @Override
    public IPage<JobCompany> pageCompanies(Long pageNo, Long pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<JobCompany> wrapper = new LambdaQueryWrapper<JobCompany>()
                .eq(JobCompany::getIsDeleted, NOT_DELETED)
                .orderByDesc(JobCompany::getCreateTime);

        // 1. 状态有值时才筛选，方便列表默认查看全部公司。
        if (status != null) {
            wrapper.eq(JobCompany::getStatus, normalizeStatus(status));
        }

        // 2. 关键词支持公司名称、行业、城市、区县、地址模糊搜索，后台查找会更顺手。
        if (StringUtils.hasText(keyword)) {
            String likeKeyword = keyword.trim();
            wrapper.and(query -> query
                    .like(JobCompany::getCompanyName, likeKeyword)
                    .or()
                    .like(JobCompany::getIndustry, likeKeyword)
                    .or()
                    .like(JobCompany::getCity, likeKeyword)
                    .or()
                    .like(JobCompany::getDistrict, likeKeyword)
                    .or()
                    .like(JobCompany::getAddress, likeKeyword));
        }

        return page(new Page<>(pageNo, pageSize), wrapper);
    }

    /**
     * 新增公司。
     *
     * <p>保存前校验公司名称是否已存在，避免重复数据入库；状态为空时默认正常。</p>
     *
     * @param request 公司表单请求参数
     * @return 返回保存后的公司实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobCompany createCompany(CompanySaveDTO request) {
        String companyName = trimToNull(request.getCompanyName());
        if (existsByCompanyName(companyName, null)) {
            throw new BizException("公司名称已经存在，请换一个名称");
        }

        Date now = new Date();
        JobCompany company = new JobCompany();
        fillCompanyFromDTO(company, request);
        company.setStatus(normalizeStatus(request.getStatus()));
        company.setIsDeleted(NOT_DELETED);
        company.setCreateTime(now);
        company.setUpdateTime(now);
        save(company);
        return company;
    }

    /**
     * 修改公司。
     *
     * <p>根据公司 ID 查询并更新信息，修改时排除自身后校验公司名称唯一性。</p>
     *
     * @param companyId 公司 ID
     * @param request 公司表单请求参数
     * @return 返回修改后的公司实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobCompany updateCompany(Long companyId, CompanySaveDTO request) {
        JobCompany company = getCompanyRequired(companyId);
        String companyName = trimToNull(request.getCompanyName());
        if (existsByCompanyName(companyName, companyId)) {
            throw new BizException("公司名称已经存在，请换一个名称");
        }

        fillCompanyFromDTO(company, request);
        company.setStatus(normalizeStatus(request.getStatus()));
        company.setUpdateTime(new Date());
        updateById(company);
        return getCompanyRequired(companyId);
    }

    /**
     * 逻辑删除公司。
     *
     * <p>将公司标记为已删除，并同步将状态置为禁用，避免已删除公司继续被岗位引用。</p>
     *
     * @param companyId 公司 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCompany(Long companyId) {
        JobCompany company = getCompanyRequired(companyId);
        company.setIsDeleted(DELETED);
        company.setStatus(STATUS_DISABLED);
        company.setUpdateTime(new Date());
        updateById(company);
    }

    /**
     * 根据 ID 查询未删除的公司。
     *
     * <p>若公司不存在或已被逻辑删除，则抛出 {@link BizException}。</p>
     *
     * @param companyId 公司 ID
     * @return 返回公司实体
     */
    @Override
    public JobCompany getCompanyRequired(Long companyId) {
        JobCompany company = getOne(new LambdaQueryWrapper<JobCompany>()
                .eq(JobCompany::getId, companyId)
                .eq(JobCompany::getIsDeleted, NOT_DELETED), false);
        if (company == null) {
            throw new BizException("公司不存在");
        }
        return company;
    }

    /**
     * 从 Excel 导入公司数据。
     * P表示参数描述，表头支持 company_name 这类英文列名，也兼容“公司名称”这类中文列名。
     *
     * @param file 前端上传的 xls 或 xlsx 文件
     * @return 返回导入统计信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompanyImportVO importCompanies(MultipartFile file) {
        validateExcelFile(file);

        CompanyImportVO result = new CompanyImportVO();
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new BizException("Excel 没有可读取的工作表");
            }

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> headerIndexMap = readHeaderIndexMap(sheet.getRow(sheet.getFirstRowNum()), formatter, evaluator);
            if (!headerIndexMap.containsKey("company_name")) {
                throw new BizException("Excel 表头缺少 company_name 或 公司名称");
            }

            int importedRows = 0;
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlankRow(row, formatter, evaluator)) {
                    continue;
                }

                importedRows++;
                result.setTotalRows(result.getTotalRows() + 1);
                if (importedRows > MAX_IMPORT_ROWS) {
                    result.addFailure("第" + (rowIndex + 1) + "行：超过单次最大导入行数 " + MAX_IMPORT_ROWS);
                    break;
                }

                try {
                    JobCompany company = readCompanyFromRow(row, headerIndexMap, formatter, evaluator);
                    upsertCompanyByName(company, result);
                } catch (Exception exception) {
                    // 1. 单行失败不影响其它行导入，前端会展示失败明细。
                    result.addFailure("第" + (rowIndex + 1) + "行：" + exception.getMessage());
                }
            }
            return result;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            printCompanyImportError(file, exception);
            throw new BizException(ResultCodeEnum.SYSTEM_ERROR.getCode(), "公司 Excel 导入失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 将 DTO 中允许编辑的字段复制到公司实体。
     *
     * @param company 公司实体
     * @param request 前端表单参数
     */
    private void fillCompanyFromDTO(JobCompany company, CompanySaveDTO request) {
        company.setCompanyName(trimToNull(request.getCompanyName()));
        company.setLogoUrl(trimToNull(request.getLogoUrl()));
        company.setIndustry(trimToNull(request.getIndustry()));
        company.setCompanySize(trimToNull(request.getCompanySize()));
        company.setFinancingStage(trimToNull(request.getFinancingStage()));
        company.setDescription(trimToNull(request.getDescription()));
        company.setProvince(trimToNull(request.getProvince()));
        company.setCity(trimToNull(request.getCity()));
        company.setDistrict(trimToNull(request.getDistrict()));
        company.setAddress(trimToNull(request.getAddress()));
        company.setLongitude(request.getLongitude());
        company.setLatitude(request.getLatitude());
        company.setProspectScore(request.getProspectScore());
    }

    /**
     * 从 Excel 行中读取公司实体。
     *
     * @param row 当前数据行
     * @param headerIndexMap 表头和列下标映射
     * @param formatter 单元格格式化工具
     * @param evaluator 公式计算工具
     * @return 返回公司实体
     */
    private JobCompany readCompanyFromRow(Row row, Map<String, Integer> headerIndexMap, DataFormatter formatter, FormulaEvaluator evaluator) {
        String companyName = readCell(row, headerIndexMap, formatter, evaluator, "company_name");
        if (!StringUtils.hasText(companyName)) {
            throw new BizException("公司名称不能为空");
        }

        JobCompany company = new JobCompany();
        company.setCompanyName(companyName);
        company.setLogoUrl(readCell(row, headerIndexMap, formatter, evaluator, "logo_url"));
        company.setIndustry(readCell(row, headerIndexMap, formatter, evaluator, "industry"));
        company.setCompanySize(readCell(row, headerIndexMap, formatter, evaluator, "company_size"));
        company.setFinancingStage(readCell(row, headerIndexMap, formatter, evaluator, "financing_stage"));
        company.setDescription(readCell(row, headerIndexMap, formatter, evaluator, "description"));
        company.setProvince(readCell(row, headerIndexMap, formatter, evaluator, "province"));
        company.setCity(readCell(row, headerIndexMap, formatter, evaluator, "city"));
        company.setDistrict(readCell(row, headerIndexMap, formatter, evaluator, "district"));
        company.setAddress(readCell(row, headerIndexMap, formatter, evaluator, "address"));
        company.setLongitude(parseBigDecimal(readCell(row, headerIndexMap, formatter, evaluator, "longitude"), "经度"));
        company.setLatitude(parseBigDecimal(readCell(row, headerIndexMap, formatter, evaluator, "latitude"), "纬度"));
        company.setProspectScore(parseBigDecimal(readCell(row, headerIndexMap, formatter, evaluator, "prospect_score"), "发展前景分数"));
        company.setStatus(normalizeStatus(parseInteger(readCell(row, headerIndexMap, formatter, evaluator, "status"))));
        return company;
    }

    /**
     * 按公司名称新增或更新。
     *
     * @param importCompany 从 Excel 读取到的公司数据
     * @param result 导入结果统计对象
     */
    private void upsertCompanyByName(JobCompany importCompany, CompanyImportVO result) {
        Date now = new Date();
        JobCompany dbCompany = getOne(new LambdaQueryWrapper<JobCompany>()
                .eq(JobCompany::getCompanyName, importCompany.getCompanyName())
                .eq(JobCompany::getIsDeleted, NOT_DELETED), false);

        if (dbCompany == null) {
            importCompany.setIsDeleted(NOT_DELETED);
            importCompany.setCreateTime(now);
            importCompany.setUpdateTime(now);
            save(importCompany);
            result.addInsert();
            return;
        }

        // 1. 重复导入同一家公司时更新资料，避免管理端出现同名重复数据。
        dbCompany.setLogoUrl(importCompany.getLogoUrl());
        dbCompany.setIndustry(importCompany.getIndustry());
        dbCompany.setCompanySize(importCompany.getCompanySize());
        dbCompany.setFinancingStage(importCompany.getFinancingStage());
        dbCompany.setDescription(importCompany.getDescription());
        dbCompany.setProvince(importCompany.getProvince());
        dbCompany.setCity(importCompany.getCity());
        dbCompany.setDistrict(importCompany.getDistrict());
        dbCompany.setAddress(importCompany.getAddress());
        dbCompany.setLongitude(importCompany.getLongitude());
        dbCompany.setLatitude(importCompany.getLatitude());
        dbCompany.setProspectScore(importCompany.getProspectScore());
        dbCompany.setStatus(importCompany.getStatus());
        dbCompany.setUpdateTime(now);
        updateById(dbCompany);
        result.addUpdate();
    }

    /**
     * 读取表头映射。
     *
     * @param headerRow 表头行
     * @param formatter 单元格格式化工具
     * @param evaluator 公式计算工具
     * @return 返回标准字段名和列下标映射
     */
    private Map<String, Integer> readHeaderIndexMap(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (headerRow == null) {
            throw new BizException("Excel 第一行必须是表头");
        }

        Map<String, Integer> headerIndexMap = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell, evaluator);
            String standardHeader = normalizeHeader(header);
            if (StringUtils.hasText(standardHeader)) {
                headerIndexMap.put(standardHeader, cell.getColumnIndex());
            }
        }
        return headerIndexMap;
    }

    /**
     * 将 Excel 表头统一成数据库字段名。
     *
     * @param header 原始表头
     * @return 返回标准字段名
     */
    private String normalizeHeader(String header) {
        if (!StringUtils.hasText(header)) {
            return null;
        }

        String normalized = header.trim()
                .toLowerCase()
                .replace("-", "_")
                .replace(" ", "_");

        return switch (normalized) {
            case "company_name", "公司名称" -> "company_name";
            case "logo_url", "公司logo", "公司_logo", "logo" -> "logo_url";
            case "industry", "行业" -> "industry";
            case "company_size", "公司规模" -> "company_size";
            case "financing_stage", "融资阶段" -> "financing_stage";
            case "description", "公司介绍", "公司简介" -> "description";
            case "province", "省份" -> "province";
            case "city", "城市" -> "city";
            case "district", "区县" -> "district";
            case "address", "公司地址", "详细地址" -> "address";
            case "longitude", "经度" -> "longitude";
            case "latitude", "纬度" -> "latitude";
            case "prospect_score", "发展前景分", "发展前景分数" -> "prospect_score";
            case "status", "状态" -> "status";
            default -> normalized;
        };
    }

    /**
     * 读取指定字段的单元格文本。
     *
     * @param row 当前行
     * @param headerIndexMap 表头映射
     * @param formatter 单元格格式化工具
     * @param evaluator 公式计算工具
     * @param fieldName 标准字段名
     * @return 返回清洗后的单元格字符串
     */
    private String readCell(Row row, Map<String, Integer> headerIndexMap, DataFormatter formatter, FormulaEvaluator evaluator, String fieldName) {
        Integer index = headerIndexMap.get(fieldName);
        if (index == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return null;
        }
        return trimToNull(formatter.formatCellValue(cell, evaluator));
    }

    /**
     * 判断一行是否为空行。
     *
     * @param row 当前行
     * @param formatter 单元格格式化工具
     * @param evaluator 公式计算工具
     * @return true 表示空行
     */
    private boolean isBlankRow(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (StringUtils.hasText(formatter.formatCellValue(cell, evaluator))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验 Excel 文件。
     *
     * @param file 前端上传文件
     */
    private void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要导入的 Excel 文件");
        }

        String filename = file.getOriginalFilename();
        String extension = getExtension(filename);
        if (!EXCEL_EXTENSIONS.contains(extension)) {
            throw new BizException("公司导入只支持 xls 或 xlsx 文件");
        }
    }

    /**
     * 解析小数。
     *
     * @param value 原始字符串
     * @param fieldLabel 字段中文名称
     * @return 返回 BigDecimal，空字符串返回 null
     */
    private BigDecimal parseBigDecimal(String value, String fieldLabel) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new BizException(fieldLabel + "不是合法数字：" + value);
        }
    }

    /**
     * 解析整数。
     *
     * @param value 原始字符串
     * @return 返回 Integer，空字符串返回 null
     */
    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new BizException("状态不是合法整数：" + value);
        }
    }

    /**
     * 统一状态值。
     *
     * @param status 原始状态
     * @return 返回 0 或 1，空值默认正常
     */
    private Integer normalizeStatus(Integer status) {
        if (status == null) {
            return STATUS_NORMAL;
        }
        return status == STATUS_DISABLED ? STATUS_DISABLED : STATUS_NORMAL;
    }

    /**
     * 判断公司名称是否已存在。
     *
     * @param companyName 公司名称
     * @param excludeCompanyId 修改时需要排除的公司 ID
     * @return true 表示已存在
     */
    private boolean existsByCompanyName(String companyName, Long excludeCompanyId) {
        LambdaQueryWrapper<JobCompany> wrapper = new LambdaQueryWrapper<JobCompany>()
                .eq(JobCompany::getCompanyName, companyName)
                .eq(JobCompany::getIsDeleted, NOT_DELETED);

        if (excludeCompanyId != null) {
            wrapper.ne(JobCompany::getId, excludeCompanyId);
        }
        return count(wrapper) > 0;
    }

    /**
     * 获取文件扩展名。
     *
     * @param filename 文件名
     * @return 返回小写扩展名
     */
    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }

    /**
     * 字符串清洗工具。
     *
     * @param value 原始字符串
     * @return 去掉首尾空格后的字符串；没有有效内容时返回 null
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 打印 Excel 导入异常，方便在后台控制台快速定位问题。
     *
     * @param file 上传文件
     * @param exception 原始异常
     */
    private void printCompanyImportError(MultipartFile file, Exception exception) {
        System.err.println();
        System.err.println("========== Job-Agent 公司 Excel 导入异常 ==========");
        System.err.println("触发位置：com.job.bootstrap.service.impl.JobCompanyServiceImpl.importCompanies");
        System.err.println("文件名：" + (file == null ? null : file.getOriginalFilename()));
        System.err.println("文件大小：" + (file == null ? null : file.getSize()));
        System.err.println("异常类型：" + exception.getClass().getName());
        System.err.println("异常信息：" + exception.getMessage());
        exception.printStackTrace(System.err);
        System.err.println("==========================================");
        System.err.println();
    }
}
