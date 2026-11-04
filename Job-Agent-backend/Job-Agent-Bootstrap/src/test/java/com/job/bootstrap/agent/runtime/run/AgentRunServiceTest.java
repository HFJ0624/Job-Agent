package com.job.bootstrap.agent.runtime.run;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.job.bootstrap.mapper.AgentRunMapper;
import com.job.common.entity.agent.AgentRun;
import com.job.enums.AgentRunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentRunService 单元测试（第一阶段）。
 *
 * 覆盖重点（文档测试清单 7/8）:
 * 1. 原子领取：影响行数 0 → 领取失败（重复请求不能同时进入同一个 run）；
 * 2. 持有权校验：旧 attempt 写回被拒绝；
 * 3. 超过 1000 字符的消息序列完整保存（不做摘要截断）。
 */
class AgentRunServiceTest {

    private final AgentRunMapper mapper = mock(AgentRunMapper.class);

    private final PlatformTransactionManager transactionManager = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    };

    private AgentRunService service;

    @BeforeEach
    void setUp() {
        service = new AgentRunService(mapper, transactionManager);
    }

    @Test
    void claimShouldFailWhenRowsZero() {
        when(mapper.update(any(), any())).thenReturn(0);

        assertThat(service.claim(5L, 0)).isFalse();
    }

    @Test
    void claimShouldSucceedWhenRowsOne() {
        when(mapper.update(any(), any())).thenReturn(1);

        assertThat(service.claim(5L, 0)).isTrue();
    }

    @Test
    void staleAttemptWritebackShouldBeRejected() {
        when(mapper.update(any(), any())).thenReturn(0);

        boolean finished = service.finish(5L, 1, AgentRunStatus.SUCCEEDED, "{}", "[]", null);

        assertThat(finished).isFalse();
    }

    @Test
    void longMessagesShouldBeSavedInFull() {
        when(mapper.update(any(), any())).thenReturn(1);
        String longMessages = "{\"messages\":\"" + "长消息".repeat(3000) + "\"}";
        assertThat(longMessages.length()).isGreaterThan(1000);

        service.saveMessages(5L, 1, longMessages);

        ArgumentCaptor<UpdateWrapper<AgentRun>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        // 完整内容进参数（没有摘要截断）
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .anySatisfy(value -> assertThat(String.valueOf(value).length()).isEqualTo(longMessages.length()));
    }

    @Test
    void createShouldPersistPendingRun() {
        when(mapper.insert(any(AgentRun.class))).thenReturn(1);

        AgentRun run = service.create(100L, 10L, null, "trace", "job-search", "找岗位", "{}");

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.PENDING.name());
        assertThat(run.getAttempt()).isZero();
        verify(mapper).insert(any(AgentRun.class));
    }
}
