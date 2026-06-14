package com.job.bootstrap.rag.utils;

import com.job.bootstrap.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 作者:hfj
 * 功能:RAG 文本切片工具
 * 日期:2026/6/14
 */
@Component
@RequiredArgsConstructor
public class RagTextSplitter {

    private final RagProperties ragProperties;

    /**
     * 将长文本切成固定大小且带重叠的分片。
     *
     * @param text 原始文本
     * @return 文本分片列表
     */
    public List<String> split(String text) {
        String normalizedText = normalize(text);
        if (!StringUtils.hasText(normalizedText)) {
            return List.of();
        }

        int chunkSize = Math.max(100, ragProperties.getRetrieval().getChunk().getSize());
        int overlap = Math.max(0, ragProperties.getRetrieval().getChunk().getOverlap());
        overlap = Math.min(overlap, chunkSize - 1);

        /*
         * 1. step 表示每次向前滑动的距离。
         * 2. chunkSize=500、overlap=80 时，step=420。
         * 3. 这样相邻分片之间会共享 80 个字符，减少语义被切断的概率。
         */
        int step = chunkSize - overlap;
        List<String> chunks = new ArrayList<>();

        for (int start = 0; start < normalizedText.length(); start += step) {
            int end = Math.min(start + chunkSize, normalizedText.length());
            String chunk = normalizedText.substring(start, end).trim();
            if (StringUtils.hasText(chunk)) {
                chunks.add(chunk);
            }
            if (end >= normalizedText.length()) {
                break;
            }
        }

        return chunks;
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        /*
         * 1. 将连续空白压缩成一个空格，降低 embedding 中无意义 token 的比例。
         * 2. 保留中文标点和英文标点，方便模型理解句子边界。
         */
        return text.replaceAll("\\s+", " ").trim();
    }
}
