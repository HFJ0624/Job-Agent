/**
 * 后端统一返回结构，对应 com.job.common.entity.base.Result。
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

export interface PageResult<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface RegisterPayload {
  username: string;
  password: string;
  nickname?: string;
  realName?: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
  gender?: number;
  education?: string;
  workYears?: number;
}

export interface LoginPayload {
  account: string;
  password: string;
}

export interface UpdateProfilePayload {
  nickname?: string;
  realName?: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
  gender?: number;
  education?: string;
  workYears?: number;
}

export interface FileUploadResponse {
  url: string;
  objectName: string;
  originalFilename: string;
}

/**
 * 简历信息，对应后端 ResumeVO。
 */
export interface ResumeInfo {
  id: string;
  userId: string;
  resumeName: string;
  fileUrl: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  rawText?: string;
  parsedJson?: string;
  score?: number;
  status: string;
  isDefault: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * 用户地址信息，对应后端 UserAddressVO。
 */
export interface UserAddressInfo {
  id: string;
  userId: string;
  addressName?: string;
  province?: string;
  city?: string;
  district?: string;
  detailAddress?: string;
  longitude?: number;
  latitude?: number;
  isDefault?: number;
  createTime?: string;
  updateTime?: string;
}

export interface SaveUserAddressPayload {
  id?: string;
  addressName?: string;
  province?: string;
  city?: string;
  district?: string;
  detailAddress?: string;
  longitude?: string;
  latitude?: string;
}

/**
 * 岗位信息，对应后端 PositionVO。
 */
export interface PositionInfo {
  id: string;
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
 * 公司信息，对应后端 CompanyVO。
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
 * 岗位详情信息，对应后端 PositionDetailVO。
 */
export interface PositionDetailInfo {
  position: PositionInfo;
  company?: CompanyInfo;
  favorited: boolean;
  favoriteCount: number;
}

/**
 * 收藏状态，对应后端 FavoriteStateVO。
 */
export interface FavoriteStateInfo {
  positionId: number;
  favorited: boolean;
  favoriteCount: number;
}

/**
 * 立即沟通请求参数，对应后端 JobCommunicationDTO。
 */
export interface CommunicatePayload {
  content?: string;
}

/**
 * 立即沟通消息，对应后端 JobMessageVO。
 */
export interface JobMessageInfo {
  id: number;
  positionId: number;
  companyId: number;
  receiverName: string;
  content: string;
  status: string;
  createTime?: string;
}

/**
 * 简历评分结果，对应后端 ResumeScoreVO。
 */
export interface ResumeScoreInfo {
  id: number;
  resumeId: number;
  userId: number;

  totalScore: number;
  level: string;

  basicInfoScore: number;
  educationScore: number;
  skillScore: number;
  projectScore: number;
  experienceScore: number;
  expressionScore: number;

  targetPosition?: string;

  advantages: string[];
  problems: string[];
  suggestions: string[];

  createTime?: string;
}

/**
 * 岗位匹配分析结果。
 */
export interface JobMatchInfo {
  id: number;
  userId: number;
  resumeId: number;
  jobId: number;

  matchScore: number;
  ruleScore: number;
  skillScore: number;
  projectScore: number;
  conditionScore: number;
  preferenceScore: number;

  matchLevel: string;
  recommendApply: boolean;

  matchedSkills: string[];
  missingSkills: string[];
  advantages: string[];
  riskPoints: string[];
  suggestions: string[];

  createTime?: string;
}

/**
 * HR 打招呼语生成结果。
 */
export interface GreetingInfo {
  id: number;
  userId: number;
  resumeId: number;
  jobId: number;

  style: string;
  content: string;
  matchedSkills: string[];
  source: string;

  createTime?: string;
}

/**
 * AI 助手回复结果。
 */
export interface AgentChatInfo {
  conversationId: number;
  answer: string;
}

/**
 * 前端展示用的聊天消息。
 */
export interface AgentChatMessage {
  role: "USER" | "ASSISTANT";
  content: string;
}

/**
 * AI 助手回复结果。
 */
export interface AgentChatInfo {
  conversationId: number;
  answer: string;
}

/**
 * AI 会话列表项。
 */
export interface AgentConversationInfo {
  id: number;
  title: string;
  conversationType: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * AI 历史消息。
 */
export interface AgentMessageInfo {
  id: number;
  conversationId: number;
  role: "USER" | "ASSISTANT" | "SYSTEM" | "TOOL";
  content: string;
  toolName?: string;
  createTime?: string;
}

/**
 * 前端展示用的聊天消息。
 */
export interface AgentChatMessage {
  role: "USER" | "ASSISTANT";
  content: string;
}

/**
 * 用户求职偏好。
 */
export interface UserJobPreferenceInfo {
  id?: number;
  expectedJobTitle?: string;
  expectedCity?: string;
  minSalary?: number;
  maxSalary?: number;
  expectedIndustry?: string;
  expectedCompanySize?: string;
  expectedFinancingStage?: string;
  expectedEducation?: string;
  expectedExperience?: string;
  expectedWorkType?: string;
  skillKeywords?: string;
  remark?: string;
}

/**
 * 岗位推荐结果。
 */
export interface JobRecommendInfo {
  jobId: number;
  jobTitle: string;
  companyId?: number;
  companyName?: string;
  city?: string;
  district?: string;
  minSalary?: number;
  maxSalary?: number;
  educationReq?: string;
  experienceReq?: string;
  skillKeywords?: string;
  recommendScore: number;
  recommendLevel: string;
  matchedSkills: string[];
  missingSkills: string[];
  reasons: string[];
}

/**
 * 求职记录。
 */
export interface JobApplicationInfo {
  id: number;
  jobId: number;
  resumeId?: number;
  companyId?: number;

  companyName?: string;
  jobTitle?: string;
  city?: string;
  salaryText?: string;
  source?: string;

  status: string;
  statusText: string;
  priority?: string;
  priorityText?: string;

  hrName?: string;
  hrContact?: string;

  applyTime?: string;
  interviewTime?: string;
  nextFollowTime?: string;

  note?: string;
  lastAction?: string;

  createTime?: string;
  updateTime?: string;
}

/**
 * 求职记录统计。
 */
export interface JobApplicationStatsInfo {
  totalCount: number;
  statusCountMap: Record<string, number>;
  todayFollowCount: number;
  interviewingCount: number;
}

/**
 * 求职记录分页结果。
 */
export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}
