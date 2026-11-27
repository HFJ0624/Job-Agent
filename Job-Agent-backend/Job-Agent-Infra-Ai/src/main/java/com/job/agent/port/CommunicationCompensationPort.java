package com.job.agent.port;

/**
 * 功能: 沟通记录补偿端口（Infra-Ai 定义，Bootstrap 实现）。
 *
 * <p>补偿动作只需要两件事：按话术记录找到最新沟通记录（含状态），以及关闭它。
 * 沟通记录的建表、状态机与权限仍在 Bootstrap 的求职沟通业务内。</p>
 */
public interface CommunicationCompensationPort {

    /** 沟通记录快照：补偿判断只需 ID 与状态。 */
    record Snapshot(Long id, String status) {
    }

    /** 按用户 + 话术记录查最新一条未删除的沟通记录；不存在返回 null。 */
    Snapshot findLatestByGreeting(Long userId, Long greetingId);

    /** 关闭沟通记录（沿用原业务语义）。 */
    void closeCommunication(Long userId, Long recordId);
}
