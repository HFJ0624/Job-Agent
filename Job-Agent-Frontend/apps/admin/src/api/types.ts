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

export interface PageResult<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
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
