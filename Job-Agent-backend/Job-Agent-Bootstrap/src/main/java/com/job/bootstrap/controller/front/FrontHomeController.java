package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.FrontHomeService;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.home.HomeOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作者:hfj
 * 功能:用户端首页接口，给前端首页提供真实数据库数据
 * 日期:2026/6/24
 */
@RestController
@RequestMapping("/front/home")
@RequiredArgsConstructor
public class FrontHomeController {

    private final FrontHomeService frontHomeService;

    /**
     * 查询首页聚合数据。
     *
     * @return 推荐岗位、热门公司、简历报告和 AI 建议
     */
    @GetMapping("/overview")
    public Result<HomeOverviewVO> overview() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.build(frontHomeService.getOverview(userId), ResultCodeEnum.SUCCESS);
    }
}
