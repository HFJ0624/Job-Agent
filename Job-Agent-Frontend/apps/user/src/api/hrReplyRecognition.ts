import { request } from "./request";
import type {
  HrReplyRecognitionConfirmPayload,
  HrReplyRecognitionInfo,
  HrReplyRecognizePayload
} from "./types";

/**
 * 从沟通记录入口识别 HR 回复。
 */
export function recognizeHrReplyFromCommunication(
  communicationId: number | string,
  payload: HrReplyRecognizePayload
) {
  return request<HrReplyRecognitionInfo>(
    `/front/hr-reply-recognitions/communications/${communicationId}/recognize`,
    {
      method: "POST",
      body: JSON.stringify(payload)
    }
  );
}

/**
 * 从求职跟进入口识别 HR 回复。
 */
export function recognizeHrReplyFromApplication(
  applicationId: number | string,
  payload: HrReplyRecognizePayload
) {
  return request<HrReplyRecognitionInfo>(
    `/front/hr-reply-recognitions/applications/${applicationId}/recognize`,
    {
      method: "POST",
      body: JSON.stringify(payload)
    }
  );
}

/**
 * 用户确认 AI 识别结果，并执行选中的动作。
 */
export function confirmHrReplyRecognition(
  recognitionId: number | string,
  payload: HrReplyRecognitionConfirmPayload
) {
  return request<HrReplyRecognitionInfo>(`/front/hr-reply-recognitions/${recognitionId}/confirm`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

/**
 * 用户取消本次识别结果。
 */
export function cancelHrReplyRecognition(recognitionId: number | string) {
  return request<HrReplyRecognitionInfo>(`/front/hr-reply-recognitions/${recognitionId}/cancel`, {
    method: "POST"
  });
}
