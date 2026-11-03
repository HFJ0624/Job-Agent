package com.job.bootstrap.agent.resume;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.entity.resume.JobResume;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 功能: 简历分段（第二阶段 ResumeAgent 的读取单元）。
 *
 * 设计说明:
 * 1. 优先用 parsedJson 的结构（对象字段/数组元素）生成带标题的分段；
 * 2. 没有结构时按空行/编号切分原文，合并过短片段，限制总段数与单段长度；
 * 3. sectionId 稳定（S1..Sn）——Agent 只能用 read_resume_section 读取合法 sectionId，
 *    不能凭空引用不存在的段落。
 */
public final class ResumeSections {

    /** 单段最大字符数（读取时截断，避免一次塞满上下文）。 */
    public static final int MAX_SECTION_CHARS = 1200;
    /** 最大分段数。 */
    public static final int MAX_SECTIONS = 12;

    /**
     * @param sectionId 段标识（S1..Sn）
     * @param title     段标题
     * @param text      段正文
     */
    public record Section(String sectionId, String title, String text) {
    }

    private ResumeSections() {
    }

    public static List<Section> of(JobResume resume, ObjectMapper objectMapper) {
        if (resume == null) {
            return List.of();
        }
        List<Section> fromJson = fromParsedJson(resume.getParsedJson(), objectMapper);
        if (!fromJson.isEmpty()) {
            return fromJson;
        }
        return fromRawText(resume.getRawText());
    }

    // 结构化字段 → 分段
    private static List<Section> fromParsedJson(String parsedJson, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(parsedJson)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(parsedJson);
            List<Section> sections = new ArrayList<>();
            if (root.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext() && sections.size() < MAX_SECTIONS) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String text = entry.getValue().isTextual()
                            ? entry.getValue().asText()
                            : entry.getValue().toString();
                    if (StringUtils.hasText(text)) {
                        sections.add(new Section("S" + (sections.size() + 1), entry.getKey(), clip(text)));
                    }
                }
            } else if (root.isArray()) {
                for (JsonNode item : root) {
                    if (sections.size() >= MAX_SECTIONS) {
                        break;
                    }
                    String text = item.isTextual() ? item.asText() : item.toString();
                    if (StringUtils.hasText(text)) {
                        sections.add(new Section("S" + (sections.size() + 1), "条目 " + (sections.size() + 1), clip(text)));
                    }
                }
            }
            return sections;
        } catch (Exception exception) {
            return List.of();
        }
    }

    // 原文 → 分段（空行/编号切分，合并短片段）
    private static List<Section> fromRawText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return List.of();
        }
        String[] blocks = rawText.split("\\R\\s*\\R");
        List<Section> sections = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String title = null;

        for (String block : blocks) {
            String text = block.trim();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (title == null) {
                title = firstLine(text);
            }
            buffer.append(text).append("\n\n");
            // 累积到一定长度或遇到明显的段落边界就切分
            if (buffer.length() >= 400) {
                sections.add(new Section("S" + (sections.size() + 1), title, clip(buffer.toString())));
                buffer.setLength(0);
                title = null;
                if (sections.size() >= MAX_SECTIONS) {
                    break;
                }
            }
        }
        if (buffer.length() > 0 && sections.size() < MAX_SECTIONS) {
            sections.add(new Section("S" + (sections.size() + 1),
                    title == null ? "片段 " + (sections.size() + 1) : title, clip(buffer.toString())));
        }
        return sections;
    }

    public static Section find(List<Section> sections, String sectionId) {
        if (sections == null || !StringUtils.hasText(sectionId)) {
            return null;
        }
        return sections.stream().filter(s -> s.sectionId().equalsIgnoreCase(sectionId.trim())).findFirst().orElse(null);
    }

    private static String firstLine(String text) {
        String first = text.split("\\R")[0].trim();
        return first.length() > 24 ? first.substring(0, 24) : first;
    }

    private static String clip(String text) {
        String trimmed = text.trim();
        return trimmed.length() > MAX_SECTION_CHARS ? trimmed.substring(0, MAX_SECTION_CHARS) + "…" : trimmed;
    }
}
