package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.ai.AiModelCallLogQueryDTO;
import com.job.common.dto.ai.AiModelConfigQueryDTO;
import com.job.common.dto.ai.AiModelConfigSaveDTO;
import com.job.common.dto.ai.AiModelRouteQueryDTO;
import com.job.common.dto.ai.AiModelRouteSaveDTO;
import com.job.common.vo.ai.AiModelCallLogVO;
import com.job.common.vo.ai.AiModelConfigVO;
import com.job.common.vo.ai.AiModelCostStatsVO;
import com.job.common.vo.ai.AiModelRouteVO;

import java.util.List;

/**
 * 后台 AI 模型与路由管理服务接口。
 *
 * <p>核心职责：为运维人员提供 AI 模型配置、路由规则、调用日志查询及成本统计等后台管理能力。</p>
 *
 * <p>所属业务模块：后台管理 - AI 基础设施</p>
 *
 * <p>主要调用链：
 * AdminAiModelController -&gt; AdminAiModelService -&gt; AdminAiModelServiceImpl -&gt; AiModelConfigRepository / AiModelRouteRepository / AiModelCallLogRepository</p>
 */
public interface AdminAiModelService {

    /**
     * 分页查询 AI 模型配置。
     *
     * @param query 查询条件
     * @return 模型配置分页
     */
    IPage<AiModelConfigVO> pageModels(AiModelConfigQueryDTO query);

    /**
     * 查询所有生效中的 AI 模型。
     *
     * @return 生效模型列表
     */
    List<AiModelConfigVO> listActiveModels();

    /**
     * 创建 AI 模型配置。
     *
     * @param request 模型配置保存参数
     * @return 创建后的模型配置
     */
    AiModelConfigVO createModel(AiModelConfigSaveDTO request);

    /**
     * 更新 AI 模型配置。
     *
     * @param id 模型配置 ID
     * @param request 模型配置保存参数
     * @return 更新后的模型配置
     */
    AiModelConfigVO updateModel(Long id, AiModelConfigSaveDTO request);

    /**
     * 删除 AI 模型配置。
     *
     * @param id 模型配置 ID
     */
    void deleteModel(Long id);

    /**
     * 分页查询模型路由规则。
     *
     * @param query 查询条件
     * @return 路由规则分页
     */
    IPage<AiModelRouteVO> pageRoutes(AiModelRouteQueryDTO query);

    /**
     * 创建模型路由规则。
     *
     * @param request 路由规则保存参数
     * @return 创建后的路由规则
     */
    AiModelRouteVO createRoute(AiModelRouteSaveDTO request);

    /**
     * 更新模型路由规则。
     *
     * @param id 路由规则 ID
     * @param request 路由规则保存参数
     * @return 更新后的路由规则
     */
    AiModelRouteVO updateRoute(Long id, AiModelRouteSaveDTO request);

    /**
     * 删除模型路由规则。
     *
     * @param id 路由规则 ID
     */
    void deleteRoute(Long id);

    /**
     * 分页查询模型调用日志。
     *
     * @param query 查询条件
     * @return 调用日志分页
     */
    IPage<AiModelCallLogVO> pageCallLogs(AiModelCallLogQueryDTO query);

    /**
     * 统计模型调用成本。
     *
     * @param query 查询条件
     * @return 成本统计结果
     */
    AiModelCostStatsVO costStats(AiModelCallLogQueryDTO query);
}
