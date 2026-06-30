import { createApp } from "vue";
import { createPinia } from "pinia";
import { createRouter, createWebHistory } from "vue-router";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import App from "./App.vue";
import HomePage from "./pages/HomePage.vue";
import JobsPage from "./pages/JobsPage.vue";
import JobDetailPage from "./pages/JobDetailPage.vue";
import ResumePage from "./pages/ResumePage.vue";
import AgentPage from "./pages/AgentPage.vue";
import LoginPage from "./pages/LoginPage.vue";
import RegisterPage from "./pages/RegisterPage.vue";
import ProfilePage from "./pages/ProfilePage.vue";
import JobRecommendPage from "./pages/JobRecommendPage.vue";
import ApplicationPage from "./pages/ApplicationPage.vue";
import FollowUpCenterPage from "./pages/FollowUpCenterPage.vue";
import CommunicationPage from "./pages/CommunicationPage.vue";
import AiInterviewPage from "./pages/AiInterviewPage.vue";
import WrongQuestionPage from "./pages/WrongQuestionPage.vue";
import LearningPlanPage from "./pages/LearningPlanPage.vue";
import AgentInboxPage from "./pages/AgentInboxPage.vue";
import AgentActionCenterPage from "./pages/AgentActionCenterPage.vue";
import "./styles.css";

// 用户端先用前端静态路由，后续接后端时只需要把页面里的 mock 数据替换成接口。
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/home" },
    { path: "/home", name: "home", component: HomePage },
    { path: "/jobs", name: "jobs", component: JobsPage },
    { path: "/jobs/:id", name: "job-detail", component: JobDetailPage },
    { path: "/resume", name: "resume", component: ResumePage },
    { path: "/agent", name: "agent", component: AgentPage },
    { path: "/login", name: "login", component: LoginPage },
    { path: "/register", name: "register", component: RegisterPage },
    { path: "/profile", name: "profile", component: ProfilePage },
    { path: "/job-recommend", name: "job-recommend", component: JobRecommendPage },
    { path: "/follow-up", name: "follow-up", component: FollowUpCenterPage },
    { path: "/application", name: "application", component: ApplicationPage },
    { path: "/communication", name: "communication", component: CommunicationPage },
    { path: "/ai-interview", name: "ai-interview", component: AiInterviewPage },
    { path: "/agent-inbox", name: "agent-inbox", component: AgentInboxPage },
    { path: "/agent-actions", name: "agent-actions", component: AgentActionCenterPage },
    { path: "/wrong-questions", name: "wrong-questions", component: WrongQuestionPage },
    { path: "/learning-plan", name: "learning-plan", component: LearningPlanPage },
  ],
  scrollBehavior() {
    return { top: 0 };
  }
});

// 用户端也接入 Element Plus，简历预览抽屉、确认弹窗等交互可以直接使用成熟组件。
createApp(App).use(createPinia()).use(router).use(ElementPlus).mount("#app");
