package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.JobCompanyService;
import com.job.bootstrap.service.JobPositionService;
import com.job.common.dto.position.PositionPageDTO;
import com.job.common.dto.position.PositionSaveDTO;
import com.job.common.entity.base.PageResult;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.company.JobCompany;
import com.job.common.entity.position.JobPosition;
import com.job.common.vo.position.PositionImportVO;
import com.job.common.vo.position.PositionVO;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 作者:hfj
 * 功能:后台岗位管理接口，提供岗位列表、增删改查、发布下架和 Excel 导入功能
 * 日期:2026/6/6 15:20
 */
@Validated
@RestController
@RequestMapping("/admin/job")
@RequiredArgsConstructor
public class AdminPositionController {

    private final JobPositionService jobPositionService;

    private final JobCompanyService jobCompanyService;

    /**
     * 分页查询岗位列表。
     * P表示参数描述，后台可以看到草稿和已发布岗位，因此 onlyPublished 固定传 false。
     *
     * @param request 分页查询参数
     * @return 返回岗位分页列表
     */
    @GetMapping("/page")
    public Result<PageResult<PositionVO>> page(@Valid PositionPageDTO request) {
        IPage<JobPosition> positionPage = jobPositionService.pagePositions(
                request.getPageNo(),
                request.getPageSize(),
                request.getKeyword(),
                request.getCompanyId(),
                request.getCity(),
                request.getDistrict(),
                request.getJobCategory(),
                request.getEducationReq(),
                request.getExperienceReq(),
                request.getWorkType(),
                request.getStatus(),
                false
        );

        List<PositionVO> records = convertToVOList(positionPage.getRecords());
        PageResult<PositionVO> pageResult = new PageResult<>(
                records,
                positionPage.getTotal(),
                positionPage.getCurrent(),
                positionPage.getSize()
        );
        return Result.build(pageResult, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询岗位详情。
     *
     * @param id 岗位ID
     * @return 返回岗位详情
     */
    @GetMapping("/{id}")
    public Result<PositionVO> detail(@PathVariable Long id) {
        JobPosition position = jobPositionService.getPositionRequired(id);
        JobCompany company = jobCompanyService.getById(position.getCompanyId());
        return Result.build(PositionVO.from(position, company), ResultCodeEnum.SUCCESS);
    }

    /**
     * 新增岗位。
     *
     * @param request 岗位表单参数
     * @return 返回新增后的岗位
     */
    @PostMapping
    public Result<PositionVO> create(@Valid @RequestBody PositionSaveDTO request) {
        JobPosition position = jobPositionService.createPosition(request);
        JobCompany company = jobCompanyService.getById(position.getCompanyId());
        return Result.build(PositionVO.from(position, company), ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改岗位。
     *
     * @param id 岗位ID
     * @param request 岗位表单参数
     * @return 返回修改后的岗位
     */
    @PutMapping("/{id}")
    public Result<PositionVO> update(@PathVariable Long id, @Valid @RequestBody PositionSaveDTO request) {
        JobPosition position = jobPositionService.updatePosition(id, request);
        JobCompany company = jobCompanyService.getById(position.getCompanyId());
        return Result.build(PositionVO.from(position, company), ResultCodeEnum.SUCCESS);
    }

    /**
     * 逻辑删除岗位。
     *
     * @param id 岗位ID
     * @return 返回空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jobPositionService.deletePosition(id);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 发布岗位。
     * P表示参数描述，发布后 status 会变成 1，前台用户才能搜索到这个岗位。
     *
     * @param id 岗位ID
     * @return 返回发布后的岗位
     */
    @PutMapping("/{id}/publish")
    public Result<PositionVO> publish(@PathVariable Long id) {
        JobPosition position = jobPositionService.publishPosition(id);
        JobCompany company = jobCompanyService.getById(position.getCompanyId());
        return Result.build(PositionVO.from(position, company), ResultCodeEnum.SUCCESS);
    }

    /**
     * 下架岗位。
     * P表示参数描述，下架后 status 会变成 0，前台用户看不到这个岗位。
     *
     * @param id 岗位ID
     * @return 返回下架后的岗位
     */
    @PutMapping("/{id}/offline")
    public Result<PositionVO> offline(@PathVariable Long id) {
        JobPosition position = jobPositionService.offlinePosition(id);
        JobCompany company = jobCompanyService.getById(position.getCompanyId());
        return Result.build(PositionVO.from(position, company), ResultCodeEnum.SUCCESS);
    }

    /**
     * Excel 导入岗位。
     * P表示参数描述，前端字段名必须是 file，后端才能通过 @RequestPart 读取到文件。
     *
     * @param file Excel 文件
     * @return 返回导入统计
     */
    @PostMapping("/import")
    public Result<PositionImportVO> importExcel(@RequestPart("file") MultipartFile file) {
        PositionImportVO importResult = jobPositionService.importPositions(file);
        return Result.build(importResult, ResultCodeEnum.SUCCESS);
    }

    /**
     * 批量把岗位实体转换成 VO。
     *
     * @param positions 岗位实体列表
     * @return 返回岗位 VO 列表
     */
    private List<PositionVO> convertToVOList(List<JobPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, JobCompany> companyMap = jobCompanyService.listByIds(
                        positions.stream().map(JobPosition::getCompanyId).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(JobCompany::getId, Function.identity(), (left, right) -> left));

        return positions.stream()
                .map(position -> PositionVO.from(position, companyMap.get(position.getCompanyId())))
                .toList();
    }
}
