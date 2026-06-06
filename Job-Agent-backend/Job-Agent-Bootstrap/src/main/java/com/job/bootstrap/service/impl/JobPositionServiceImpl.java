package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.job.bootstrap.mapper.JobCompanyMapper;
import com.job.bootstrap.mapper.JobPositionMapper;
import com.job.bootstrap.service.JobPositionService;
import com.job.common.dto.position.PositionSaveDTO;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.company.JobCompany;
import com.job.common.entity.position.JobPosition;
import com.job.common.vo.position.PositionImportVO;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 作者:hfj
 * 功能:岗位业务服务实现，处理岗位分页、表单保存、发布下架、逻辑删除和 Excel 导入
 * 日期:2026/6/6 15:20
 */
@Service
@RequiredArgsConstructor
public class JobPositionServiceImpl extends ServiceImpl<JobPositionMapper, JobPosition> implements JobPositionService {

    /**
     * 岗位草稿/下线状态。
     */
    private static final int STATUS_DRAFT = 0;

    /**
     * 岗位已发布状态。
     */
    private static final int STATUS_PUBLISHED = 1;

    /**
     * 逻辑未删除状态。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 逻辑已删除状态。
     */
    private static final int DELETED = 1;

    /**
     * 正常公司状态。
     */
    private static final int COMPANY_STATUS_NORMAL = 1;

    /**
     * 手工录入来源。
     */
    private static final String SOURCE_MANUAL = "MANUAL";

    /**
     * Excel 导入来源。
     */
    private static final String SOURCE_IMPORT = "IMPORT";

    /**
     * 单次 Excel 最大导入行数，防止误上传超大文件拖慢接口。
     */
    private static final int MAX_IMPORT_ROWS = 5000;

    /**
     * 导入文件允许的扩展名。
     */
    private static final Set<String> EXCEL_EXTENSIONS = Set.of(".xls", ".xlsx");

    private final JobCompanyMapper jobCompanyMapper;

    /**
     * 分页查询岗位列表。
     * P表示参数描述，onlyPublished=true 时会强制 status=1，专门给前台岗位列表使用。
     *
     * @param pageNo 当前页码
     * @param pageSize 每页条数
     * @param keyword 搜索关键词
     * @param companyId 公司ID
     * @param city 工作城市
     * @param district 工作区县
     * @param jobCategory 岗位类别
     * @param educationReq 学历要求
     * @param experienceReq 经验要求
     * @param workType 工作类型
     * @param status 岗位状态
     * @param onlyPublished 是否只查已发布
     * @return 返回岗位分页数据
     */
    @Override
    public IPage<JobPosition> pagePositions(Long pageNo, Long pageSize, String keyword, Long companyId, String city,
                                            String district, String jobCategory, String educationReq,
                                            String experienceReq, String workType, Integer status,
                                            boolean onlyPublished) {
        LambdaQueryWrapper<JobPosition> wrapper = new LambdaQueryWrapper<JobPosition>()
                .eq(JobPosition::getIsDeleted, NOT_DELETED);

        if (onlyPublished) {
            wrapper.eq(JobPosition::getStatus, STATUS_PUBLISHED);
        } else if (status != null) {
            wrapper.eq(JobPosition::getStatus, normalizeStatus(status, STATUS_DRAFT));
        }

        if (companyId != null) {
            wrapper.eq(JobPosition::getCompanyId, companyId);
        }
        if (StringUtils.hasText(city)) {
            wrapper.eq(JobPosition::getCity, city.trim());
        }
        if (StringUtils.hasText(district)) {
            wrapper.eq(JobPosition::getDistrict, district.trim());
        }
        if (StringUtils.hasText(jobCategory)) {
            wrapper.eq(JobPosition::getJobCategory, jobCategory.trim());
        }
        if (StringUtils.hasText(educationReq)) {
            wrapper.eq(JobPosition::getEducationReq, educationReq.trim());
        }
        if (StringUtils.hasText(experienceReq)) {
            wrapper.eq(JobPosition::getExperienceReq, experienceReq.trim());
        }
        if (StringUtils.hasText(workType)) {
            wrapper.eq(JobPosition::getWorkType, workType.trim());
        }

        if (StringUtils.hasText(keyword)) {
            String likeKeyword = keyword.trim();
            List<Long> companyIds = findCompanyIdsByName(likeKeyword);

            // 1. 关键词既匹配岗位本身，也匹配公司名称和常用筛选字段，用户输入“本科/远程/后端”都能搜到相关岗位。
            wrapper.and(query -> {
                query.like(JobPosition::getJobTitle, likeKeyword)
                        .or()
                        .like(JobPosition::getJobCategory, likeKeyword)
                        .or()
                        .like(JobPosition::getCity, likeKeyword)
                        .or()
                        .like(JobPosition::getDistrict, likeKeyword)
                        .or()
                        .like(JobPosition::getEducationReq, likeKeyword)
                        .or()
                        .like(JobPosition::getExperienceReq, likeKeyword)
                        .or()
                        .like(JobPosition::getWorkType, likeKeyword)
                        .or()
                        .like(JobPosition::getWelfareTags, likeKeyword)
                        .or()
                        .like(JobPosition::getSkillKeywords, likeKeyword);
                if (!companyIds.isEmpty()) {
                    query.or().in(JobPosition::getCompanyId, companyIds);
                }
            });
        }

        wrapper.orderByDesc(JobPosition::getStatus)
                .orderByDesc(JobPosition::getPublishTime)
                .orderByDesc(JobPosition::getCreateTime);

        return page(new Page<>(pageNo, pageSize), wrapper);
    }

    /**
     * 新增岗位。
     *
     * @param request 岗位表单参数
     * @return 返回新增后的岗位
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobPosition createPosition(PositionSaveDTO request) {
        getCompanyRequired(request.getCompanyId());
        validateSalary(request.getMinSalary(), request.getMaxSalary());

        Date now = new Date();
        JobPosition position = new JobPosition();
        fillPositionFromDTO(position, request);
        position.setSource(defaultSource(request.getSource(), SOURCE_MANUAL));
        position.setStatus(normalizeStatus(request.getStatus(), STATUS_DRAFT));
        position.setPublishTime(position.getStatus() == STATUS_PUBLISHED ? now : null);
        position.setIsDeleted(NOT_DELETED);
        position.setCreateTime(now);
        position.setUpdateTime(now);
        save(position);
        return position;
    }

    /**
     * 修改岗位。
     *
     * @param positionId 岗位ID
     * @param request 岗位表单参数
     * @return 返回修改后的岗位
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobPosition updatePosition(Long positionId, PositionSaveDTO request) {
        JobPosition position = getPositionRequired(positionId);
        getCompanyRequired(request.getCompanyId());
        validateSalary(request.getMinSalary(), request.getMaxSalary());

        Date now = new Date();
        fillPositionFromDTO(position, request);
        position.setSource(defaultSource(request.getSource(), SOURCE_MANUAL));
        position.setStatus(normalizeStatus(request.getStatus(), STATUS_DRAFT));
        if (position.getStatus() == STATUS_PUBLISHED && position.getPublishTime() == null) {
            position.setPublishTime(now);
        }
        if (position.getStatus() == STATUS_DRAFT) {
            position.setPublishTime(null);
        }
        position.setUpdateTime(now);
        updateById(position);
        return getPositionRequired(positionId);
    }

    /**
     * 逻辑删除岗位。
     *
     * @param positionId 岗位ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePosition(Long positionId) {
        JobPosition position = getPositionRequired(positionId);
        position.setStatus(STATUS_DRAFT);
        position.setIsDeleted(DELETED);
        position.setUpdateTime(new Date());
        updateById(position);
    }

    /**
     * 发布岗位。
     *
     * @param positionId 岗位ID
     * @return 返回发布后的岗位
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobPosition publishPosition(Long positionId) {
        JobPosition position = getPositionRequired(positionId);
        getCompanyRequired(position.getCompanyId());

        Date now = new Date();
        position.setStatus(STATUS_PUBLISHED);
        position.setPublishTime(now);
        position.setUpdateTime(now);
        updateById(position);
        return getPositionRequired(positionId);
    }

    /**
     * 下架岗位。
     *
     * @param positionId 岗位ID
     * @return 返回下架后的岗位
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobPosition offlinePosition(Long positionId) {
        JobPosition position = getPositionRequired(positionId);
        position.setStatus(STATUS_DRAFT);
        position.setPublishTime(null);
        position.setUpdateTime(new Date());
        updateById(position);
        return getPositionRequired(positionId);
    }

    /**
     * 根据 ID 查询岗位。
     *
     * @param positionId 岗位ID
     * @return 返回岗位实体
     */
    @Override
    public JobPosition getPositionRequired(Long positionId) {
        JobPosition position = getOne(new LambdaQueryWrapper<JobPosition>()
                .eq(JobPosition::getId, positionId)
                .eq(JobPosition::getIsDeleted, NOT_DELETED), false);
        if (position == null) {
            throw new BizException("岗位不存在");
        }
        return position;
    }

    /**
     * 从 Excel 导入岗位。
     * P表示参数描述，表头支持英文数据库字段名，也兼容常见中文列名。
     *
     * @param file 前端上传的 Excel 文件
     * @return 返回导入统计信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PositionImportVO importPositions(MultipartFile file) {
        validateExcelFile(file);

        PositionImportVO result = new PositionImportVO();
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new BizException("Excel 没有可读取的工作表");
            }

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> headerIndexMap = readHeaderIndexMap(sheet.getRow(sheet.getFirstRowNum()), formatter, evaluator);
            if (!headerIndexMap.containsKey("job_title")) {
                throw new BizException("Excel 表头缺少 job_title 或 岗位名称");
            }
            if (!headerIndexMap.containsKey("company_id") && !headerIndexMap.containsKey("company_name")) {
                throw new BizException("Excel 表头必须包含 company_id 或 company_name");
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
                    result.addFailure("第" + (rowIndex + 1) + "行：超过单次最大导入行数" + MAX_IMPORT_ROWS);
                    break;
                }

                try {
                    JobPosition position = readPositionFromRow(row, headerIndexMap, formatter, evaluator);
                    upsertPosition(position, result);
                } catch (Exception exception) {
                    // 1. 单行失败不影响其它行导入，前端会展示失败明细。
                    result.addFailure("第" + (rowIndex + 1) + "行：" + exception.getMessage());
                }
            }
            return result;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            printPositionImportError(file, exception);
            throw new BizException(ResultCodeEnum.SYSTEM_ERROR.getCode(), "岗位 Excel 导入失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 把 DTO 中允许编辑的字段复制到岗位实体。
     *
     * @param position 岗位实体
     * @param request 前端表单参数
     */
    private void fillPositionFromDTO(JobPosition position, PositionSaveDTO request) {
        position.setCompanyId(request.getCompanyId());
        position.setJobTitle(trimToNull(request.getJobTitle()));
        position.setJobCategory(trimToNull(request.getJobCategory()));
        position.setCity(trimToNull(request.getCity()));
        position.setDistrict(trimToNull(request.getDistrict()));
        position.setMinSalary(request.getMinSalary());
        position.setMaxSalary(request.getMaxSalary());
        position.setSalaryMonths(request.getSalaryMonths());
        position.setEducationReq(trimToNull(request.getEducationReq()));
        position.setExperienceReq(trimToNull(request.getExperienceReq()));
        position.setJobDescription(trimToNull(request.getJobDescription()));
        position.setJobRequirement(trimToNull(request.getJobRequirement()));
        position.setSkillKeywords(trimToNull(request.getSkillKeywords()));
        position.setWorkType(trimToNull(request.getWorkType()));
        position.setWelfareTags(trimToNull(request.getWelfareTags()));
        position.setSourceUrl(trimToNull(request.getSourceUrl()));
    }

    /**
     * 从 Excel 行读取岗位实体。
     *
     * @param row 当前数据行
     * @param headerIndexMap 表头映射
     * @param formatter 单元格格式化工具
     * @param evaluator 公式计算工具
     * @return 返回岗位实体
     */
    private JobPosition readPositionFromRow(Row row, Map<String, Integer> headerIndexMap,
                                            DataFormatter formatter, FormulaEvaluator evaluator) {
        String jobTitle = readCell(row, headerIndexMap, formatter, evaluator, "job_title");
        if (!StringUtils.hasText(jobTitle)) {
            throw new BizException("岗位名称不能为空");
        }

        String city = readCell(row, headerIndexMap, formatter, evaluator, "city");
        String district = readCell(row, headerIndexMap, formatter, evaluator, "district");
        Long companyId = resolveCompanyId(row, headerIndexMap, formatter, evaluator, city, district);

        JobPosition position = new JobPosition();
        position.setCompanyId(companyId);
        position.setJobTitle(jobTitle);
        position.setJobCategory(readCell(row, headerIndexMap, formatter, evaluator, "job_category"));
        position.setCity(city);
        position.setDistrict(district);
        position.setMinSalary(parseInteger(readCell(row, headerIndexMap, formatter, evaluator, "min_salary"), "最低薪资"));
        position.setMaxSalary(parseInteger(readCell(row, headerIndexMap, formatter, evaluator, "max_salary"), "最高薪资"));
        position.setSalaryMonths(parseInteger(readCell(row, headerIndexMap, formatter, evaluator, "salary_months"), "薪资月份"));
        validateSalary(position.getMinSalary(), position.getMaxSalary());
        position.setEducationReq(readCell(row, headerIndexMap, formatter, evaluator, "education_req"));
        position.setExperienceReq(readCell(row, headerIndexMap, formatter, evaluator, "experience_req"));
        position.setJobDescription(readCell(row, headerIndexMap, formatter, evaluator, "job_description"));
        position.setJobRequirement(readCell(row, headerIndexMap, formatter, evaluator, "job_requirement"));
        position.setSkillKeywords(readCell(row, headerIndexMap, formatter, evaluator, "skill_keywords"));
        position.setWorkType(readCell(row, headerIndexMap, formatter, evaluator, "work_type"));
        position.setWelfareTags(readCell(row, headerIndexMap, formatter, evaluator, "welfare_tags"));
        position.setSource(defaultSource(readCell(row, headerIndexMap, formatter, evaluator, "source"), SOURCE_IMPORT));
        position.setSourceUrl(readCell(row, headerIndexMap, formatter, evaluator, "source_url"));
        position.setStatus(parseStatus(readCell(row, headerIndexMap, formatter, evaluator, "status"), STATUS_DRAFT));
        return position;
    }

    /**
     * 按公司、岗位名称、城市新增或更新岗位。
     *
     * @param importPosition Excel 读取到的岗位
     * @param result 导入统计对象
     */
    private void upsertPosition(JobPosition importPosition, PositionImportVO result) {
        Date now = new Date();
        JobPosition dbPosition = getOne(new LambdaQueryWrapper<JobPosition>()
                .eq(JobPosition::getCompanyId, importPosition.getCompanyId())
                .eq(JobPosition::getJobTitle, importPosition.getJobTitle())
                .eq(StringUtils.hasText(importPosition.getCity()), JobPosition::getCity, importPosition.getCity())
                .eq(JobPosition::getIsDeleted, NOT_DELETED), false);

        if (dbPosition == null) {
            importPosition.setIsDeleted(NOT_DELETED);
            importPosition.setPublishTime(importPosition.getStatus() == STATUS_PUBLISHED ? now : null);
            importPosition.setCreateTime(now);
            importPosition.setUpdateTime(now);
            save(importPosition);
            result.addInsert();
            return;
        }

        // 1. 重复导入同一个公司同一个城市的同名岗位时更新资料，避免后台出现重复岗位。
        dbPosition.setJobCategory(importPosition.getJobCategory());
        dbPosition.setCity(importPosition.getCity());
        dbPosition.setDistrict(importPosition.getDistrict());
        dbPosition.setMinSalary(importPosition.getMinSalary());
        dbPosition.setMaxSalary(importPosition.getMaxSalary());
        dbPosition.setSalaryMonths(importPosition.getSalaryMonths());
        dbPosition.setEducationReq(importPosition.getEducationReq());
        dbPosition.setExperienceReq(importPosition.getExperienceReq());
        dbPosition.setJobDescription(importPosition.getJobDescription());
        dbPosition.setJobRequirement(importPosition.getJobRequirement());
        dbPosition.setSkillKeywords(importPosition.getSkillKeywords());
        dbPosition.setWorkType(importPosition.getWorkType());
        dbPosition.setWelfareTags(importPosition.getWelfareTags());
        dbPosition.setSource(importPosition.getSource());
        dbPosition.setSourceUrl(importPosition.getSourceUrl());
        dbPosition.setStatus(importPosition.getStatus());
        dbPosition.setPublishTime(importPosition.getStatus() == STATUS_PUBLISHED ? now : null);
        dbPosition.setUpdateTime(now);
        updateById(dbPosition);
        result.addUpdate();
    }

    /**
     * 根据 company_id 或 company_name 解析公司ID。
     * P表示参数描述，Excel 只填 company_name 时会自动匹配公司，匹配不到则创建最小公司信息。
     *
     * @param row 当前数据行
     * @param headerIndexMap 表头映射
     * @param formatter 单元格格式化工具
     * @param evaluator 公式计算工具
     * @param city 岗位城市
     * @param district 岗位区域
     * @return 返回公司ID
     */
    private Long resolveCompanyId(Row row, Map<String, Integer> headerIndexMap, DataFormatter formatter,
                                  FormulaEvaluator evaluator, String city, String district) {
        Long companyId = parseLong(readCell(row, headerIndexMap, formatter, evaluator, "company_id"), "公司ID");
        if (companyId != null) {
            getCompanyRequired(companyId);
            return companyId;
        }

        String companyName = readCell(row, headerIndexMap, formatter, evaluator, "company_name");
        if (!StringUtils.hasText(companyName)) {
            throw new BizException("公司ID和公司名称不能同时为空");
        }

        JobCompany company = jobCompanyMapper.selectOne(new LambdaQueryWrapper<JobCompany>()
                .eq(JobCompany::getCompanyName, companyName)
                .eq(JobCompany::getIsDeleted, NOT_DELETED)
                .last("limit 1"));
        if (company != null) {
            return company.getId();
        }

        Date now = new Date();
        JobCompany newCompany = new JobCompany();
        newCompany.setCompanyName(companyName.trim());
        newCompany.setCity(trimToNull(city));
        newCompany.setDistrict(trimToNull(district));
        newCompany.setStatus(COMPANY_STATUS_NORMAL);
        newCompany.setIsDeleted(NOT_DELETED);
        newCompany.setCreateTime(now);
        newCompany.setUpdateTime(now);
        jobCompanyMapper.insert(newCompany);
        return newCompany.getId();
    }

    /**
     * 根据公司名称模糊查询公司ID。
     *
     * @param keyword 搜索关键词
     * @return 返回匹配公司ID列表
     */
    private List<Long> findCompanyIdsByName(String keyword) {
        return jobCompanyMapper.selectList(new LambdaQueryWrapper<JobCompany>()
                        .select(JobCompany::getId)
                        .eq(JobCompany::getIsDeleted, NOT_DELETED)
                        .like(JobCompany::getCompanyName, keyword))
                .stream()
                .map(JobCompany::getId)
                .toList();
    }

    /**
     * 根据 ID 查询公司。
     *
     * @param companyId 公司ID
     * @return 返回公司实体
     */
    private JobCompany getCompanyRequired(Long companyId) {
        JobCompany company = jobCompanyMapper.selectOne(new LambdaQueryWrapper<JobCompany>()
                .eq(JobCompany::getId, companyId)
                .eq(JobCompany::getIsDeleted, NOT_DELETED)
                .last("limit 1"));
        if (company == null) {
            throw new BizException("公司不存在，请先维护公司信息");
        }
        return company;
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
     * 把 Excel 表头统一成数据库字段名。
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
            case "company_id", "公司id", "公司ID" -> "company_id";
            case "company_name", "公司名称", "公司" -> "company_name";
            case "job_title", "title", "岗位名称", "职位名称", "岗位" -> "job_title";
            case "job_category", "岗位类别", "职位类别", "类别" -> "job_category";
            case "city", "城市", "工作城市" -> "city";
            case "district", "区域", "区县", "工作区域" -> "district";
            case "min_salary", "最低薪资", "最低薪酬" -> "min_salary";
            case "max_salary", "最高薪资", "最高薪酬" -> "max_salary";
            case "salary_months", "薪资月份", "薪资月数" -> "salary_months";
            case "education_req", "学历要求", "学历" -> "education_req";
            case "experience_req", "经验要求", "工作经验", "经验" -> "experience_req";
            case "job_description", "岗位描述", "工作内容" -> "job_description";
            case "job_requirement", "岗位要求", "任职要求" -> "job_requirement";
            case "skill_keywords", "技能关键词", "技能", "关键词" -> "skill_keywords";
            case "work_type", "工作类型" -> "work_type";
            case "welfare_tags", "福利标签", "福利" -> "welfare_tags";
            case "source", "岗位来源", "来源" -> "source";
            case "source_url", "岗位来源链接", "来源链接" -> "source_url";
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
    private String readCell(Row row, Map<String, Integer> headerIndexMap, DataFormatter formatter,
                            FormulaEvaluator evaluator, String fieldName) {
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

        String extension = getExtension(file.getOriginalFilename());
        if (!EXCEL_EXTENSIONS.contains(extension)) {
            throw new BizException("岗位导入只支持 xls 或 xlsx 文件");
        }
    }

    /**
     * 校验薪资区间。
     *
     * @param minSalary 最低薪资
     * @param maxSalary 最高薪资
     */
    private void validateSalary(Integer minSalary, Integer maxSalary) {
        if (minSalary != null && maxSalary != null && minSalary > maxSalary) {
            throw new BizException("最低薪资不能大于最高薪资");
        }
    }

    /**
     * 解析岗位状态。
     *
     * @param value 原始状态文本
     * @param defaultStatus 默认状态
     * @return 返回 0 或 1
     */
    private Integer parseStatus(String value, int defaultStatus) {
        if (!StringUtils.hasText(value)) {
            return defaultStatus;
        }
        String text = value.trim();
        if ("1".equals(text) || "已发布".equals(text) || "发布".equals(text) || "上架".equals(text)) {
            return STATUS_PUBLISHED;
        }
        if ("0".equals(text) || "草稿".equals(text) || "未发布".equals(text) || "下架".equals(text)) {
            return STATUS_DRAFT;
        }
        throw new BizException("状态只能填写 0、1、草稿、下架、已发布或上架");
    }

    /**
     * 统一状态值。
     *
     * @param status 原始状态
     * @param defaultStatus 默认状态
     * @return 返回 0 或 1
     */
    private Integer normalizeStatus(Integer status, int defaultStatus) {
        if (status == null) {
            return defaultStatus;
        }
        return status == STATUS_PUBLISHED ? STATUS_PUBLISHED : STATUS_DRAFT;
    }

    /**
     * 解析 Long。
     *
     * @param value 原始字符串
     * @param fieldLabel 字段中文名称
     * @return 返回 Long，空字符串返回 null
     */
    private Long parseLong(String value, String fieldLabel) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim()).longValueExact();
        } catch (Exception exception) {
            throw new BizException(fieldLabel + "不是合法整数：" + value);
        }
    }

    /**
     * 解析整数。
     *
     * @param value 原始字符串
     * @param fieldLabel 字段中文名称
     * @return 返回 Integer，空字符串返回 null
     */
    private Integer parseInteger(String value, String fieldLabel) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", "")).intValueExact();
        } catch (Exception exception) {
            throw new BizException(fieldLabel + "不是合法整数：" + value);
        }
    }

    /**
     * 给来源字段设置默认值。
     *
     * @param source 原始来源
     * @param defaultSource 默认来源
     * @return 返回来源
     */
    private String defaultSource(String source, String defaultSource) {
        String cleanedSource = trimToNull(source);
        return cleanedSource == null ? defaultSource : cleanedSource;
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
    private void printPositionImportError(MultipartFile file, Exception exception) {
        System.err.println();
        System.err.println("========== Job-Agent 岗位 Excel 导入异常 ==========");
        System.err.println("触发位置：com.job.bootstrap.service.impl.JobPositionServiceImpl.importPositions");
        System.err.println("文件名：" + (file == null ? null : file.getOriginalFilename()));
        System.err.println("文件大小：" + (file == null ? null : file.getSize()));
        System.err.println("异常类型：" + exception.getClass().getName());
        System.err.println("异常信息：" + exception.getMessage());
        exception.printStackTrace(System.err);
        System.err.println("==========================================");
        System.err.println();
    }
}
