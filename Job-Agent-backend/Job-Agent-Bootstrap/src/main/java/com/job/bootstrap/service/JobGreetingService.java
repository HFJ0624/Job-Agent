package com.job.bootstrap.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.job.common.entity.greeting.JobGreetingRecord;
import com.job.common.vo.greeting.GreetingVO;

/**
 * 作者:hfj
 * 功能:HR 打招呼语生成服务
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
