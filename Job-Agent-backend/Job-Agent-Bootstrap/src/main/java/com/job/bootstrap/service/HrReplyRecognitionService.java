package com.job.bootstrap.service;

import com.job.common.dto.communication.HrReplyRecognitionConfirmDTO;
import com.job.common.dto.communication.HrReplyRecognizeDTO;
import com.job.common.vo.communication.HrReplyRecognitionVO;

/**
 * HR 回复识别服务。
 */
public interface HrReplyRecognitionService {

    /**
     * 从沟通记录入口识别 HR 回复。
     */
    HrReplyRecognitionVO recognizeFromCommunication(Long userId, Long communicationId, HrReplyRecognizeDTO dto);

    /**
     * 从跟进中心入口识别 HR 回复。
     */
    HrReplyRecognitionVO recognizeFromApplication(Long userId, Long applicationId, HrReplyRecognizeDTO dto);

    /**
     * 用户确认识别结果，并执行选中的业务动作。
     */
    HrReplyRecognitionVO confirm(Long userId, Long recognitionId, HrReplyRecognitionConfirmDTO dto);

    /**
     * 用户取消本次识别结果。
     */
    HrReplyRecognitionVO cancel(Long userId, Long recognitionId);
}
