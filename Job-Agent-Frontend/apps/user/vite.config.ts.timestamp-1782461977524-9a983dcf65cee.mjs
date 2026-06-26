// vite.config.ts
import { fileURLToPath, URL } from "node:url";
import vue from "file:///D:/workspace/Job-Agent/Job-Agent-Frontend/node_modules/@vitejs/plugin-vue/dist/index.mjs";
import { defineConfig } from "file:///D:/workspace/Job-Agent/Job-Agent-Frontend/node_modules/vite/dist/node/index.js";
var __vite_injected_original_import_meta_url = "file:///D:/workspace/Job-Agent/Job-Agent-Frontend/apps/user/vite.config.ts";
var vite_config_default = defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", __vite_injected_original_import_meta_url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      // 用户前台开发环境把 /front 转发到后端，避免浏览器跨域问题。
      "/front": {
        target: "http://localhost:8500",
        changeOrigin: true
      }
    }
  }
});
export {
  vite_config_default as default
};
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcudHMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCJEOlxcXFx3b3Jrc3BhY2VcXFxcSm9iLUFnZW50XFxcXEpvYi1BZ2VudC1Gcm9udGVuZFxcXFxhcHBzXFxcXHVzZXJcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfZmlsZW5hbWUgPSBcIkQ6XFxcXHdvcmtzcGFjZVxcXFxKb2ItQWdlbnRcXFxcSm9iLUFnZW50LUZyb250ZW5kXFxcXGFwcHNcXFxcdXNlclxcXFx2aXRlLmNvbmZpZy50c1wiO2NvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9pbXBvcnRfbWV0YV91cmwgPSBcImZpbGU6Ly8vRDovd29ya3NwYWNlL0pvYi1BZ2VudC9Kb2ItQWdlbnQtRnJvbnRlbmQvYXBwcy91c2VyL3ZpdGUuY29uZmlnLnRzXCI7aW1wb3J0IHsgZmlsZVVSTFRvUGF0aCwgVVJMIH0gZnJvbSBcIm5vZGU6dXJsXCI7XG5pbXBvcnQgdnVlIGZyb20gXCJAdml0ZWpzL3BsdWdpbi12dWVcIjtcbmltcG9ydCB7IGRlZmluZUNvbmZpZyB9IGZyb20gXCJ2aXRlXCI7XG5cbmV4cG9ydCBkZWZhdWx0IGRlZmluZUNvbmZpZyh7XG4gIHBsdWdpbnM6IFt2dWUoKV0sXG4gIHJlc29sdmU6IHtcbiAgICBhbGlhczoge1xuICAgICAgXCJAXCI6IGZpbGVVUkxUb1BhdGgobmV3IFVSTChcIi4vc3JjXCIsIGltcG9ydC5tZXRhLnVybCkpXG4gICAgfVxuICB9LFxuICBzZXJ2ZXI6IHtcbiAgICBwb3J0OiA1MTczLFxuICAgIHByb3h5OiB7XG4gICAgICAvLyBcdTc1MjhcdTYyMzdcdTUyNERcdTUzRjBcdTVGMDBcdTUzRDFcdTczQUZcdTU4ODNcdTYyOEEgL2Zyb250IFx1OEY2Q1x1NTNEMVx1NTIzMFx1NTQwRVx1N0FFRlx1RkYwQ1x1OTA3Rlx1NTE0RFx1NkQ0Rlx1ODlDOFx1NTY2OFx1OERFOFx1NTdERlx1OTVFRVx1OTg5OFx1MzAwMlxuICAgICAgXCIvZnJvbnRcIjoge1xuICAgICAgICB0YXJnZXQ6IFwiaHR0cDovL2xvY2FsaG9zdDo4NTAwXCIsXG4gICAgICAgIGNoYW5nZU9yaWdpbjogdHJ1ZVxuICAgICAgfVxuICAgIH1cbiAgfVxufSk7XG4iXSwKICAibWFwcGluZ3MiOiAiO0FBQXVWLFNBQVMsZUFBZSxXQUFXO0FBQzFYLE9BQU8sU0FBUztBQUNoQixTQUFTLG9CQUFvQjtBQUY0TCxJQUFNLDJDQUEyQztBQUkxUSxJQUFPLHNCQUFRLGFBQWE7QUFBQSxFQUMxQixTQUFTLENBQUMsSUFBSSxDQUFDO0FBQUEsRUFDZixTQUFTO0FBQUEsSUFDUCxPQUFPO0FBQUEsTUFDTCxLQUFLLGNBQWMsSUFBSSxJQUFJLFNBQVMsd0NBQWUsQ0FBQztBQUFBLElBQ3REO0FBQUEsRUFDRjtBQUFBLEVBQ0EsUUFBUTtBQUFBLElBQ04sTUFBTTtBQUFBLElBQ04sT0FBTztBQUFBO0FBQUEsTUFFTCxVQUFVO0FBQUEsUUFDUixRQUFRO0FBQUEsUUFDUixjQUFjO0FBQUEsTUFDaEI7QUFBQSxJQUNGO0FBQUEsRUFDRjtBQUNGLENBQUM7IiwKICAibmFtZXMiOiBbXQp9Cg==
