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
  build: {
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ["vue", "vue-router", "pinia"],
          element: ["element-plus", "@element-plus/icons-vue"]
        }
      }
    }
  },
  server: {
    port: 5174,
    proxy: {
      // 管理后台开发环境把 /admin 转发到后端 8500 端口。
      "/admin": {
        target: "http://localhost:8500",
        changeOrigin: true
      }
    }
  }
});
