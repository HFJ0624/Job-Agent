package com.job.bootstrap.integration.agent.communication;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.agent.port.CommunicationCompensationPort;
import com.job.bootstrap.infrastructure.persistence.communication.mapper.JobCommunicationRecordMapper;
import com.job.bootstrap.application.communication.service.JobCommunicationRecordService;
import com.job.common.entity.communication.JobCommunicationRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 功能: 沟通补偿端口的宿主实现，复用原 Mapper 查询与关闭逻辑。 */
@Component
@RequiredArgsConstructor
public class CommunicationCompensationAdapter implements CommunicationCompensationPort {

    private final JobCommunicationRecordMapper jobCommunicationRecordMapper;
    private final JobCommunicationRecordService jobCommunicationRecordService;

    @Override
    public Snapshot findLatestByGreeting(Long userId, Long greetingId) {
        JobCommunicationRecord record = jobCommunicationRecordMapper.selectOne(
                new LambdaQueryWrapper<JobCommunicationRecord>()
                        .eq(JobCommunicationRecord::getUserId, userId)
                        .eq(JobCommunicationRecord::getGreetingRecordId, greetingId)
                        .eq(JobCommunicationRecord::getIsDeleted, 0)
                        .orderByDesc(JobCommunicationRecord::getId)
                        .last("limit 1"));
        return record == null ? null : new Snapshot(record.getId(), record.getCommunicationStatus());
    }

    @Override
    public void closeCommunication(Long userId, Long recordId) {
        jobCommunicationRecordService.closeCommunication(userId, recordId);
    }
}
