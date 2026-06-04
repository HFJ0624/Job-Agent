import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      // 开发环境把 /api 转发到后端，避免浏览器跨域问题。
      "/api": {
        target: "http://localhost:8500",
        changeOrigin: true
      }
    }
  }
});
