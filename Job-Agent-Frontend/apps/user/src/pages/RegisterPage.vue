<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { uploadAvatar } from "../api/file";
import type { RegisterPayload } from "../api/types";
import { useAuthStore } from "../stores/auth";

const router = useRouter();
const authStore = useAuthStore();
const errorMessage = ref("");
const successMessage = ref("");
const avatarPreview = ref("");
const avatarName = ref("");
const avatarUploading = ref(false);

const form = reactive<RegisterPayload>({
  username: "",
  password: "",
  nickname: "",
  realName: "",
  phone: "",
  email: "",
  avatarUrl: "",
  gender: 0,
  education: "",
  workYears: undefined
});

async function changeAvatar(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];

  if (!file) {
    return;
  }
  if (!file.type.startsWith("image/")) {
    errorMessage.value = "头像只支持图片文件";
    input.value = "";
    return;
  }
  if (file.size > 2 * 1024 * 1024) {
    errorMessage.value = "头像文件不能超过2MB";
    input.value = "";
    return;
  }

  errorMessage.value = "";
  avatarUploading.value = true;
  avatarName.value = file.name;
  avatarPreview.value = URL.createObjectURL(file);

  try {
    const result = await uploadAvatar(file);
    form.avatarUrl = result.url;
    avatarPreview.value = result.url;
  } catch (error) {
    form.avatarUrl = "";
    avatarPreview.value = "";
    avatarName.value = "";
    errorMessage.value = error instanceof Error ? error.message : "头像上传失败";
    input.value = "";
  } finally {
    avatarUploading.value = false;
  }
}

async function submit() {
  errorMessage.value = "";
  successMessage.value = "";
  if (avatarUploading.value) {
    errorMessage.value = "头像正在上传，请稍等";
    return;
  }
  try {
    await authStore.register(form);
    successMessage.value = "注册成功，请登录";
    setTimeout(() => router.push("/login"), 500);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "注册失败";
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card wide">
      <p class="eyebrow">创建账号</p>
      <h1>注册求职账号</h1>
      <p class="auth-desc">注册后可以维护资料、上传简历，并使用 AI 求职助手。</p>

      <form class="form-grid" @submit.prevent="submit">
        <div class="avatar-upload full-line">
          <div class="avatar-preview">
            <img v-if="avatarPreview" :src="avatarPreview" alt="头像预览" />
            <span v-else>头像</span>
          </div>
          <div class="avatar-upload-main">
            <strong>上传头像</strong>
            <p>支持 JPG、PNG、WEBP、GIF，最大 2MB。可不上传，后续也能在资料页修改。</p>
            <label class="upload-button">
              <input type="file" accept="image/*" @change="changeAvatar" />
              {{ avatarUploading ? "上传中..." : "选择头像" }}
            </label>
            <small v-if="avatarName">{{ avatarName }}</small>
          </div>
        </div>

        <label>
          <span>用户名</span>
          <input v-model="form.username" required minlength="4" maxlength="32" placeholder="test001" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="form.password" required minlength="6" maxlength="32" type="password" placeholder="至少 6 位" />
        </label>
        <label>
          <span>昵称</span>
          <input v-model="form.nickname" maxlength="64" placeholder="测试用户" />
        </label>
        <label>
          <span>真实姓名</span>
          <input v-model="form.realName" maxlength="64" placeholder="黄锋杰" />
        </label>
        <label>
          <span>手机号</span>
          <input v-model="form.phone" placeholder="13800000000" />
        </label>
        <label>
          <span>邮箱</span>
          <input v-model="form.email" type="email" placeholder="test001@example.com" />
        </label>
        <label>
          <span>性别</span>
          <select v-model.number="form.gender">
            <option :value="0">未知</option>
            <option :value="1">男</option>
            <option :value="2">女</option>
          </select>
        </label>
        <label>
          <span>教育经历</span>
          <select v-model="form.education">
            <option value="">请选择</option>
            <option value="高中及以下">高中及以下</option>
            <option value="大专">大专</option>
            <option value="本科">本科</option>
            <option value="硕士">硕士</option>
            <option value="博士">博士</option>
          </select>
        </label>
        <label class="full-line">
          <span>工作年限</span>
          <input v-model.number="form.workYears" min="0" max="60" step="0.5" type="number" placeholder="例如 1.5" />
        </label>

        <p v-if="errorMessage" class="form-error full-line">{{ errorMessage }}</p>
        <p v-if="successMessage" class="form-success full-line">{{ successMessage }}</p>
        <button class="primary-button large full-line" :disabled="authStore.loading || avatarUploading">
          {{ avatarUploading ? "头像上传中..." : "注册" }}
        </button>
      </form>

      <p class="auth-switch">已有账号？<RouterLink to="/login">去登录</RouterLink></p>
    </section>
  </main>
</template>
