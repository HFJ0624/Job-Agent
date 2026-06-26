package com.job.bootstrap.controller.admin;

import com.job.bootstrap.service.AdminMockInterviewService;
import com.job.common.dto.interview.MockInterviewSessionQueryDTO;
import com.job.common.entity.base.PageResult;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.interview.MockInterviewMediaRecordVO;
import com.job.common.vo.interview.MockInterviewSessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 功能: 后台模拟面试记录管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/mock-interviews")
public class AdminMockInterviewController {

    private final AdminMockInterviewService adminMockInterviewService;

    /**
     * 分页查询用户模拟面试记录。
     */
    @GetMapping("/sessions/page")
    public Result<PageResult<MockInterviewSessionVO>> pageSessions(MockInterviewSessionQueryDTO query) {
        return Result.build(adminMockInterviewService.pageSessions(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询单场模拟面试详情。
     */
    @GetMapping("/sessions/{sessionId}")
    public Result<MockInterviewSessionVO> detail(@PathVariable Long sessionId) {
        return Result.build(adminMockInterviewService.getDetail(sessionId), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询单场模拟面试音频/视频记录。
     */
    @GetMapping("/sessions/{sessionId}/media")
    public Result<List<MockInterviewMediaRecordVO>> mediaRecords(@PathVariable Long sessionId) {
        return Result.build(adminMockInterviewService.listMediaRecords(sessionId), ResultCodeEnum.SUCCESS);
    }
}
