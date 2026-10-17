package com.job.bootstrap.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能: SSE（Server-Sent Events）流式解析器（任务 G5）。
 *
 * 设计说明:
 * 1. 只负责“字节流 → data 事件负载”的纯逻辑解析：不碰网络、不碰 JSON，便于完整单测。
 * 2. 处理真实流式场景的三个坑：
 *    - 跨块边界：一个事件的 data 行可能被拆到两次 feed 里（内部缓冲拼接）；
 *    - 换行风格：同时兼容 \n 与 \r\n；
 *    - 噪声行：空行、注释行（: 开头）、event:/id: 等非 data 字段一律忽略。
 * 3. OpenAI 兼容接口的每个 data 行是一个完整 JSON（单行 data 规范）；
 *    多行 data 拼接（SSE 规范）不在本版本范围内，需要时再扩展。
 * 4. 使用方式：边读边 feed，流结束后调用 flush 吐出缓冲区残留（无换行结尾的最后一行）。
 */
public class SseStreamParser {

    private final StringBuilder buffer = new StringBuilder();

    /**
     * 喂入一段流式文本（可按任意边界切分），返回本次完整解析出的 data 事件负载。
     *
     * @param chunk 一段流式文本（已按字符集解码）
     * @return 完整事件的负载列表（不含 "data:" 前缀）
     */
    public List<String> feed(String chunk) {
        List<String> events = new ArrayList<>();
        if (chunk == null || chunk.isEmpty()) {
            return events;
        }
        buffer.append(chunk);

        int newlineIndex;
        while ((newlineIndex = buffer.indexOf("\n")) >= 0) {
            String line = buffer.substring(0, newlineIndex);
            buffer.delete(0, newlineIndex + 1);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            String payload = extractDataPayload(line);
            if (payload != null) {
                events.add(payload);
            }
        }
        return events;
    }

    /**
     * 流结束调用：把缓冲区里最后一条没有换行结尾的行按事件吐出。
     *
     * @return 残留事件的负载列表
     */
    public List<String> flush() {
        List<String> events = new ArrayList<>();
        if (buffer.length() > 0) {
            String line = buffer.toString();
            buffer.setLength(0);
            String payload = extractDataPayload(line);
            if (payload != null) {
                events.add(payload);
            }
        }
        return events;
    }

    // 只认 data 行；空行/注释/其他 SSE 字段返回 null
    private String extractDataPayload(String line) {
        if (line == null || line.isEmpty() || line.startsWith(":")) {
            return null;
        }
        if (!line.startsWith("data:")) {
            return null;
        }
        String payload = line.substring("data:".length());
        if (payload.startsWith(" ")) {
            payload = payload.substring(1);
        }
        return payload;
    }
}
