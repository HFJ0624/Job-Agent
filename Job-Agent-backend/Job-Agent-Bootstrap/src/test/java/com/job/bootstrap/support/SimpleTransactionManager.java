package com.job.bootstrap.support;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 单测用极简事务管理器：只推进状态，不真正开启事务。
 *
 * 用途: AgentChatServiceImpl 需要 PlatformTransactionManager 来构造 TransactionTemplate，
 * 单测里不希望因此引入数据库；用它替代 mock，避免 getTransaction 返回 null。
 */
public class SimpleTransactionManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
        // 单测无真实事务
    }

    @Override
    public void rollback(TransactionStatus status) {
        // 单测无真实事务
    }
}
