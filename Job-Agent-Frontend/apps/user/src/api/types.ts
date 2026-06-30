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
  matchScore?: number;
}

/**
 * 用户端首页热门公司，对应后端 HomeHotCompanyVO。
 */
export interface HomeHotCompanyInfo {
  id: string;
  companyName: string;
  logoUrl?: string;
  industry?: string;
  companySize?: string;
  financingStage?: string;
  jobCount: number;
}

/**
 * 用户端首页简历报告，对应后端 HomeResumeMatchReportVO。
 */
export interface HomeResumeMatchReportInfo {
  hasResume: boolean;
  resumeId?: string;
  resumeName?: string;
  resumeStatus?: string;
  hasScore: boolean;
  score?: number;
  level?: string;
  summary?: string;
  suggestions: string[];
}

/**
 * 用户端首页聚合数据，对应后端 HomeOverviewVO。
 */
export interface HomeOverviewInfo {
  recommendedJobs: PositionInfo[];
  hotCompanies: HomeHotCompanyInfo[];
  resumeMatchReport: HomeResumeMatchReportInfo;
  aiSuggestion: string;
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
export interface ResumeScoreBreakdownInfo {
  basicInfoScore: number;
  careerGoalScore: number;
  educationScore: number;
  skillsScore: number;
  projectExperienceScore: number;
  workExperienceScore: number;
  quantifiedImpactScore: number;
  formatScore: number;
}

export interface ResumeScoreDimensionInfo {
  dimensionName: string;
  score: number;
  maxScore: number;
  reason?: string;
  issues?: string[];
  suggestions?: string[];
}

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

  scoreVersion?: string;
  overallScore?: number;
  scoreBreakdown?: ResumeScoreBreakdownInfo;
  dimensions?: ResumeScoreDimensionInfo[];
  strengths?: string[];
  weaknesses?: string[];
  riskPoints?: string[];
  improvementSuggestions?: string[];
  summary?: string;
  llmStatus?: "PROCESSING" | "SUCCESS" | "FAILED" | "SKIPPED" | string;
  llmError?: string;

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
  planId?: number | null;
  answer: string;
  requiresUserConfirmation?: boolean;
  requiredConfirmationToolNames?: string[];
  confirmationMessage?: string;
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
 * Agent Inbox 单条待办。
 */
export interface AgentInboxItemInfo {
  itemKey: string;
  itemType: string;
  itemTypeDesc?: string;
  priority: "HIGH" | "NORMAL" | "LOW" | string;
  title: string;
  description?: string;
  actionText?: string;
  targetPath?: string;
  sourceId?: number;
  applicationId?: number;
  communicationId?: number;
  jobId?: number;
  companyName?: string;
  jobTitle?: string;
  dueTime?: string;
  createTime?: string;
}

/**
 * Agent Inbox 今日待办聚合结果。
 */
export interface AgentInboxInfo {
  totalCount: number;
  highPriorityCount: number;
  dueCount: number;
  normalCount: number;
  summaryText?: string;
  items: AgentInboxItemInfo[];
}

/**
 * Agent 主动日报记录。
 */
export interface AgentDailyReportInfo {
  id: number;
  userId: number;
  reportDate: string;
  reportTitle: string;
  summaryText?: string;
  contentText?: string;
  inboxTotalCount: number;
  highPriorityCount: number;
  dueCount: number;
  emailTo?: string;
  emailStatus: "PENDING" | "SENT" | "SKIPPED" | "FAILED" | string;
  emailError?: string;
  sendTime?: string;
  createTime?: string;
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

/**
 * AI 面试准备结果。
 */
export interface InterviewPrepareInfo {
  id: number;
  applicationId: number;
  jobId?: number;
  resumeId?: number;

  jobTitle?: string;
  companyName?: string;

  technicalQuestions: string[];
  projectQuestions: string[];
  hrQuestions: string[];
  reviewSuggestions: string[];

  summary?: string;
  source?: string;
  createTime?: string;
}

/**
 * AI 投递决策。
 */
export interface JobApplyDecisionInfo {
  id: number;
  resumeId: number;
  jobId: number;
  jobTitle?: string;
  companyName?: string;
  decision: "APPLY" | "CAUTIOUS" | "SKIP" | string;
  decisionLabel: string;
  decisionScore: number;
  reason?: string;
  risks: string[];
  resumeSuggestions: string[];
  interviewSuggestions: string[];
  nextActions: string[];
  matchRecordId?: number;
  source?: string;
  createTime?: string;
}

/**
 * 模拟面试题目。
 */
export interface MockInterviewQuestionInfo {
  id: number;
  sessionId: number;
  questionType: string;
  questionContent: string;
  sortNo: number;
  answered: number;
}

/**
 * 模拟面试回答评分。
 */
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

/**
 * 模拟面试会话。
 */
export interface MockInterviewSessionInfo {
  id: number;
  applicationId: number;
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

/**
 * 模拟面试复盘报告。
 */
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

  source?: string;
  createTime?: string;
}

/**
 * 模拟面试补课清单。
 */
export interface MockInterviewStudyPlanInfo {
  sessionId: number;
  reviewId: number;
  items: MockInterviewStudyItemInfo[];
}

export interface MockInterviewStudyItemInfo {
  knowledgePoint: string;
  suggestion: string;
  materials: MockInterviewStudyMaterialInfo[];
}

export interface MockInterviewStudyMaterialInfo {
  documentId?: number;
  chunkId?: number;
  title?: string;
  content?: string;
  source?: string;
  score?: number;
}

export interface MockInterviewWrongQuestionInfo {
  id: number;
  sessionId: number;
  questionId: number;
  answerId: number;
  jobId?: number;
  resumeId?: number;
  questionType?: string;
  questionContent: string;
  standardAnswer?: string;
  lastAnswerContent?: string;
  lastScore?: number;
  similarityScore?: number;
  knowledgePoints: string[];
  missingPoints: string[];
  suggestions: string[];
  wrongReason?: string;
  wrongCount: number;
  masteryStatus: string;
  createTime?: string;
  updateTime?: string;
}

export interface MockInterviewLearningPlanInfo {
  id: number;
  planTitle: string;
  planDays: number;
  source: string;
  weakKnowledgePoints: string[];
  status: string;
  createTime?: string;
  items: MockInterviewLearningPlanItemInfo[];
}

export interface MockInterviewLearningPlanItemInfo {
  id: number;
  dayNo: number;
  title: string;
  knowledgePoint: string;
  learningGoal?: string;
  practiceTask?: string;
  reviewSuggestion?: string;
  completionStatus: string;
  materials: MockInterviewLearningPlanMaterialInfo[];
}

export interface MockInterviewLearningPlanMaterialInfo {
  documentId?: number;
  chunkId?: number;
  title?: string;
  content?: string;
  source?: string;
  score?: number;
}

export interface MockInterviewStudyPlanRetestInfo {
  id: number;
  planId: number;
  itemId: number;
  knowledgePoint: string;
  questionContent: string;
  standardAnswer?: string;
  userAnswer?: string;
  score?: number;
  passed?: boolean;
  feedback?: string;
  suggestion?: string;
  status: string;
  createTime?: string;
}

/**
 * 求职沟通记录状态。
 *
 * 说明:
 * 这些值要和后端 CommunicationStatus 枚举保持一致。
 */
export type CommunicationStatus =
  | "GREETING_GENERATED"
  | "COPIED"
  | "COMMUNICATED"
  | "REPLIED"
  | "AI_REPLY_GENERATED"
  | "USER_REPLIED"
  | "INTERVIEW_INVITED"
  | "NO_REPLY"
  | "CLOSED";

/**
 * 求职沟通记录。
 *
 * 对应后端 JobCommunicationRecordVO。
 */
export interface CommunicationRecordInfo {
  id: number;
  applicationId?: number;

  /**
   * 简历ID，内部使用，页面不要直接展示。
   */
  resumeId?: number;

  /**
   * 简历名称，页面展示用。
   */
  resumeName?: string;

  /**
   * 岗位ID，内部使用，页面不要直接展示。
   */
  jobId: number;

  /**
   * 岗位名称。
   */
  jobTitle?: string;

  /**
   * 公司ID。
   */
  companyId?: number;

  /**
   * 公司名称。
   */
  companyName?: string;

  /**
   * 工作城市。
   */
  jobCity?: string;

  /**
   * 最低薪资。
   */
  minSalary?: number;

  /**
   * 最高薪资。
   */
  maxSalary?: number;

  /**
   * 薪资展示文本。
   */
  salaryText?: string;

  greetingRecordId?: number;

  platform?: string;
  externalJobUrl?: string;

  hrName?: string;
  hrContact?: string;

  greetingText?: string;
  hrReply?: string;

  communicationStatus: CommunicationStatus;
  communicationStatusDesc?: string;

  interviewTime?: string;
  nextFollowTime?: string;

  aiReplyText?: string;
  userReplyText?: string;
  note?: string;

  interviewMethod?: string;
  interviewMethodDesc?: string;
  interviewLocation?: string;
  interviewPlatform?: string;
  meetingLink?: string;
  interviewContact?: string;
  interviewExtractJson?: string;
  interviewExtractConfidence?: number;

  createTime?: string;
  updateTime?: string;
}

/**
 * 沟通记录分页结果。
 */
export interface CommunicationPageResult {
  records: CommunicationRecordInfo[];
  total: number;
  pageNo: number;
  pageSize: number;
}

/**
 * 沟通记录统计。
 */
export interface CommunicationStatsInfo {
  totalCount: number;
  greetingGeneratedCount: number;
  copiedCount: number;
  communicatedCount: number;
  repliedCount: number;
  interviewInvitedCount: number;
  noReplyCount: number;
  closedCount: number;
}

/**
 * 新增沟通记录请求参数。
 */
export interface CommunicationCreatePayload {
  applicationId?: number;
  resumeId?: number;
  jobId: number;
  greetingRecordId?: number;
  platform?: string;
  externalJobUrl?: string;
  hrName?: string;
  hrContact?: string;
  greetingText?: string;
  note?: string;
}

/**
 * 保存 HR 回复请求参数。
 */
export interface CommunicationReplyPayload {
  hrReply: string;
  note?: string;
}

/**
 * 标记面试邀约请求参数。
 */
export interface CommunicationInterviewPayload {
  interviewTime?: string;
  nextFollowTime?: string;
  note?: string;
}

/**
 * 沟通消息流水。
 */
export interface CommunicationMessageInfo {
  id: number;
  communicationId: number;
  senderType: string;
  senderTypeDesc?: string;
  messageContent: string;
  replyStyle?: string;
  statusAfter?: string;
  createTime?: string;
}

/**
 * 保存 HR 回复并生成 AI 回复请求。
 */
export interface HrReplyGeneratePayload {
  hrReply: string;
  progressStatus?: string;
  replyStyle?: string;
  userRequirement?: string;
  note?: string;
}

/**
 * 用户已发送回复请求。
 */
export interface UserReplySentPayload {
  userReplyText?: string;
}

/**
 * 更新沟通状态请求。
 */
export interface CommunicationStatusUpdatePayload {
  communicationStatus: string;
  interviewTime?: string;
  nextFollowTime?: string;
  note?: string;
}
/**
 * 面试邀约信息提取结果。
 */
export interface InterviewInviteExtractInfo {
  interviewInvited: boolean;
  interviewTime?: string;
  dateText?: string;
  timeText?: string;
  interviewMethod?: string;
  interviewMethodDesc?: string;
  interviewLocation?: string;
  interviewPlatform?: string;
  meetingLink?: string;
  interviewContact?: string;
  needUserConfirm?: boolean;
  confirmQuestion?: string;
  confidence?: number;
  reason?: string;
}

/**
 * 面试邀约提取请求。
 */
export interface InterviewInviteExtractPayload {
  hrReply?: string;
}

/**
 * 面试邀约确认保存请求。
 */
export interface InterviewInviteConfirmPayload {
  interviewTime?: string;
  interviewMethod?: string;
  interviewLocation?: string;
  interviewPlatform?: string;
  meetingLink?: string;
  interviewContact?: string;
  nextFollowTime?: string;
  note?: string;
}

/**
 * HR 回复识别结果。
 *
 * 说明：
 * AI 只负责识别和建议，真正更新业务数据必须由用户点击确认。
 */
export interface HrReplyRecognitionInfo {
  id: number;
  applicationId?: number;
  communicationId?: number;
  jobId?: number;
  resumeId?: number;
  companyName?: string;
  jobTitle?: string;
  currentStatus?: string;
  hrReplyText: string;
  intentType?: string;
  intentTypeDesc?: string;
  confidence?: number;
  suggestedStatus?: string;
  suggestedStatusDesc?: string;
  communicationStatus?: string;
  interviewTime?: string;
  nextFollowTime?: string;
  todoItems?: string[];
  replySuggestion?: string;
  reason?: string;
  recognitionJson?: string;
  confirmStatus?: string;
  executedActionsJson?: string;
  errorMsg?: string;
  defaultActions?: Record<string, boolean>;
  createTime?: string;
  updateTime?: string;
}

/**
 * HR 回复识别请求。
 */
export interface HrReplyRecognizePayload {
  hrReplyText: string;
  userNote?: string;
}

/**
 * HR 回复识别确认请求。
 */
export interface HrReplyRecognitionConfirmPayload {
  saveCommunication?: boolean;
  updateApplicationStatus?: boolean;
  createReminder?: boolean;
  generateInterviewPrepare?: boolean;
  suggestedStatus?: string;
  interviewTime?: string;
  nextFollowTime?: string;
  note?: string;
}

/**
 * 求职提醒信息。
 */
export interface JobReminderInfo {
  id: number;
  applicationId?: number;
  communicationId?: number;
  resumeId?: number;
  resumeName?: string;
  jobId?: number;
  jobTitle?: string;
  companyId?: number;
  companyName?: string;
  reminderType: string;
  reminderTypeDesc?: string;
  reminderTitle: string;
  reminderContent?: string;
  eventTime?: string;
  remindTime: string;
  advanceMinutes?: number;
  reminderStatus: string;
  reminderStatusDesc?: string;
  isRead?: number;
  overdue?: boolean;
  minutesLeft?: number;
  doneTime?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 提醒分页结果。
 */
export interface ReminderPageResult {
  records: JobReminderInfo[];
  total: number;
  pageNo: number;
  pageSize: number;
}

/**
 * 提醒统计。
 */
export interface ReminderStatsInfo {
  pendingCount: number;
  dueCount: number;
  todayCount: number;
  interviewCount: number;
  followUpCount: number;
  unreadCount: number;
}

/**
 * 延期提醒参数。
 */
export interface ReminderPostponePayload {
  remindTime: string;
  eventTime?: string;
}

export interface FrontFollowUpActionInfo {
  actionCode: string;
  title: string;
  description: string;
  buttonText: string;
  priority: string;
  targetPath: string;
}

export interface FrontFollowUpApplicationInfo {
  application: JobApplicationInfo;
  pendingReminders: JobReminderInfo[];
  suggestedActions: FrontFollowUpActionInfo[];
  priority: string;
  priorityReason?: string;
}

export interface FrontFollowUpCenterInfo {
  applicationStats: JobApplicationStatsInfo;
  reminderStats: ReminderStatsInfo;
  applications: FrontFollowUpApplicationInfo[];
}
