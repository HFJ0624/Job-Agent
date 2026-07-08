package com.job.bootstrap.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.job.common.entity.greeting.JobGreetingRecord;
import com.job.common.vo.greeting.GreetingVO;

/**
 * HR 打招呼语生成服务接口。
 *
 * <p>核心职责：基于用户简历和岗位 JD，调用 AI 模型生成个性化 HR 打招呼语，并记录生成历史。</p>
 *
 * <p>所属业务模块：求职辅助 - 沟通助手</p>
 *
 * <p>主要调用链：
 * JobGreetingController / JobInteractionService -&gt; JobGreetingService -&gt; JobGreetingServiceImpl -&gt; AiModelGatewayService / JobResumeService / JobPositionService</p>
 */
public interface JobGreetingService extends IService<JobGreetingRecord> {

    /**
     * 根据用户简历和岗位生成 HR 打招呼语。
     *
     * @param userId 当前登录用户ID
     * @param resumeId 简历ID
     * @param jobId 岗位ID
     * @param style 语气风格
     * @return 打招呼语结果
     */
    GreetingVO generateGreeting(Long userId, Long resumeId, Long jobId, String style);
}
