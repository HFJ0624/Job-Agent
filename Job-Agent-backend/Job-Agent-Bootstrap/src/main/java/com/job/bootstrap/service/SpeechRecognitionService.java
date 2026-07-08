package com.job.bootstrap.service;

/**
 * 语音识别服务接口。
 *
 * <p>核心职责：为面试模拟、语音输入等场景提供音频到文本的转换能力，屏蔽底层 ASR 供应商差异。</p>
 *
 * <p>所属业务模块：面试辅助 - 语音交互</p>
 *
 * <p>主要调用链：
 * InterviewController / AgentChatController -&gt; SpeechRecognitionService -&gt; VolcanoAsrServiceImpl / 其他 ASR 实现</p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>上层业务只关心“音频字节转文本”，不绑定具体供应商。</li>
 *   <li>第一版使用火山引擎 ASR，后续可扩展阿里云、腾讯云或本地模型实现。</li>
 * </ol>
 * </p>
 */
public interface SpeechRecognitionService {

    /**
     * 识别一段音频。
     *
     * @param audioBytes 音频字节
     * @param contentType 音频 MIME 类型
     * @param originalFilename 原始文件名
     * @return 识别结果
     */
    SpeechRecognitionResult recognize(byte[] audioBytes, String contentType, String originalFilename);

    /**
     * 识别结果。
     */
    record SpeechRecognitionResult(
            boolean success,
            String text,
            String provider,
            String errorMessage
    ) {
        public static SpeechRecognitionResult success(String provider, String text) {
            return new SpeechRecognitionResult(true, text, provider, null);
        }

        public static SpeechRecognitionResult failed(String provider, String errorMessage) {
            return new SpeechRecognitionResult(false, null, provider, errorMessage);
        }
    }
}
