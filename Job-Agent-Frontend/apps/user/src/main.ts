import { createApp } from "vue";
import { createPinia } from "pinia";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import HomePage from "./pages/HomePage.vue";
import JobsPage from "./pages/JobsPage.vue";
import ResumePage from "./pages/ResumePage.vue";
import AgentPage from "./pages/AgentPage.vue";
import LoginPage from "./pages/LoginPage.vue";
import RegisterPage from "./pages/RegisterPage.vue";
import ProfilePage from "./pages/ProfilePage.vue";
import "./styles.css";

// 用户端先用前端静态路由，后续接后端时只需要把页面里的 mock 数据替换成接口。
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/home" },
    { path: "/home", name: "home", component: HomePage },
    { path: "/jobs", name: "jobs", component: JobsPage },
    { path: "/resume", name: "resume", component: ResumePage },
    { path: "/agent", name: "agent", component: AgentPage },
    { path: "/login", name: "login", component: LoginPage },
    { path: "/register", name: "register", component: RegisterPage },
    { path: "/profile", name: "profile", component: ProfilePage }
  ],
  scrollBehavior() {
    return { top: 0 };
  }
});

createApp(App).use(createPinia()).use(router).mount("#app");
