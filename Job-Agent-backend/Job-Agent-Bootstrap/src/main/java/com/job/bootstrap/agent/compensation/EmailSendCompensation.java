package com.job.bootstrap.agent.compensation;

import org.springframework.stereotype.Component;

/**
 * 功能: 邮件发送补偿占位（任务 G9）：邮件已发出，不可自动撤回。
 */
@Component
public class EmailSendCompensation extends IrreversibleExternalCompensation {

    @Override
    public String toolName() {
        return "EmailConnectorTool.sendEmail";
    }
}
