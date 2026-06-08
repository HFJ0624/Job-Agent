package com.job.bootstrap.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.job.common.entity.match.JobMatchRecord;
import com.job.common.vo.match.JobMatchVO;

/**
 * 作者:hfj
 * 功能:岗位匹配服务接口
 */
public interface JobMatchService extends IService<JobMatchRecord> {

    /**
     * 根据用户简历和岗位进行匹配分析。
     *
     * @param userId 当前登录用户ID
     * @param resumeId 简历ID
     * @param jobId 岗位ID
     * @return 匹配分析结果
     */
    JobMatchVO matchJob(Long userId, Long resumeId, Long jobId);

    /**
     * 查询最近一次匹配记录。
     *
     * @param userId 当前登录用户ID
     * @param resumeId 简历ID
     * @param jobId 岗位ID
     * @return 最近一次匹配结果，没有则返回 null
     */
    JobMatchVO getLatestMatch(Long userId, Long resumeId, Long jobId);
}
