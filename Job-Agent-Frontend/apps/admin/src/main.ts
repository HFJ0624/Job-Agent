import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import App from "./App.vue";
import router from "./router";
import "./styles.css";

// 管理后台使用 Element Plus，整体布局参考 pure-admin 的后台风格。
createApp(App).use(createPinia()).use(router).use(ElementPlus).mount("#app");
