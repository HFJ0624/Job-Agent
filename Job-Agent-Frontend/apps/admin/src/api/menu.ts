import type { AdminMenuItem } from "../types/menu";

/**
 * 后台固定菜单。
 * P表示参数描述，这里不再按角色动态请求菜单，侧边栏和路由都直接使用这份静态配置。
 */
export const adminMenus: AdminMenuItem[] = [
  {
    id: 1,
    path: "/dashboard",
    name: "Dashboard",
    title: "数据看板",
    icon: "DataBoard",
    component: "dashboard/Workbench"
  },
  {
    id: 2,
    path: "/user",
    name: "UserRoot",
    title: "用户管理",
    icon: "User",
    children: [
      {
        id: 21,
        path: "/user/list",
        name: "UserList",
        title: "用户列表",
        icon: "UserFilled",
        component: "users/UserList"
      }
    ]
  },
  {
    id: 3,
    path: "/job",
    name: "JobRoot",
    title: "岗位管理",
    icon: "Briefcase",
    children: [
      {
        id: 31,
        path: "/job/list",
        name: "JobList",
        title: "岗位列表",
        icon: "Tickets",
        component: "jobs/JobList"
      },
      {
        id: 32,
        path: "/job/import",
        name: "JobImport",
        title: "岗位导入",
        icon: "Upload",
        component: "jobs/JobImport"
      }
    ]
  },
  {
    id: 4,
    path: "/community",
    name: "Community",
    title: "社区管理",
    icon: "ChatLineRound",
    component: "community/PostManage"
  },
  {
    id: 5,
    path: "/agent/logs",
    name: "AgentLogs",
    title: "Agent 日志",
    icon: "Connection",
    component: "agent/TraceLog"
  },
  {
    id: 6,
    path: "/system",
    name: "SystemRoot",
    title: "系统配置",
    icon: "Setting",
    children: [
      {
        id: 61,
        path: "/system/prompts",
        name: "PromptManage",
        title: "Prompt 管理",
        icon: "Document",
        component: "system/PromptManage"
      },
      {
        id: 62,
        path: "/system/models",
        name: "ModelManage",
        title: "模型配置",
        icon: "Cpu",
        component: "system/ModelManage"
      }
    ]
  }
];
