package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.JobCompanyService;
import com.job.common.dto.company.CompanyPageDTO;
import com.job.common.dto.company.CompanySaveDTO;
import com.job.common.entity.base.PageResult;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.company.JobCompany;
import com.job.common.vo.company.CompanyImportVO;
import com.job.common.vo.company.CompanyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 作者:hfj
 * 功能:后台公司管理接口，提供公司列表、增删改查和 Excel 导入功能
 * 日期:2026/6/6 10:30
 */
@Validated
@RestController
@RequestMapping("/admin/company")
@RequiredArgsConstructor
public class AdminCompanyController {

    private final JobCompanyService jobCompanyService;

    /**
     * 分页查询公司列表。
     * P表示参数描述，keyword 可以按公司名称、行业、城市、地址搜索。
     *
     * @param request 分页查询参数
     * @return 返回公司分页列表
     */
    @GetMapping("/page")
    public Result<PageResult<CompanyVO>> page(@Valid CompanyPageDTO request) {
        IPage<JobCompany> companyPage = jobCompanyService.pageCompanies(
                request.getPageNo(),
                request.getPageSize(),
                request.getKeyword(),
                request.getStatus()
        );

        List<CompanyVO> records = companyPage.getRecords()
                .stream()
                .map(CompanyVO::from)
                .toList();

        PageResult<CompanyVO> pageResult = new PageResult<>(
                records,
                companyPage.getTotal(),
                companyPage.getCurrent(),
                companyPage.getSize()
        );
        return Result.build(pageResult, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询公司详情。
     *
     * @param id 公司 ID
     * @return 返回公司详情
     */
    @GetMapping("/{id}")
    public Result<CompanyVO> detail(@PathVariable Long id) {
        JobCompany company = jobCompanyService.getCompanyRequired(id);
        return Result.build(CompanyVO.from(company), ResultCodeEnum.SUCCESS);
    }

    /**
     * 新增公司。
     *
     * @param request 公司表单参数
     * @return 返回新增后的公司
     */
    @PostMapping
    public Result<CompanyVO> create(@Valid @RequestBody CompanySaveDTO request) {
        JobCompany company = jobCompanyService.createCompany(request);
        return Result.build(CompanyVO.from(company), ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改公司。
     *
     * @param id 公司 ID
     * @param request 公司表单参数
     * @return 返回修改后的公司
     */
    @PutMapping("/{id}")
    public Result<CompanyVO> update(@PathVariable Long id, @Valid @RequestBody CompanySaveDTO request) {
        JobCompany company = jobCompanyService.updateCompany(id, request);
        return Result.build(CompanyVO.from(company), ResultCodeEnum.SUCCESS);
    }

    /**
     * 逻辑删除公司。
     *
     * @param id 公司 ID
     * @return 返回空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jobCompanyService.deleteCompany(id);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * Excel 导入公司。
     * P表示参数描述，前端字段名必须叫 file，后端才能通过 @RequestPart 读取到文件。
     *
     * @param file Excel 文件
     * @return 返回导入统计
     */
    @PostMapping("/import")
    public Result<CompanyImportVO> importExcel(@RequestPart("file") MultipartFile file) {
        CompanyImportVO importResult = jobCompanyService.importCompanies(file);
        return Result.build(importResult, ResultCodeEnum.SUCCESS);
    }
}
