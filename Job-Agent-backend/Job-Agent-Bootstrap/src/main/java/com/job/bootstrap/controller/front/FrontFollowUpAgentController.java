package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.FrontFollowUpAgentService;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.FrontFollowUpCenterVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端求职跟进 Agent 接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/follow-up-agent")
public class FrontFollowUpAgentController {

    private final FrontFollowUpAgentService frontFollowUpAgentService;

    /**
     * 查询求职跟进中心聚合数据。
     */
    @GetMapping("/center")
    public Result<FrontFollowUpCenterVO> center() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.build(frontFollowUpAgentService.getCenter(userId), ResultCodeEnum.SUCCESS);
    }
}
