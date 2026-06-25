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
    path: "/company",
    name: "CompanyRoot",
    title: "公司管理",
    icon: "OfficeBuilding",
    children: [
      {
        id: 41,
        path: "/company/list",
        name: "CompanyList",
        title: "公司列表",
        icon: "OfficeBuilding",
        component: "company/CompanyList"
      },
      {
        id: 42,
        path: "/company/import",
        name: "CompanyImport",
        title: "公司导入",
        icon: "Upload",
        component: "company/CompanyImport"
      }
    ]
  },
  {
    id: 5,
    path: "/community",
    name: "Community",
    title: "社区管理",
    icon: "ChatLineRound",
    component: "community/PostManage"
  },
  {
    id: 6,
    path: "/agent/logs",
    name: "AgentLogs",
    title: "Agent 日志",
    icon: "Connection",
    component: "agent/TraceLog"
  },
  {
    id: 10,
    path: "/agent/observability",
    name: "AgentObservability",
    title: "Agent 观测",
    icon: "DataBoard",
    component: "agent/ObservationDashboard"
  },
  {
    id: 9,
    path: "/agent/plans",
    name: "AgentPlans",
    title: "Agent 计划",
    icon: "List",
    component: "agent/PlanLog"
  },
  {
    id: 11,
    path: "/agent/memories",
    name: "AgentMemories",
    title: "Agent 记忆",
    icon: "Collection",
    component: "agent/MemoryManage"
  },
  {
    id: 8,
    path: "/agent/rag",
    name: "AgentRag",
    title: "RAG 知识库",
    icon: "DataBoard",
    component: "agent/RagKnowledge"
  },
  {
    id: 12,
    path: "/agent/eval",
    name: "AgentEval",
    title: "Eval 平台",
    icon: "TrendCharts",
    component: "agent/EvalPlatform"
  },
  {
    id: 7,
    path: "/system",
    name: "SystemRoot",
    title: "系统配置",
    icon: "Setting",
    children: [
      {
        id: 71,
        path: "/system/prompts",
        name: "PromptManage",
        title: "Prompt 管理",
        icon: "Document",
        component: "system/PromptManage"
      },
      {
        id: 72,
        path: "/system/models",
        name: "ModelManage",
        title: "模型配置",
        icon: "Cpu",
        component: "system/ModelManage"
      }
    ]
  }
];
