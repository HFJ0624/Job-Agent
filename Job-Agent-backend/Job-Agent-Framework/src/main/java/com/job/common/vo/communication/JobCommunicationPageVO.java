package com.job.common.vo.communication;

import lombok.Data;

import java.util.List;

/**
 * 作者: hfj
 * 功能: 沟通记录分页 VO
 */
@Data
public class JobCommunicationPageVO {

    private List<JobCommunicationRecordVO> records;

    private Long total;

    private Long pageNo;

    private Long pageSize;
}
