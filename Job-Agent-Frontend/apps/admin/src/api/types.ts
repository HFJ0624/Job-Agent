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
