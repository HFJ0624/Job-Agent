package com.job.bootstrap.service;

/**
 * 功能: 语音识别服务抽象。
 *
 * 说明:
 * 1. 面试业务只关心“音频字节转文本”，不绑定具体供应商。
 * 2. 第一版使用火山引擎 ASR，后续可以增加阿里云、腾讯云或本地模型实现。
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
