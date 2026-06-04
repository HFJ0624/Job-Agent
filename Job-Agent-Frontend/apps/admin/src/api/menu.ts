import type { AdminMenuItem, AdminUserProfile } from "../types/menu";

const mockProfiles: Record<AdminUserProfile["role"], AdminUserProfile> = {
  admin: { id: 1, name: "超级管理员", role: "admin" },
  operator: { id: 2, name: "运营管理员", role: "operator" }
};

// 这里模拟后端返回的动态菜单。真实接后端时，只需要把这个数组换成接口返回值。
const allMenus: AdminMenuItem[] = [
  {
    id: 1,
    path: "/dashboard",
    name: "Dashboard",
    title: "数据看板",
    icon: "DataBoard",
    component: "dashboard/Workbench",
    roles: ["admin", "operator"]
  },
  {
    id: 2,
    path: "/user",
    name: "UserRoot",
    title: "用户管理",
    icon: "User",
    roles: ["admin"],
    children: [
      {
        id: 21,
        path: "/user/list",
        name: "UserList",
        title: "用户列表",
        icon: "UserFilled",
        component: "users/UserList",
        roles: ["admin"]
      }
    ]
  },
  {
    id: 3,
    path: "/job",
    name: "JobRoot",
    title: "岗位管理",
    icon: "Briefcase",
    roles: ["admin", "operator"],
    children: [
      {
        id: 31,
        path: "/job/list",
        name: "JobList",
        title: "岗位列表",
        icon: "Tickets",
        component: "jobs/JobList",
        roles: ["admin", "operator"]
      },
      {
        id: 32,
        path: "/job/import",
        name: "JobImport",
        title: "岗位导入",
        icon: "Upload",
        component: "jobs/JobImport",
        roles: ["admin"]
      }
    ]
  },
  {
    id: 4,
    path: "/community",
    name: "Community",
    title: "社区管理",
    icon: "ChatLineRound",
    component: "community/PostManage",
    roles: ["admin", "operator"]
  },
  {
    id: 5,
    path: "/agent/logs",
    name: "AgentLogs",
    title: "Agent 日志",
    icon: "Connection",
    component: "agent/TraceLog",
    roles: ["admin", "operator"]
  },
  {
    id: 6,
    path: "/system",
    name: "SystemRoot",
    title: "系统配置",
    icon: "Setting",
    roles: ["admin"],
    children: [
      {
        id: 61,
        path: "/system/prompts",
        name: "PromptManage",
        title: "Prompt 管理",
        icon: "Document",
        component: "system/PromptManage",
        roles: ["admin"]
      },
      {
        id: 62,
        path: "/system/models",
        name: "ModelManage",
        title: "模型配置",
        icon: "Cpu",
        component: "system/ModelManage",
        roles: ["admin"]
      }
    ]
  }
];

function hasRole(menu: AdminMenuItem, role: AdminUserProfile["role"]) {
  return !menu.roles || menu.roles.includes(role);
}

function filterMenusByRole(menus: AdminMenuItem[], role: AdminUserProfile["role"]): AdminMenuItem[] {
  return menus
    .filter(menu => hasRole(menu, role))
    .map(menu => ({
      ...menu,
      children: menu.children ? filterMenusByRole(menu.children, role) : undefined
    }))
    .filter(menu => !menu.children || menu.children.length > 0);
}

export async function fetchAdminProfile(role: AdminUserProfile["role"]) {
  // 用 Promise 模拟接口异步返回，方便后面替换为真实 request 方法。
  return Promise.resolve(mockProfiles[role]);
}

export async function fetchAdminMenus(role: AdminUserProfile["role"]) {
  return Promise.resolve(filterMenusByRole(allMenus, role));
}
