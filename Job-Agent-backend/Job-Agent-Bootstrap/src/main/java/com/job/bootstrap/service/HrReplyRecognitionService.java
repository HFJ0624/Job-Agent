package com.job.bootstrap.service;

import com.job.common.dto.communication.HrReplyRecognitionConfirmDTO;
import com.job.common.dto.communication.HrReplyRecognizeDTO;
import com.job.common.vo.communication.HrReplyRecognitionVO;

/**
 * HR 回复识别服务。
 *
 * <p>核心职责：基于 AI 能力识别 HR 在邮件或沟通中的回复意图（如约面试、拒信、索要材料等），并为用户提供一键确认执行业务动作的能力。</p>
 *
 * <p>所属业务模块：求职沟通 / HR 回复智能解析</p>
 *
 * <p>主要调用链：Front Controller → HrReplyRecognitionService → LLM 识别引擎 / 沟通记录 Service / 求职申请 Service</p>
 */
public interface HrReplyRecognitionService {

    /**
     * 从沟通记录入口识别 HR 回复意图。
     *
     * @param userId          当前用户 ID
     * @param communicationId 沟通记录 ID
     * @param dto             识别参数（包含需要识别的消息范围、上下文等）
     * @return HR 回复识别结果，包含意图分类、置信度、建议动作列表
     */
    HrReplyRecognitionVO recognizeFromCommunication(Long userId, Long communicationId, HrReplyRecognizeDTO dto);

    /**
     * 从求职跟进中心入口识别 HR 回复意图。
     *
     * @param userId        当前用户 ID
     * @param applicationId 求职申请记录 ID
     * @param dto           识别参数
     * @return HR 回复识别结果
     */
    HrReplyRecognitionVO recognizeFromApplication(Long userId, Long applicationId, HrReplyRecognizeDTO dto);

    /**
     * 用户确认 HR 回复识别结果，并执行选中的业务动作。
     *
     * @param userId        当前用户 ID
     * @param recognitionId 识别记录 ID
     * @param dto           确认参数（包含用户选择的动作、补充信息等）
     * @return 执行后的识别结果状态
     */
    HrReplyRecognitionVO confirm(Long userId, Long recognitionId, HrReplyRecognitionConfirmDTO dto);

    /**
     * 用户取消本次 HR 回复识别结果。
     *
     * @param userId        当前用户 ID
     * @param recognitionId 识别记录 ID
     * @return 取消后的识别结果状态
     */
    HrReplyRecognitionVO cancel(Long userId, Long recognitionId);
}
