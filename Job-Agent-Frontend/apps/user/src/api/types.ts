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
