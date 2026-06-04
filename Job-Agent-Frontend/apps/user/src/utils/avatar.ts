/**
 * 将用户头像地址转换成前端可以展示的地址。
 *
 * 说明：登录后用户信息里已经保存了 avatarUrl，前端直接取这个值展示头像。
 */
export function normalizeAvatarUrl(avatarUrl?: string) {
  return avatarUrl || "";
}

/**
 * 没有头像时显示名字首字。
 */
export function avatarInitial(name?: string) {
  return (name || "U").trim().slice(0, 1).toUpperCase();
}
