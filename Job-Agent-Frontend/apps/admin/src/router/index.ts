import type { RouteRecordRaw } from "vue-router";
import { createRouter, createWebHistory } from "vue-router";
import { useAdminMenuStore } from "../stores/menu";
import { useAdminUserStore } from "../stores/user";
import type { AdminMenuItem } from "../types/menu";

const AdminLayout = () => import("../layout/AdminLayout.vue");
const LoginView = () => import("../views/login/LoginView.vue");
const NotFoundView = () => import("../views/NotFoundView.vue");

// Vite 会把 views 下的页面编译成懒加载模块，菜单只需要提供 component 字段。
const pageModules = import.meta.glob("../views/**/*.vue");

const staticRoutes: RouteRecordRaw[] = [
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
    children: []
  },
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    component: NotFoundView
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes
});

function trimLeadingSlash(path: string) {
  return path.replace(/^\//, "");
}

function resolvePage(component?: string) {
  if (!component) return undefined;
  return pageModules[`../views/${component}.vue`] as RouteRecordRaw["component"];
}

function flattenMenuRoutes(menus: AdminMenuItem[]) {
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
      routes.push(...flattenMenuRoutes(menu.children));
    }
  });

  return routes;
}

function registerDynamicRoutes(menus: AdminMenuItem[]) {
  flattenMenuRoutes(menus).forEach(route => {
    // hasRoute 可以避免刷新或重复进入时反复注册同名路由。
    if (route.name && !router.hasRoute(route.name)) {
      router.addRoute("RootLayout", route);
    }
  });
}

router.beforeEach(async to => {
  const userStore = useAdminUserStore();
  const menuStore = useAdminMenuStore();

  if (to.path === "/login") {
    return true;
  }

  if (!userStore.isLogin) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }

  if (!userStore.profile) {
    try {
      await userStore.loadProfile();
    } catch {
      await userStore.logout();
      menuStore.reset();
      return { path: "/login", query: { redirect: to.fullPath } };
    }
  }

  if (!menuStore.loaded) {
    await menuStore.loadMenus(userStore.role);
    registerDynamicRoutes(menuStore.menus);

    // 菜单路由刚注册完，需要只按 path 重新匹配一次。
    // 不能把旧的 route name 带回去，否则首次进入 /dashboard 时可能仍然命中 NotFound。
    return { path: to.fullPath, replace: true };
  }

  return true;
});

export default router;
