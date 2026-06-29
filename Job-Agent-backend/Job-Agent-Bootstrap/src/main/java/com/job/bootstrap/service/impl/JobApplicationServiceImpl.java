package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.JobApplicationRecordMapper;
import com.job.bootstrap.service.ApplicationFollowUpAgentService;
import com.job.bootstrap.service.JobApplicationService;
import com.job.bootstrap.service.JobCompanyService;
import com.job.bootstrap.service.JobPositionService;
import com.job.common.dto.application.JobApplicationQueryDTO;
import com.job.common.dto.application.JobApplicationSaveDTO;
import com.job.common.dto.application.JobApplicationStatusUpdateDTO;
import com.job.common.entity.application.JobApplicationRecord;
import com.job.common.entity.position.JobPosition;
import com.job.common.vo.application.JobApplicationStatsVO;
import com.job.common.vo.application.JobApplicationVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 作者:hfj
 * 功能:求职投递记录服务实现
 */
@Service
@RequiredArgsConstructor
public class JobApplicationServiceImpl implements JobApplicationService {

    private final JobApplicationRecordMapper jobApplicationRecordMapper;
    private final JobPositionService jobPositionService;
    private final JobCompanyService jobCompanyService;
    private final ApplicationFollowUpAgentService applicationFollowUpAgentService;

    private static final String STATUS_INTERESTED = "INTERESTED";
    private static final String STATUS_COMMUNICATED = "COMMUNICATED";
    private static final String STATUS_APPLIED = "APPLIED";
    private static final String STATUS_INTERVIEWING = "INTERVIEWING";
    private static final String STATUS_OFFER = "OFFER";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CLOSED = "CLOSED";

    private static final String PRIORITY_NORMAL = "NORMAL";

    /**
     * 新增或更新求职记录。
     *
     * 说明:
     * 1. 同一个用户对同一个岗位只保留一条求职记录。
     * 2. 用户重复点击“加入求职进度”时，更新已有记录。
     */
    @Override
    public JobApplicationVO saveApplication(Long userId, JobApplicationSaveDTO dto) {
        JobPosition job = jobPositionService.getById(dto.getJobId());

        if (job == null) {
            throw new BizException("岗位不存在");
        }

        /*
         * 查询当前用户是否已经添加过该岗位。
         */
        JobApplicationRecord record = jobApplicationRecordMapper.selectOne(
                new LambdaQueryWrapper<JobApplicationRecord>()
                        .eq(JobApplicationRecord::getUserId, userId)
                        .eq(JobApplicationRecord::getJobId, dto.getJobId())
                        .last("limit 1")
        );

        boolean isNew = record == null;

        if (isNew) {
            record = new JobApplicationRecord();
            record.setUserId(userId);
            record.setJobId(dto.getJobId());

            /*
             * 创建时保存岗位快照，避免后续岗位被修改或删除后影响求职进度展示。
             */
            fillJobSnapshot(record, job);

            record.setStatus(STATUS_INTERESTED);
            record.setPriority(PRIORITY_NORMAL);
            record.setSource("平台岗位");
            record.setLastAction("加入求职进度");
            record.setIsDeleted(0);
        }

        /*
         * 允许用户更新这些字段。
         */
        record.setResumeId(dto.getResumeId());

        if (StringUtils.hasText(dto.getStatus())) {
            record.setStatus(normalizeStatus(dto.getStatus()));
        }

        if (StringUtils.hasText(dto.getPriority())) {
            record.setPriority(normalizePriority(dto.getPriority()));
        }

        record.setHrName(trimToNull(dto.getHrName()));
        record.setHrContact(trimToNull(dto.getHrContact()));
        record.setApplyTime(dto.getApplyTime());
        record.setInterviewTime(dto.getInterviewTime());
        record.setNextFollowTime(dto.getNextFollowTime());
        record.setNote(trimToNull(dto.getNote()));

        if (isNew) {
            jobApplicationRecordMapper.insert(record);
        } else {
            record.setLastAction("更新求职记录");
            jobApplicationRecordMapper.updateById(record);
        }

        return JobApplicationVO.from(record);
    }

    /**
     * 分页查询求职记录。
     */
    @Override
    public IPage<JobApplicationVO> pageApplications(Long userId, JobApplicationQueryDTO query) {
        long pageNum = query.getPageNum() == null || query.getPageNum() <= 0 ? 1 : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() <= 0 ? 10 : query.getPageSize();

        if (pageSize > 100) {
            pageSize = 100;
        }

        Page<JobApplicationRecord> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<JobApplicationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobApplicationRecord::getUserId, userId);

        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(JobApplicationRecord::getStatus, query.getStatus().trim());
        }

        if (StringUtils.hasText(query.getCity())) {
            wrapper.eq(JobApplicationRecord::getCity, query.getCity().trim());
        }

        if (StringUtils.hasText(query.getPriority())) {
            wrapper.eq(JobApplicationRecord::getPriority, query.getPriority().trim());
        }

        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w
                    .like(JobApplicationRecord::getJobTitle, keyword)
                    .or()
                    .like(JobApplicationRecord::getCompanyName, keyword)
                    .or()
                    .like(JobApplicationRecord::getNote, keyword)
            );
        }

        /*
         * 下次跟进时间越近越靠前，其次按更新时间排序。
         */
        wrapper.orderByAsc(JobApplicationRecord::getNextFollowTime)
                .orderByDesc(JobApplicationRecord::getUpdateTime);

        IPage<JobApplicationRecord> entityPage = jobApplicationRecordMapper.selectPage(page, wrapper);

        return entityPage.convert(JobApplicationVO::from);
    }

    /**
     * 修改求职状态。
     */
    @Override
    public JobApplicationVO updateStatus(Long userId, Long id, JobApplicationStatusUpdateDTO dto) {
        JobApplicationRecord record = getUserRecordRequired(userId, id);

        String newStatus = normalizeStatus(dto.getStatus());
        record.setStatus(newStatus);

        /*
         * 不同状态下自动补充一些时间字段。
         */
        if (STATUS_APPLIED.equals(newStatus) && record.getApplyTime() == null) {
            record.setApplyTime(new Date());
        }

        if (STATUS_INTERVIEWING.equals(newStatus)) {
            record.setInterviewTime(dto.getInterviewTime());
        }

        record.setNextFollowTime(dto.getNextFollowTime());

        if (StringUtils.hasText(dto.getNote())) {
            record.setNote(dto.getNote().trim());
        }

        record.setLastAction("状态更新为：" + newStatus);
        jobApplicationRecordMapper.updateById(record);

        /*
         * 状态进入面试中后，统一交给求职跟进 Agent 创建提醒、准备材料和邮件通知任务。
         */
        if (STATUS_INTERVIEWING.equals(newStatus)) {
            applicationFollowUpAgentService.onInterviewScheduled(record);
        }

        return JobApplicationVO.from(record);
    }

    /**
     * 删除求职记录。
     */
    @Override
    public void deleteApplication(Long userId, Long id) {
        JobApplicationRecord record = getUserRecordRequired(userId, id);

        /*
         * BaseEntity 使用 @TableLogic 时，deleteById 会执行逻辑删除。
         */
        jobApplicationRecordMapper.deleteById(record.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncInterviewProgress(
            Long userId,
            Long applicationId,
            Date interviewTime,
            Date nextFollowTime
    ) {
        if (applicationId == null) {
            return;
        }

        JobApplicationRecord application = jobApplicationRecordMapper.selectById(applicationId);

        if (application == null) {
            return;
        }

        if (!userId.equals(application.getUserId())) {
            throw new SecurityException("无权更新该求职进度");
        }

        /*
         * 如果已经有面试时间，说明进入面试阶段。
         */
        if (interviewTime != null) {
            application.setStatus("INTERVIEWING");
            application.setInterviewTime(interviewTime);
            application.setLastAction("确认面试邀约");
        }

        if (nextFollowTime != null) {
            application.setNextFollowTime(nextFollowTime);
        }

        jobApplicationRecordMapper.updateById(application);

        /*
         * 沟通记录同步出面试时间时，也走同一个自动跟进入口，避免只有手动更新状态才触发。
         */
        if (interviewTime != null) {
            applicationFollowUpAgentService.onInterviewScheduled(application);
        }
    }

    /**
     * 查询统计信息。
     */
    @Override
    public JobApplicationStatsVO getStats(Long userId) {
        List<JobApplicationRecord> list = jobApplicationRecordMapper.selectList(
                new LambdaQueryWrapper<JobApplicationRecord>()
                        .eq(JobApplicationRecord::getUserId, userId)
        );

        Map<String, Long> statusCountMap = new LinkedHashMap<>();

        /*
         * 初始化所有状态，保证前端即使数量为0也能展示。
         */
        for (String status : allStatuses()) {
            statusCountMap.put(status, 0L);
        }

        for (JobApplicationRecord record : list) {
            statusCountMap.computeIfPresent(record.getStatus(), (key, value) -> value + 1);
        }

        Date now = new Date();

        long todayFollowCount = list.stream()
                .filter(item -> item.getNextFollowTime() != null)
                .filter(item -> !item.getNextFollowTime().after(now))
                .count();

        long interviewingCount = list.stream()
                .filter(item -> STATUS_INTERVIEWING.equals(item.getStatus()))
                .count();

        JobApplicationStatsVO stats = new JobApplicationStatsVO();
        stats.setTotalCount((long) list.size());
        stats.setStatusCountMap(statusCountMap);
        stats.setTodayFollowCount(todayFollowCount);
        stats.setInterviewingCount(interviewingCount);
        return stats;
    }

    /**
     * 查询当前用户的一条求职记录。
     */
    private JobApplicationRecord getUserRecordRequired(Long userId, Long id) {
        JobApplicationRecord record = jobApplicationRecordMapper.selectById(id);

        if (record == null || !userId.equals(record.getUserId())) {
            throw new BizException("求职记录不存在或无权限访问");
        }

        return record;
    }

    /**
     * 保存岗位快照。
     */
    private void fillJobSnapshot(JobApplicationRecord record, JobPosition job) {
        String companyName = jobCompanyService.getCompanyRequired(job.getCompanyId()).getCompanyName();
        record.setCompanyId(job.getCompanyId());
        record.setCompanyName(companyName);
        record.setJobTitle(job.getJobTitle());
        record.setCity(job.getCity());
        record.setSalaryText(formatSalary(job));
    }

    /**
     * 格式化薪资。
     */
    private String formatSalary(JobPosition job) {
        Integer minSalary = job.getMinSalary();
        Integer maxSalary = job.getMaxSalary();

        if (minSalary == null && maxSalary == null) {
            return "薪资面议";
        }

        int min = minSalary == null ? 0 : minSalary / 1000;
        int max = maxSalary == null ? 0 : maxSalary / 1000;

        if (min > 0 && max > 0) {
            return min + "-" + max + "K";
        }

        if (min > 0) {
            return min + "K起";
        }

        return max + "K以内";
    }

    /**
     * 状态校验。
     */
    private String normalizeStatus(String status) {
        String value = status == null ? STATUS_INTERESTED : status.trim();

        if (!allStatuses().contains(value)) {
            throw new BizException("不支持的求职状态：" + value);
        }

        return value;
    }

    /**
     * 优先级校验。
     */
    private String normalizePriority(String priority) {
        String value = priority == null ? PRIORITY_NORMAL : priority.trim();

        if (!List.of("LOW", "NORMAL", "HIGH").contains(value)) {
            throw new BizException("不支持的优先级：" + value);
        }

        return value;
    }

    /**
     * 所有状态。
     */
    private List<String> allStatuses() {
        return List.of(
                STATUS_INTERESTED,
                STATUS_COMMUNICATED,
                STATUS_APPLIED,
                STATUS_INTERVIEWING,
                STATUS_OFFER,
                STATUS_REJECTED,
                STATUS_CLOSED
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
