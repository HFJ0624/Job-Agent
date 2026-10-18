package com.job.bootstrap.agent.compensation;

import org.springframework.stereotype.Component;

/**
 * 功能: 通知发送补偿占位（任务 G9）：通知已触达用户，不可自动撤回。
 */
@Component
public class NotificationSendCompensation extends IrreversibleExternalCompensation {

    @Override
    public String toolName() {
        return "NotificationConnectorTool.sendNotification";
    }
}
