import { request } from "./request";
import type { SaveUserAddressPayload, UserAddressInfo } from "./types";

/**
 * 查询当前登录用户的默认家庭地址。
 */
export function getDefaultAddress() {
  return request<UserAddressInfo | null>("/api/user/address/default");
}

/**
 * 保存当前登录用户的默认家庭地址。
 * P表示参数描述，手动填写和高德地图选择后的地址都走这个接口。
 */
export function saveDefaultAddress(payload: SaveUserAddressPayload) {
  return request<UserAddressInfo>("/api/user/address/default", {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}
