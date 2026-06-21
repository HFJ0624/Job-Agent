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
 * 作者:hfj
 * 功能:后台 AI 模型与路由管理服务
 * 日期:2026/6/21
 */
public interface AdminAiModelService {

    IPage<AiModelConfigVO> pageModels(AiModelConfigQueryDTO query);

    List<AiModelConfigVO> listActiveModels();

    AiModelConfigVO createModel(AiModelConfigSaveDTO request);

    AiModelConfigVO updateModel(Long id, AiModelConfigSaveDTO request);

    void deleteModel(Long id);

    IPage<AiModelRouteVO> pageRoutes(AiModelRouteQueryDTO query);

    AiModelRouteVO createRoute(AiModelRouteSaveDTO request);

    AiModelRouteVO updateRoute(Long id, AiModelRouteSaveDTO request);

    void deleteRoute(Long id);

    IPage<AiModelCallLogVO> pageCallLogs(AiModelCallLogQueryDTO query);

    AiModelCostStatsVO costStats(AiModelCallLogQueryDTO query);
}
