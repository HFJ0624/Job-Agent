package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.JobCompanyService;
import com.job.bootstrap.service.JobInteractionService;
import com.job.bootstrap.service.JobPositionService;
import com.job.common.dto.interaction.JobCommunicationDTO;
import com.job.common.dto.position.PositionPageDTO;
import com.job.common.entity.base.PageResult;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.company.JobCompany;
import com.job.common.entity.interaction.JobPositionMessage;
import com.job.common.entity.position.JobPosition;
import com.job.common.vo.interaction.FavoriteStateVO;
import com.job.common.vo.interaction.JobMessageVO;
import com.job.common.vo.position.PositionDetailVO;
import com.job.common.vo.position.PositionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 作者:hfj
 * 功能:前台岗位接口，给求职用户展示已发布岗位
 * 日期:2026/6/6 15:20
 */
@Validated
@RestController
@RequestMapping("/front/job")
@RequiredArgsConstructor
public class JobController {

    private final JobPositionService jobPositionService;

    private final JobCompanyService jobCompanyService;

    private final JobInteractionService jobInteractionService;

    /**
     * 前台分页查询岗位。
     * P表示参数描述，前台永远只查询已发布岗位，草稿和下架岗位不会返回给用户。
     *
     * @param request 查询参数
     * @return 返回已发布岗位分页列表
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
                null,
                true
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
     * 前台查询岗位详情。
     * P表示参数描述，未发布或已删除岗位不返回，避免用户通过改 URL 看到后台草稿岗位。
     *
     * @param id 岗位ID
     * @return 返回岗位详情、公司详情和收藏状态
     */
    @GetMapping("/{id}")
    public Result<PositionDetailVO> detail(@PathVariable Long id) {
        JobPosition position = jobPositionService.getPositionRequired(id);
        if (position.getStatus() == null || position.getStatus() != 1) {
            return Result.build(null, ResultCodeEnum.DATA_ERROR);
        }
        JobCompany company = jobCompanyService.getById(position.getCompanyId());
        Long loginUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        boolean favorited = jobInteractionService.isFavorited(loginUserId, id);
        Long favoriteCount = jobInteractionService.countFavorites(id);
        return Result.build(PositionDetailVO.from(position, company, favorited, favoriteCount), ResultCodeEnum.SUCCESS);
    }

    /**
     * 收藏或取消收藏岗位。
     * P表示参数描述，该接口需要登录；未收藏时点击会收藏，已收藏时点击会取消收藏。
     *
     * @param id 岗位ID
     * @return 返回最新收藏状态
     */
    @PostMapping("/{id}/favorite")
    public Result<FavoriteStateVO> toggleFavorite(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        FavoriteStateVO favoriteState = jobInteractionService.toggleFavorite(userId, id);
        return Result.build(favoriteState, ResultCodeEnum.SUCCESS);
    }

    /**
     * 立即沟通。
     * P表示参数描述，用户点击后会给岗位所属公司的 HR 保存一条沟通消息。
     *
     * @param id 岗位ID
     * @param request 沟通消息参数，content 为空时后端自动生成默认消息
     * @return 返回已发送的消息
     */
    @PostMapping("/{id}/communicate")
    public Result<JobMessageVO> communicate(@PathVariable Long id,
                                            @Valid @RequestBody(required = false) JobCommunicationDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        String content = request == null ? null : request.getContent();
        JobPositionMessage message = jobInteractionService.communicate(userId, id, content);
        JobCompany company = jobCompanyService.getById(message.getCompanyId());
        String companyName = company == null ? null : company.getCompanyName();
        return Result.build(JobMessageVO.from(message, companyName), ResultCodeEnum.SUCCESS);
    }

    /**
     * 批量转换岗位 VO。
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
