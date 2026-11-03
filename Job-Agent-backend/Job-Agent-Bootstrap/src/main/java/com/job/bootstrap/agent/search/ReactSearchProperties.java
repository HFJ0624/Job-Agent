package com.job.bootstrap.agent.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 功能: 第一阶段 ReAct 岗位搜索开关（默认关闭）。
 *
 * 说明: 关闭走原模板链路；开启后仅岗位搜索场景走新 Workflow。
 * 已有任务按所属 run 路由，不因开关变化中途切换引擎。
 */
@ConfigurationProperties(prefix = "job.agent.react-search")
public class ReactSearchProperties {

    /** 是否启用 ReAct 岗位搜索链路。 */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
