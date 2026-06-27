package com.job.bootstrap.interview;

import com.job.bootstrap.interview.model.InterviewQuestionImportItem;
import com.job.bootstrap.interview.parser.InterviewQuestionMarkdownParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewQuestionMarkdownParserTest {

    @Test
    void shouldParseHeadingQuestionAndAnswerBlocks() {
        InterviewQuestionMarkdownParser parser = new InterviewQuestionMarkdownParser();
        String markdown = """
                # Java 面试题

                ## Spring Bean 生命周期是什么？

                Spring Bean 生命周期包括实例化、属性填充、Aware 回调、初始化、销毁等阶段。

                ## Redis 缓存穿透怎么解决？

                可以使用布隆过滤器、缓存空值、参数校验等方式处理缓存穿透。
                """;

        List<InterviewQuestionImportItem> items = parser.parse("demo.md", markdown);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getQuestionTitle()).isEqualTo("Spring Bean 生命周期是什么？");
        assertThat(items.get(0).getStandardAnswer()).contains("实例化");
        assertThat(items.get(1).getQuestionTitle()).isEqualTo("Redis 缓存穿透怎么解决？");
        assertThat(items.get(1).getCategory()).isEqualTo("Java 面试题");
    }
}
