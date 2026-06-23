package com.job.bootstrap.agent.memory;

import com.job.enums.AgentMemoryActionType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 作者: hfj
 * 功能: 长期记忆动作分类器
 * 日期: 2026/6/23
 *
 * 说明:
 * 1. 该类解决“什么时候不该记”的问题。
 * 2. 抽取器只负责从文本里找候选事实，本类先判断这句话是不是在操作长期记忆。
 * 3. 第一版使用高确定性规则，不调用模型，避免每句话都增加成本和延迟。
 * 4. 后续可以在本类后面接 MEMORY_ACTION_CLASSIFY 模型场景，但仍然保留规则兜底。
 */
@Component
public class AgentMemoryActionClassifier {

    private static final List<String> DELETE_WORDS = List.of(
            "不要记住", "别记住", "不用记住", "不要保存", "别保存", "忘掉", "忘记", "清除", "删除"
    );

    private static final List<String> ASK_WORDS = List.of(
            "你还记得", "还记得", "记得我", "我之前说过", "我刚才是不是", "你知道我叫",
            "我叫什么", "我叫啥", "你叫什么名字", "你现在叫什么"
    );

    private static final List<String> UPDATE_WORDS = List.of(
            "改成", "改为", "换成", "以后不要叫", "以后别叫", "不要再叫", "别再叫"
    );

    private static final List<String> SET_WORDS = List.of(
            "请记住", "记住", "以后", "之后", "以后都", "你可以叫我", "以后叫我",
            "我叫", "我的名字叫", "偏好", "喜欢", "不喜欢", "我想找", "想找",
            "目标", "期望", "希望薪资", "期望薪资", "最低薪资", "薪资不低于",
            "回答风格", "回复风格", "说话风格", "不考虑", "不接受", "不要推荐", "排除"
    );

    /**
     * 判断用户输入对应的长期记忆动作。
     *
     * 方法步骤:
     * 1. 空文本直接判定为普通聊天。
     * 2. 删除类表达优先处理，因为用户的“别记住”应该立即阻断后续写入。
     * 3. 询问类表达第二优先，避免“你还记得我叫什么吗”被误写成 user_name。
     * 4. 修改类表达第三优先，后续仍会走抽取器，并覆盖同 memoryKey 的旧值。
     * 5. 设置类表达最后判断，只有带明确长期偏好信号时才允许写入。
     *
     * @param message 用户原始输入或已脱敏输入
     * @return 动作判断结果
     */
    public AgentMemoryActionDecision classify(String message) {
        if (!StringUtils.hasText(message)) {
            return AgentMemoryActionDecision.normal("空文本不进入长期记忆流程");
        }

        String text = message.trim();
        if (containsAny(text, DELETE_WORDS)) {
            return AgentMemoryActionDecision.builder()
                    .actionType(AgentMemoryActionType.DELETE_MEMORY)
                    .targetMemoryKeys(resolveDeleteTargetKeys(text))
                    .reason("命中删除或忘记类表达")
                    .build();
        }

        if (looksLikeMemoryQuestion(text)) {
            return AgentMemoryActionDecision.builder()
                    .actionType(AgentMemoryActionType.ASK_MEMORY)
                    .targetMemoryKeys(List.of())
                    .reason("命中询问已有记忆的表达")
                    .build();
        }

        if (containsAny(text, UPDATE_WORDS)) {
            return AgentMemoryActionDecision.builder()
                    .actionType(AgentMemoryActionType.UPDATE_MEMORY)
                    .targetMemoryKeys(List.of())
                    .reason("命中修改长期记忆的表达")
                    .build();
        }

        if (containsAny(text, SET_WORDS)) {
            return AgentMemoryActionDecision.builder()
                    .actionType(AgentMemoryActionType.SET_MEMORY)
                    .targetMemoryKeys(List.of())
                    .reason("命中设置长期记忆的表达")
                    .build();
        }

        return AgentMemoryActionDecision.normal("未命中长期记忆动作表达");
    }

    private boolean looksLikeMemoryQuestion(String text) {
        if (containsAny(text, ASK_WORDS)) {
            return true;
        }

        /*
         * 问号 + 记忆关键词组合，才认为是询问记忆。
         * 单纯的“吗”太宽泛，容易把普通求职问题误判成 ASK_MEMORY。
         */
        return containsAny(text, List.of("?", "？", "吗"))
                && containsAny(text, List.of("记得", "记住", "名字", "叫什么", "之前说"));
    }

    private List<String> resolveDeleteTargetKeys(String text) {
        Set<String> keys = new LinkedHashSet<>();

        /*
         * 删除动作必须尽量限定到具体槽位。
         * 如果识别不到目标 key，就返回空列表，由上层选择“不做破坏性归档”。
         */
        addIfMatched(keys, text, List.of("我的名字", "我叫什么", "叫我", "我叫"), "user_name");
        addIfMatched(keys, text, List.of("你叫什么", "助手名字", "助手名称", "你叫"), "assistant_nickname");
        addIfMatched(keys, text, List.of("城市", "地点", "北京", "上海", "深圳", "杭州", "远程"), "preferred_city");
        addIfMatched(keys, text, List.of("岗位", "职位", "方向", "Java", "后端", "前端", "AI"), "target_role");
        addIfMatched(keys, text, List.of("薪资", "工资", "多少钱", "最低"), "min_salary");
        addIfMatched(keys, text, List.of("回答风格", "回复风格", "说话风格", "表达风格"), "answer_style");
        addIfMatched(keys, text, List.of("外包"), "excluded_outsourcing");
        addIfMatched(keys, text, List.of("996"), "excluded_996");
        addIfMatched(keys, text, List.of("大小周"), "excluded_big_small_week");

        if (CollectionUtils.isEmpty(keys)) {
            return List.of();
        }
        return new ArrayList<>(keys);
    }

    private void addIfMatched(Set<String> keys, String text, List<String> words, String memoryKey) {
        if (containsAny(text, words)) {
            keys.add(memoryKey);
        }
    }

    private boolean containsAny(String text, List<String> words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
