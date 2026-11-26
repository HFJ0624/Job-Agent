package com.job.bootstrap.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SseStreamParser 单元测试（任务 G5）。
 *
 * 覆盖重点（流式解析的三个真实坑）:
 * 1. 跨块边界：一个 data 行被拆到两次 feed。
 * 2. 换行风格：\n 与 \r\n 混用。
 * 3. 噪声行：空行、注释、event:/id: 等非 data 字段忽略。
 * 4. flush：无换行结尾的最后一行。
 */
class SseStreamParserTest {

    private final SseStreamParser parser = new SseStreamParser();

    @Test
    void shouldParseSingleLineEvents() {
        List<String> events = parser.feed("data: {\"a\":1}\n");
        assertThat(events).containsExactly("{\"a\":1}");
    }

    @Test
    void shouldParseEventSplitAcrossChunks() {
        // 一个事件的 data 行被拆成两段喂入
        assertThat(parser.feed("data: {\"to")).isEmpty();
        List<String> events = parser.feed("ken\":\"hi\"}\n");
        assertThat(events).containsExactly("{\"token\":\"hi\"}");
    }

    @Test
    void shouldHandleCrlfAndMultipleEventsInOneChunk() {
        List<String> events = parser.feed("data: one\r\ndata: two\n");
        assertThat(events).containsExactly("one", "two");
    }

    @Test
    void shouldIgnoreNoiseLines() {
        List<String> events = parser.feed(": heartbeat\nevent: message\nid: 1\n\ndata: payload\n");
        assertThat(events).containsExactly("payload");
    }

    @Test
    void shouldStripDataPrefixWithoutSpace() {
        List<String> events = parser.feed("data:no-space\n");
        assertThat(events).containsExactly("no-space");
    }

    @Test
    void shouldFlushTrailingLineWithoutNewline() {
        assertThat(parser.feed("data: partial")).isEmpty();
        assertThat(parser.flush()).containsExactly("partial");
    }

    @Test
    void shouldReturnDoneMarkerAsEvent() {
        // [DONE] 由调用方识别，解析器只负责原样吐出
        List<String> events = parser.feed("data: [DONE]\n");
        assertThat(events).containsExactly("[DONE]");
    }

    @Test
    void shouldNotEmitAnythingForEmptyFeed() {
        assertThat(parser.feed("")).isEmpty();
        assertThat(parser.feed(null)).isEmpty();
        assertThat(parser.flush()).isEmpty();
    }
}
