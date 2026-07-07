/**
 * 后端统一返回结构，对应 Java 里的 Result<T>。
 */
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

export interface UserInfo {
  id: number;
  username: string;
  nickname?: string;
  realName?: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
  gender?: number;
  education?: string;
  workYears?: number;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

export interface LoginResponse {
  tokenName: string;
  tokenValue: string;
  user: UserInfo;
}

/**
 * 后台首页核心指标卡片。
 */
export interface AdminDashboardMetric {
  label: string;
  value: number;
  subText: string;
}

/**
 * 后台首页待处理事项。
 */
export interface AdminDashboardPendingItem {
  title: string;
  content: string;
  level: "success" | "warning" | "danger" | string;
}

/**
 * 后台首页系统能力状态。
 */
export interface AdminDashboardSystemItem {
  label: string;
  value: string;
}

/**
 * 后台首页求职跟进 Agent 看板项。
 */
export interface AdminFollowUpAgentItem {
  title: string;
  value: number;
  description: string;
  level: "success" | "warning" | "danger" | "info" | string;
}

/**
 * 后台首页聚合数据。
 */
export interface AdminDashboardOverview {
  metrics: AdminDashboardMetric[];
  pendingItems: AdminDashboardPendingItem[];
  systemItems: AdminDashboardSystemItem[];
  followUpAgentItems?: AdminFollowUpAgentItem[];
}

/**
 * 公司信息，和后端 CompanyVO 字段保持一致。
 */
export interface CompanyInfo {
  id: number;
  companyName: string;
  logoUrl?: string;
  industry?: string;
  companySize?: string;
  financingStage?: string;
  description?: string;
  province?: string;
  city?: string;
  district?: string;
  address?: string;
  longitude?: number;
  latitude?: number;
  prospectScore?: number;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * 公司新增和编辑表单，和后端 CompanySaveDTO 对应。
 */
export interface CompanySavePayload {
  companyName: string;
  logoUrl?: string;
  industry?: string;
  companySize?: string;
  financingStage?: string;
  description?: string;
  province?: string;
  city?: string;
  district?: string;
  address?: string;
  longitude?: number | null;
  latitude?: number | null;
  prospectScore?: number | null;
  status?: number;
}

/**
 * 公司 Excel 导入结果，和后端 CompanyImportVO 对应。
 */
export interface CompanyImportResult {
  totalRows: number;
  insertCount: number;
  updateCount: number;
  failureCount: number;
  failureMessages: string[];
}

/**
 * 岗位信息，和后端 PositionVO 字段保持一致。
 */
export interface PositionInfo {
  id: number;
  companyId: number;
  companyName?: string;
  companyLogoUrl?: string;
  companyIndustry?: string;
  companySize?: string;
  financingStage?: string;
  jobTitle: string;
  jobCategory?: string;
  city?: string;
  district?: string;
  minSalary?: number;
  maxSalary?: number;
  salaryMonths?: number;
  educationReq?: string;
  experienceReq?: string;
  jobDescription?: string;
  jobRequirement?: string;
  skillKeywords?: string;
  workType?: string;
  welfareTags?: string;
  source?: string;
  sourceUrl?: string;
  status?: number;
  publishTime?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 岗位新增和编辑表单，和后端 PositionSaveDTO 对应。
 */
export interface PositionSavePayload {
  companyId: number | null;
  jobTitle: string;
  jobCategory?: string;
  city?: string;
  district?: string;
  minSalary?: number | null;
  maxSalary?: number | null;
  salaryMonths?: number | null;
  educationReq?: string;
  experienceReq?: string;
  jobDescription?: string;
  jobRequirement?: string;
  skillKeywords?: string;
  workType?: string;
  welfareTags?: string;
  source?: string;
  sourceUrl?: string;
  status?: number;
}

/**
 * 岗位 Excel 导入结果，和后端 PositionImportVO 对应。
 */
export interface PositionImportResult {
  totalRows: number;
  insertCount: number;
  updateCount: number;
  failureCount: number;
  failureMessages: string[];
}

/**
 * Agent Trace 日志查询参数。
 */
export interface AgentTraceLogQuery {
  pageNum: number;
  pageSize: number;
  traceId?: string;
  userId?: string | number;
  conversationId?: string | number;
  intentCode?: string;
  toolName?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}

/**
 * Agent Trace 日志展示数据。
 */
export interface AgentTraceLogInfo {
  id: number;
  traceId: string;
  userId: number;
  conversationId?: number;
  intentCode?: string;
  toolName?: string;
  inputData?: string;
  outputData?: string;
  status: string;
  errorMsg?: string;
  costTime?: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * Agent 工具参数 Schema。
 */
export interface AgentToolParamSchemaInfo {
  name: string;
  type: string;
  required?: boolean;
  description?: string;
  source?: string;
  example?: string;
  defaultValue?: string;
  sensitive?: boolean;
}

/**
 * Agent 工具出参 Schema。
 */
export interface AgentToolOutputSchemaInfo {
  name: string;
  type: string;
  description?: string;
  nullable?: boolean;
  example?: string;
}

/**
 * Agent 工具错误码 Schema。
 */
export interface AgentToolErrorSchemaInfo {
  code: string;
  message?: string;
  userMessage?: string;
  retryable?: boolean;
}

/**
 * Agent 工具统一 Schema，用于后台展示外部连接器能力。
 */
export interface AgentToolSchemaInfo {
  toolName: string;
  displayName: string;
  category: string;
  version?: string;
  description?: string;
  javaClassName?: string;
  javaMethodName?: string;
  permissionType?: string;
  sideEffectType?: string;
  hasSideEffect?: boolean;
  confirmationType?: string;
  requiresUserConfirmation?: boolean;
  confirmationMessage?: string;
  inputParams?: AgentToolParamSchemaInfo[];
  outputFields?: AgentToolOutputSchemaInfo[];
  errorCodes?: AgentToolErrorSchemaInfo[];
}

/**
 * Agent Eval 数据集。
 */
export interface AgentEvalDatasetInfo {
  id?: number;
  datasetName: string;
  datasetCode: string;
  description?: string;
  evalType?: string;
  enableStatus?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AgentEvalDatasetQuery {
  pageNum: number;
  pageSize: number;
  datasetName?: string;
  datasetCode?: string;
  evalType?: string;
  enableStatus?: number | string;
}

/**
 * Agent Eval 用例。
 */
export interface AgentEvalCaseInfo {
  id?: number;
  datasetId?: number;
  caseName: string;
  userId: number;
  inputMessage: string;
  evalType?: string;
  expectedIntent?: string;
  expectedToolName?: string;
  expectedToolParamsJson?: string;
  expectedRagDocumentId?: number;
  expectedRagChunkId?: number;
  expectedRagKeywords?: string;
  expectedAnswerKeywords?: string;
  minAnswerScore?: number;
  tags?: string;
  enableStatus?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AgentEvalCaseQuery {
  pageNum: number;
  pageSize: number;
  datasetId?: number | string;
  caseName?: string;
  userId?: number | string;
  evalType?: string;
  expectedToolName?: string;
  enableStatus?: number | string;
}

/**
 * Agent Eval 核心链路模板创建参数。
 */
export interface AgentEvalCoreTemplateCreatePayload {
  datasetId?: number;
  userId: number;
  overwrite?: boolean;
}

/**
 * Agent Eval 核心链路模板创建结果。
 */
export interface AgentEvalCoreTemplateCreateResult {
  createdCount: number;
  skippedCount: number;
  skippedTypes: string[];
  createdCases: AgentEvalCaseInfo[];
}

/**
 * Agent Eval 批量运行记录。
 */
export interface AgentEvalRunInfo {
  id: number;
  datasetId?: number;
  runName?: string;
  runType?: string;
  totalCount: number;
  passCount: number;
  failCount: number;
  toolAccuracy?: number;
  paramAccuracy?: number;
  ragHitRate?: number;
  answerQualityAvg?: number;
  avgCostTime?: number;
  baselineFlag?: number;
  compareRunId?: number;
  passRateDelta?: number;
  toolAccuracyDelta?: number;
  paramAccuracyDelta?: number;
  ragHitRateDelta?: number;
  answerQualityDelta?: number;
  failureStatsJson?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
  failReason?: string;
  createTime?: string;
}

export interface AgentEvalRunQuery {
  pageNum: number;
  pageSize: number;
  datasetId?: number | string;
  runType?: string;
  status?: string;
}

/**
 * Agent Eval 单条结果。
 */
export interface AgentEvalResultInfo {
  id: number;
  runId?: number;
  datasetId?: number;
  caseId?: number;
  userId?: number;
  conversationId?: number;
  inputMessage?: string;
  evalType?: string;
  actualAnswer?: string;
  actualTools?: string;
  expectedToolName?: string;
  toolSelectPass?: number;
  expectedToolParamsJson?: string;
  actualToolParamsJson?: string;
  toolParamPass?: number;
  ragHitPass?: number;
  ragHitRank?: number;
  ragResultsJson?: string;
  answerKeywordPass?: number;
  answerQualityScore?: number;
  judgeScore?: number;
  judgePass?: number;
  judgeReason?: string;
  judgeDetailJson?: string;
  baselineResultId?: number;
  answerScoreDelta?: number;
  passStatus?: number;
  failReason?: string;
  failureType?: string;
  traceId?: string;
  costTime?: number;
  createTime?: string;
}

export interface AgentEvalResultQuery {
  pageNum: number;
  pageSize: number;
  runId?: number | string;
  datasetId?: number | string;
  caseId?: number | string;
  evalType?: string;
  passStatus?: number | string;
  failureType?: string;
}

/**
 * Agent Eval 单条结果诊断。
 */
export interface AgentEvalResultDiagnosis {
  resultId: number;
  passStatus?: number;
  failureType?: string;
  priority?: string;
  summary?: string;
  rootCauses: string[];
  suggestions: string[];
  evidence: string[];
}

/**
 * Agent Eval 诊断快捷修复参数。
 */
export interface AgentEvalQuickFixPayload {
  actionType: string;
}

/**
 * Agent Eval 用例质量检查问题项。
 */
export interface AgentEvalCaseQualityIssue {
  caseId?: number;
  caseName?: string;
  evalType?: string;
  riskLevel: string;
  issueType: string;
  issueMessage: string;
  suggestion?: string;
  fixable?: boolean;
  fixActionType?: string;
  fixButtonText?: string;
  fixConfirmText?: string;
}

/**
 * Agent Eval 用例质量检查报告。
 */
export interface AgentEvalCaseQualityReport {
  totalCaseCount: number;
  problemCaseCount: number;
  highRiskIssueCount: number;
  mediumRiskIssueCount: number;
  lowRiskIssueCount: number;
  issues: AgentEvalCaseQualityIssue[];
}

/**
 * Agent Eval 质量体检指标项。
 */
export interface AgentEvalHealthMetric {
  metricCode: string;
  metricName: string;
  metricValue?: number;
  percentMetric?: boolean;
  status?: string;
  description?: string;
}

/**
 * Agent Eval 失败分类项。
 */
export interface AgentEvalHealthFailure {
  failureType: string;
  count: number;
  suggestion?: string;
}

/**
 * Agent 核心链路质量体检报告。
 */
export interface AgentEvalHealthReport {
  latestRunId?: number;
  latestRunName?: string;
  datasetId?: number;
  totalCount: number;
  passCount: number;
  failCount: number;
  passRate: number;
  toolAccuracy?: number;
  paramAccuracy?: number;
  ragHitRate?: number;
  answerQualityAvg?: number;
  coreCoverageRate: number;
  weakestMetric?: string;
  weakestMetricValue?: number;
  coveredCoreTypes: string[];
  missingCoreTypes: string[];
  metricItems: AgentEvalHealthMetric[];
  failureItems: AgentEvalHealthFailure[];
  qualitySuggestions: string[];
}

/**
 * 工作流任务查询参数。
 */
export interface WorkflowTaskQuery {
  pageNum: number;
  pageSize: number;
  taskNo?: string;
  taskType?: string;
  status?: string;
  bizId?: string | number;
  userId?: string | number;
  startTime?: string;
  endTime?: string;
}

/**
 * 工作流任务展示数据。
 */
export interface WorkflowTaskInfo {
  id: number;
  taskNo: string;
  taskType: string;
  bizId?: number;
  userId?: number;
  requestJson?: string;
  resultJson?: string;
  status: string;
  progressPercent?: number;
  currentStep?: string;
  retryCount?: number;
  maxRetryCount?: number;
  retryIntervalSeconds?: number;
  nextRunTime?: string;
  lockedBy?: string;
  lockTime?: string;
  startTime?: string;
  finishTime?: string;
  costTime?: number;
  errorMsg?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 工作流任务阶段日志。
 */
export interface WorkflowTaskLogInfo {
  id: number;
  taskId: number;
  taskNo: string;
  taskType: string;
  stepName?: string;
  progressPercent?: number;
  logMessage?: string;
  logLevel?: string;
  errorMsg?: string;
  createTime?: string;
}

/**
 * Agent 观测看板查询参数。
 */
export interface AgentActionItemQuery {
  pageNum: number;
  pageSize: number;
  userId?: string | number;
  sourceType?: string;
  actionType?: string;
  actionStatus?: string;
  failedOnly?: boolean;
  hasWorkflowTask?: boolean | string;
  workflowTaskId?: string | number;
  keyword?: string;
}

export interface AgentActionItemInfo {
  id: number;
  userId: number;
  actionKey: string;
  sourceType: string;
  sourceId?: number;
  actionType: string;
  bizType?: string;
  bizId?: number;
  actionTitle: string;
  actionDesc?: string;
  actionPayload?: string;
  priority: string;
  actionStatus: string;
  targetPath?: string;
  executeError?: string;
  workflowTaskId?: number;
  workflowTaskNo?: string;
  workflowTaskStatus?: string;
  workflowTaskProgress?: number;
  workflowTaskStep?: string;
  workflowTaskError?: string;
  snoozeUntil?: string;
  note?: string;
  doneTime?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AgentObservationDashboardQuery {
  startTime?: string;
  endTime?: string;
  eventType?: string;
  modelCode?: string;
  toolName?: string;
}

/**
 * Agent 观测统计项。
 */
export interface AgentObservationStatItem {
  name: string;
  count: number;
  ratio?: number;
  totalCost?: number;
  totalTokens?: number;
  avgDurationMs?: number;
  maxDurationMs?: number;
  lastTime?: string;
}

/**
 * Agent 观测看板数据。
 */
export interface AgentObservationDashboard {
  totalEvents: number;
  successEvents: number;
  failedEvents: number;
  blockedEvents: number;
  skippedEvents: number;
  successRate: number;
  avgDurationMs: number;
  totalTokens: number;
  totalCost: number;
  eventTypeStats: AgentObservationStatItem[];
  failureStats: AgentObservationStatItem[];
  slowModelStats: AgentObservationStatItem[];
  slowToolStats: AgentObservationStatItem[];
  recentAlerts: AgentObservationAlertRecordInfo[];
}

/**
 * Agent 运营看板顶部指标卡。
 */
export interface AgentOperationMetric {
  label: string;
  value: number;
  subText: string;
  level: "success" | "warning" | "danger" | "info" | string;
}

/**
 * Agent 运营看板通用统计项。
 */
export interface AgentOperationStat {
  name: string;
  count: number;
  ratio: number;
}

/**
 * Agent 运营看板最近失败记录。
 */
export interface AgentOperationFailure {
  failureType: string;
  userId?: number;
  title?: string;
  reason?: string;
  createTime?: string;
}

/**
 * Agent 运营看板聚合数据。
 */
export interface AgentOperationDashboard {
  metrics: AgentOperationMetric[];
  reportStats: AgentOperationStat[];
  actionStatusStats: AgentOperationStat[];
  actionSourceStats: AgentOperationStat[];
  actionTypeFailureStats: AgentOperationStat[];
  recentFailures: AgentOperationFailure[];
}

/**
 * Agent 统一观测事件查询参数。
 */
export interface AgentObservationEventQuery {
  pageNum: number;
  pageSize: number;
  traceId?: string;
  userId?: string | number;
  conversationId?: string | number;
  planId?: string | number;
  stepId?: string | number;
  sceneCode?: string;
  intentCode?: string;
  eventType?: string;
  eventName?: string;
  status?: string;
  errorCategory?: string;
  modelCode?: string;
  toolName?: string;
  startTime?: string;
  endTime?: string;
}

/**
 * Agent 统一观测事件展示数据。
 */
export interface AgentObservationEventInfo {
  id: number;
  traceId?: string;
  spanId?: string;
  parentSpanId?: string;
  userId?: number;
  conversationId?: number;
  planId?: number;
  stepId?: number;
  sceneCode?: string;
  intentCode?: string;
  eventType?: string;
  eventName?: string;
  status?: string;
  errorCategory?: string;
  errorCode?: string;
  errorMsg?: string;
  modelCode?: string;
  toolName?: string;
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  totalCost?: number;
  durationMs?: number;
  requestSnapshot?: string;
  responseSnapshot?: string;
  createTime?: string;
}

/**
 * Agent 观测告警规则查询参数。
 */
export interface AgentObservationAlertRuleQuery {
  pageNum: number;
  pageSize: number;
  ruleName?: string;
  ruleType?: string;
  status?: string;
}

/**
 * Agent 观测告警规则展示数据。
 */
export interface AgentObservationAlertRuleInfo {
  id?: number;
  ruleName: string;
  ruleType: string;
  eventType?: string;
  errorCategory?: string;
  modelCode?: string;
  toolName?: string;
  thresholdValue: number;
  windowMinutes?: number;
  minSampleCount?: number;
  cooldownMinutes?: number;
  alertLevel?: string;
  status?: string;
  lastEvaluateTime?: string;
  lastAlertTime?: string;
  remark?: string;
  createTime?: string;
}

/**
 * Agent 观测告警记录查询参数。
 */
export interface AgentObservationAlertRecordQuery {
  pageNum: number;
  pageSize: number;
  ruleId?: string | number;
  ruleType?: string;
  alertLevel?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}

/**
 * Agent 观测告警记录展示数据。
 */
export interface AgentObservationAlertRecordInfo {
  id: number;
  ruleId?: number;
  ruleName?: string;
  ruleType?: string;
  alertLevel?: string;
  metricValue?: number;
  thresholdValue?: number;
  windowStartTime?: string;
  windowEndTime?: string;
  alertMessage?: string;
  status?: string;
  createTime?: string;
}

/**
 * Agent Trace 保留策略展示数据。
 */
export interface AgentTraceRetentionPolicyInfo {
  id?: number;
  policyName: string;
  targetTable: string;
  retentionDays: number;
  batchSize?: number;
  status?: string;
  lastExecuteTime?: string;
  lastDeletedCount?: number;
  remark?: string;
  createTime?: string;
}

/**
 * Agent Trace 保留策略预览数据。
 */
export interface AgentTraceRetentionPreview {
  policyId: number;
  targetTable: string;
  retentionDays: number;
  cutoffTime?: string;
  matchedCount: number;
  batchSize: number;
}

/**
 * Agent 计划查询参数。
 */
export interface AgentPlanQuery {
  pageNum: number;
  pageSize: number;
  traceId?: string;
  userId?: string | number;
  conversationId?: string | number;
  intentCode?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}

/**
 * Agent 计划步骤展示数据。
 */
export interface AgentPlanStepInfo {
  id: number;
  planId: number;
  userId: number;
  conversationId?: number;
  stepNo: number;
  stepName: string;
  stepGoal?: string;
  toolName?: string;
  toolInputSchema?: string;
  completionCriteria?: string;
  status: string;
  resultSummary?: string;
  errorMsg?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * Agent 计划展示数据。
 */
export interface AgentPlanInfo {
  id: number;
  traceId: string;
  userId: number;
  conversationId?: number;
  intentCode?: string;
  userGoal?: string;
  planTitle?: string;
  planSummary?: string;
  requiredParamsJson?: string;
  extractedParamsJson?: string;
  missingParamsJson?: string;
  status: string;
  failReason?: string;
  steps?: AgentPlanStepInfo[];
  createTime?: string;
  updateTime?: string;
}

/**
 * Agent 长期记忆查询参数。
 */
export interface AgentMemoryQuery {
  pageNum: number;
  pageSize: number;
  userId?: string | number;
  memoryType?: string;
  memoryKey?: string;
  sourceType?: string;
  status?: string;
  keyword?: string;
  startTime?: string;
  endTime?: string;
}

/**
 * Agent 长期记忆展示数据。
 */
export interface AgentMemoryInfo {
  id: number;
  userId: number;
  memoryType?: string;
  memoryKey?: string;
  memoryValue?: string;
  summary?: string;
  sourceType?: string;
  sourceId?: number;
  confidence?: number;
  importance?: number;
  status?: string;
  lastUsedTime?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * Agent 用户长期记忆画像展示数据。
 */
export interface AgentUserMemoryProfileInfo {
  id?: number;
  userId?: number;
  profileSummary?: string;
  memoryCount?: number;
  profileVersion?: number;
  lastBuildTime?: string;
  status?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * MyBatis-Plus 分页返回结构。
 */
/**
 * RAG 文档类型统计。
 */
export interface RagDocumentTypeStats {
  userId: number;
  documentType: string;
  documentCount: number;
  chunkCount: number;
}

/**
 * RAG 知识库统计。
 */
export interface RagStats {
  tableName: string;
  dimension: number;
  maxResults: number;
  minScore: number;
  chunkSize: number;
  chunkOverlap: number;
  extensionReady: boolean;
  tableReady: boolean;
  schemaReady: boolean;
  setupMessage?: string;
  totalChunks: number;
  publicChunks: number;
  privateChunks: number;
  typeStats: RagDocumentTypeStats[];
}

/**
 * RAG 索引结果。
 */
export interface RagIndexResult {
  indexedDocumentCount: number;
  indexedChunkCount: number;
  resumeCount: number;
  jobCount: number;
  companyCount: number;
  communicationCount: number;
  messageCount: number;
  skippedDocumentCount: number;
  warnings: string[];
}

/**
 * RAG 检索结果。
 */
export interface RagSearchResult {
  id: number;
  documentId?: number;
  chunkId?: number;
  referenceNo?: number;
  userId: number;
  documentType: string;
  businessId: number;
  chunkIndex: number;
  title?: string;
  content: string;
  source?: string;
  permissionScope?: string;
  referenceTitle?: string;
  score: number;
  vectorScore?: number;
  keywordScore?: number;
  rerankScore?: number;
  retrievalSource?: string;
  metadata?: Record<string, unknown>;
}

/**
 * RAG 文档分页查询参数。
 */
export interface RagDocumentQuery {
  pageNum: number;
  pageSize: number;
  userId?: string | number;
  documentType?: string;
  businessId?: string | number;
  title?: string;
  permissionScope?: string;
  status?: string;
  indexStatus?: string;
  keyword?: string;
  startTime?: string;
  endTime?: string;
}

/**
 * RAG 切片分页查询参数。
 */
export interface RagChunkQuery {
  pageNum: number;
  pageSize: number;
  documentId?: string | number;
  userId?: string | number;
  documentType?: string;
  businessId?: string | number;
  title?: string;
  status?: string;
  vectorStatus?: string;
  keyword?: string;
  startTime?: string;
  endTime?: string;
}

/**
 * RAG 文档展示数据。
 */
export interface RagDocumentInfo {
  id: number;
  userId: number;
  documentType: string;
  businessId: number;
  title?: string;
  source?: string;
  permissionScope: string;
  contentHash?: string;
  chunkCount: number;
  status: string;
  indexStatus: string;
  errorMsg?: string;
  lastIndexTime?: string;
  metadataJson?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * RAG 切片展示数据。
 */
export interface RagChunkInfo {
  id: number;
  documentId: number;
  userId: number;
  documentType: string;
  businessId: number;
  chunkIndex: number;
  title?: string;
  content: string;
  contentHash?: string;
  source?: string;
  metadataJson?: string;
  status: string;
  vectorStatus: string;
  lastIndexTime?: string;
  createTime?: string;
  updateTime?: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface AgentFollowUpApplicationQuery {
  pageNum: number;
  pageSize: number;
  userId?: string | number;
  status?: string;
  keyword?: string;
  failedEmailOnly?: boolean;
}

export interface AgentFollowUpApplicationInfo {
  id: number;
  userId: number;
  jobId?: number;
  resumeId?: number;
  companyName?: string;
  jobTitle?: string;
  status?: string;
  priority?: string;
  hrName?: string;
  hrContact?: string;
  applyTime?: string;
  interviewTime?: string;
  nextFollowTime?: string;
  lastAction?: string;
  reminderCount?: number;
  pendingReminderCount?: number;
  emailTaskCount?: number;
  failedEmailTaskCount?: number;
  latestEmailTaskStatus?: string;
  latestEmailTaskTime?: string;
}

export interface AgentFollowUpRuleQuery {
  pageNum: number;
  pageSize: number;
  ruleName?: string;
  ruleType?: string;
  status?: string;
}

export interface AgentFollowUpRuleInfo {
  id?: number;
  ruleCode: string;
  ruleName: string;
  ruleType: string;
  triggerStatus?: string;
  delayMinutes?: number;
  reminderType?: string;
  reminderTitle?: string;
  reminderTemplate?: string;
  emailEnabled?: number;
  workflowEnabled?: number;
  maxRetryCount?: number;
  retryIntervalSeconds?: number;
  status?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * AI 模型配置查询参数。
 */
export interface AiModelConfigQuery {
  pageNum: number;
  pageSize: number;
  modelCode?: string;
  modelName?: string;
  provider?: string;
  status?: string;
}

/**
 * AI 模型配置展示数据。
 */
export interface AiModelConfigInfo {
  id?: number;
  modelCode: string;
  modelName: string;
  provider: string;
  baseUrl: string;
  apiKey?: string;
  chatPath?: string;
  modelIdentifier: string;
  temperature?: number;
  maxTokens?: number;
  timeoutSeconds?: number;
  maxRetries?: number;
  inputPricePer1k?: number;
  outputPricePer1k?: number;
  circuitEnabled?: number;
  failureThreshold?: number;
  cooldownSeconds?: number;
  status?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * AI Prompt 模板查询参数。
 */
export interface AiPromptTemplateQuery {
  pageNum: number;
  pageSize: number;
  promptCode?: string;
  sceneCode?: string;
  status?: string;
}

/**
 * AI Prompt 模板展示数据。
 */
export interface AiPromptTemplateInfo {
  id?: number;
  promptCode: string;
  promptName: string;
  sceneCode: string;
  description?: string;
  status?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * AI Prompt 版本展示数据。
 */
export interface AiPromptVersionInfo {
  id?: number;
  templateId: number;
  versionNo: string;
  title: string;
  content: string;
  variablesJson?: string;
  status?: string;
  grayPercent?: number;
  abGroup?: string;
  publishTime?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * AI 模型路由查询参数。
 */
export interface AiModelRouteQuery {
  pageNum: number;
  pageSize: number;
  sceneCode?: string;
  promptCode?: string;
  primaryModelCode?: string;
  status?: string;
}

/**
 * AI 模型路由展示数据。
 */
export interface AiModelRouteInfo {
  id?: number;
  sceneCode: string;
  routeName: string;
  primaryModelCode: string;
  fallbackModelCode?: string;
  promptCode: string;
  promptVersionId?: number | null;
  grayPercent?: number;
  abGroup?: string;
  status?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * AI 模型调用日志查询参数。
 */
export interface AiModelCallLogQuery {
  pageNum: number;
  pageSize: number;
  traceId?: string;
  userId?: string | number;
  sceneCode?: string;
  modelCode?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}

/**
 * AI 模型调用日志展示数据。
 */
export interface AiModelCallLogInfo {
  id: number;
  traceId?: string;
  userId?: number;
  sceneCode?: string;
  promptCode?: string;
  promptVersionId?: number;
  modelCode?: string;
  fallbackUsed?: number;
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  inputCost?: number;
  outputCost?: number;
  totalCost?: number;
  costTime?: number;
  status?: string;
  errorMsg?: string;
  createTime?: string;
}

/**
 * AI 模型成本统计。
 */
export interface AiModelCostStats {
  totalCalls: number;
  successCalls: number;
  failedCalls: number;
  totalTokens: number;
  totalCost: number;
  avgCostTime: number;
}

export interface MockInterviewSessionQuery {
  pageNum: number;
  pageSize: number;
  userId?: string | number;
  jobId?: string | number;
  resumeId?: string | number;
  status?: string;
  keyword?: string;
}

export interface MockInterviewQuestionInfo {
  id: number;
  sessionId: number;
  questionType: string;
  questionContent: string;
  standardAnswer?: string;
  sortNo: number;
  answered: number;
}

export interface MockInterviewAnswerInfo {
  id: number;
  sessionId: number;
  questionId: number;
  answerContent: string;
  score: number;
  level: string;
  strengths: string[];
  problems: string[];
  suggestions: string[];
  correct?: boolean;
  similarityScore?: number;
  matchedPoints?: string[];
  missingPoints?: string[];
  knowledgePoints?: string[];
  reviewConclusion?: string;
  wrongBook?: boolean;
}

export interface MockInterviewMediaRecordInfo {
  id: number;
  sessionId: number;
  questionId?: number;
  answerId?: number;
  userId: number;
  mediaType: string;
  fileUrl: string;
  objectName?: string;
  fileName?: string;
  fileSize?: number;
  durationSeconds?: number;
  asrText?: string;
  asrProvider?: string;
  asrStatus?: string;
  asrError?: string;
  createTime?: string;
}

export interface MockInterviewSessionInfo {
  id: number;
  userId?: number;
  applicationId?: number;
  interviewPrepareId?: number;
  jobId?: number;
  resumeId?: number;
  jobTitle?: string;
  companyName?: string;
  status: string;
  currentIndex: number;
  totalQuestionCount: number;
  totalScore?: number;
  summary?: string;
  questions?: MockInterviewQuestionInfo[];
  answers?: MockInterviewAnswerInfo[];
  mediaRecords?: MockInterviewMediaRecordInfo[];
  createTime?: string;
}

export interface MockInterviewReviewInfo {
  id: number;
  sessionId: number;
  applicationId?: number;
  jobId?: number;
  jobTitle?: string;
  companyName?: string;
  totalScore: number;
  reviewLevel: string;
  answeredCount: number;
  strengthSummary?: string;
  weaknessSummary?: string;
  improvementPlan?: string;
  weakQuestions: string[];
  abilityTags: string[];
  questionReviews?: MockInterviewQuestionReviewInfo[];
  source?: string;
  createTime?: string;
}

export interface MockInterviewQuestionReviewInfo {
  questionId: number;
  answerId?: number;
  sortNo?: number;
  questionType?: string;
  questionContent?: string;
  standardAnswer?: string;
  userAnswer?: string;
  score?: number;
  level?: string;
  correct?: boolean;
  similarityScore?: number;
  matchedPoints?: string[];
  missingPoints?: string[];
  knowledgePoints?: string[];
  reviewConclusion?: string;
  strengths?: string[];
  problems?: string[];
  suggestions?: string[];
  wrongBook?: boolean;
}

export interface InterviewQuestionBankQuery {
  pageNum: number;
  pageSize: number;
  keyword?: string;
  questionType?: string;
  category?: string;
  difficulty?: string;
  status?: string;
  sourceFile?: string;
}

export interface InterviewQuestionBankInfo {
  id: number;
  questionTitle: string;
  standardAnswer: string;
  questionType?: string;
  category?: string;
  difficulty?: string;
  tags?: string;
  sourceFile?: string;
  sourceHash?: string;
  ragDocumentId?: number;
  ragChunkId?: number;
  status?: string;
  createTime?: string;
  updateTime?: string;
}

export interface InterviewQuestionImportPayload {
  directoryPath?: string;
  indexAfterImport?: boolean;
}

export interface InterviewQuestionImportResult {
  scannedFileCount: number;
  parsedQuestionCount: number;
  insertedCount: number;
  updatedCount: number;
  indexedCount: number;
  failedCount: number;
  warnings: string[];
}
