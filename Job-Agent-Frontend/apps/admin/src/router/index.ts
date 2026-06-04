import type { RouteRecordRaw } from "vue-router";
import { createRouter, createWebHistory } from "vue-router";
import { adminMenus } from "../api/menu";
import { useAdminUserStore } from "../stores/user";
import type { AdminMenuItem } from "../types/menu";

const AdminLayout = () => import("../layout/AdminLayout.vue");
const LoginView = () => import("../views/login/LoginView.vue");
const NotFoundView = () => import("../views/NotFoundView.vue");

// Vite 会把 views 下的页面编译成懒加载模块，固定菜单只需要提供 component 字段。
const pageModules = import.meta.glob("../views/**/*.vue");

function trimLeadingSlash(path: string) {
  return path.replace(/^\//, "");
}

function resolvePage(component?: string) {
  if (!component) return undefined;
  return pageModules[`../views/${component}.vue`] as RouteRecordRaw["component"];
}

function flattenStaticRoutes(menus: AdminMenuItem[]) {
  const routes: RouteRecordRaw[] = [];

  menus.forEach(menu => {
    const page = resolvePage(menu.component);

    if (page) {
      routes.push({
        path: trimLeadingSlash(menu.path),
        name: menu.name,
        component: page,
        meta: {
          title: menu.title,
          icon: menu.icon,
          menuPath: menu.path
        }
      });
    }

    if (menu.children?.length) {
      routes.push(...flattenStaticRoutes(menu.children));
    }
  });

  return routes;
}

const routes: RouteRecordRaw[] = [
  {
    path: "/login",
    name: "Login",
    component: LoginView,
    meta: { title: "登录" }
  },
  {
    path: "/",
    name: "RootLayout",
    component: AdminLayout,
    redirect: "/dashboard",
    children: flattenStaticRoutes(adminMenus)
  },
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    component: NotFoundView
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async to => {
  const userStore = useAdminUserStore();

  if (to.path === "/login") {
    return true;
  }

  if (!userStore.isLogin) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }

  if (!userStore.profile) {
    try {
      // 刷新页面后 Pinia 会丢失内存里的用户信息，这里用后台 /admin/auth/me 补回来。
      await userStore.loadProfile();
    } catch {
      await userStore.logout();
      return { path: "/login", query: { redirect: to.fullPath } };
    }
  }

  return true;
});

export default router;
