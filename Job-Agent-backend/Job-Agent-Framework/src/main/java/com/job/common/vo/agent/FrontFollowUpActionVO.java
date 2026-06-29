package com.job.common.vo.agent;

import lombok.Data;

/**
 * 用户端求职跟进 Agent 建议动作。
 */
@Data
public class FrontFollowUpActionVO {

    /**
     * 动作编码，用于前端判断应该跳转到哪个功能。
     */
    private String actionCode;

    /**
     * 动作标题。
     */
    private String title;

    /**
     * 动作说明。
     */
    private String description;

    /**
     * 动作按钮文案。
     */
    private String buttonText;

    /**
     * 动作优先级：HIGH / NORMAL / LOW。
     */
    private String priority;

    /**
     * 前端可直接跳转的路径。
     */
    private String targetPath;

    public static FrontFollowUpActionVO of(
            String actionCode,
            String title,
            String description,
            String buttonText,
            String priority,
            String targetPath
    ) {
        FrontFollowUpActionVO vo = new FrontFollowUpActionVO();
        vo.setActionCode(actionCode);
        vo.setTitle(title);
        vo.setDescription(description);
        vo.setButtonText(buttonText);
        vo.setPriority(priority);
        vo.setTargetPath(targetPath);
        return vo;
    }
}
