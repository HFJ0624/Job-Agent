<template>
  <main class="page-section">
    <section v-if="!authStore.isLogin" class="resume-board">
      <div>
        <p class="eyebrow">个人中心</p>
        <h1>请先登录</h1>
        <p>登录后可以查看和修改当前用户资料。</p>
      </div>
      <RouterLink class="primary-button large" to="/login?redirect=/profile">去登录</RouterLink>
    </section>

    <section v-else class="profile-card">
      <div class="section-heading">
        <div>
          <p class="eyebrow">个人中心</p>
          <h1>我的资料</h1>
        </div>
        <span class="match-score">账号：{{ authStore.user?.username }}</span>
      </div>

      <form class="form-grid" @submit.prevent="submit">
        <div class="profile-avatar-panel full-line">
          <button class="profile-avatar-button" type="button" @click="openAvatarPicker">
            <img v-if="avatarPreviewUrl" :src="avatarPreviewUrl" alt="用户头像" />
            <span v-else>{{ avatarText }}</span>
            <small>{{ avatarUploading ? "上传中..." : "点击修改头像" }}</small>
          </button>
          <input ref="avatarInput" class="hidden-file-input" type="file" accept="image/*" @change="changeAvatar" />
          <div>
            <h3>{{ authStore.displayName }}</h3>
            <p>点击头像即可重新选择图片，上传成功后会自动保存到 MinIO 和个人资料。</p>
          </div>
        </div>

        <label>
          <span>昵称</span>
          <input v-model="form.nickname" placeholder="新昵称" />
        </label>
        <label>
          <span>真实姓名</span>
          <input v-model="form.realName" placeholder="张三" />
        </label>
        <label>
          <span>手机号</span>
          <input v-model="form.phone" placeholder="13800000001" />
        </label>
        <label>
          <span>邮箱</span>
          <input v-model="form.email" type="email" placeholder="new@example.com" />
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
          <span>学历</span>
          <input v-model="form.education" placeholder="本科" />
        </label>
        <label>
          <span>工作年限</span>
          <input v-model.number="form.workYears" min="0" step="0.5" type="number" />
        </label>

        <p v-if="errorMessage" class="form-error full-line">{{ errorMessage }}</p>
        <p v-if="successMessage" class="form-success full-line">{{ successMessage }}</p>
        <button class="primary-button large full-line" :disabled="avatarUploading">保存资料</button>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { uploadAvatar } from "../api/file";
import { updateProfile } from "../api/user";
import { useAuthStore } from "../stores/auth";
import { avatarInitial, normalizeAvatarUrl } from "../utils/avatar";

const authStore = useAuthStore();
const errorMessage = ref("");
const successMessage = ref("");
const avatarInput = ref<HTMLInputElement | null>(null);
const avatarUploading = ref(false);
const localAvatarPreview = ref("");

const form = reactive({
  nickname: "",
  realName: "",
  phone: "",
  email: "",
  avatarUrl: "",
  gender: 0,
  education: "",
  workYears: 0
});

const avatarPreviewUrl = computed(() => localAvatarPreview.value || normalizeAvatarUrl(form.avatarUrl));
const avatarText = computed(() => avatarInitial(authStore.displayName));

function fillForm() {
  const user = authStore.user;
  if (!user) return;
  form.nickname = user.nickname || "";
  form.realName = user.realName || "";
  form.phone = user.phone || "";
  form.email = user.email || "";
  form.avatarUrl = user.avatarUrl || "";
  form.gender = user.gender || 0;
  form.education = user.education || "";
  form.workYears = Number(user.workYears || 0);
  localAvatarPreview.value = "";
}

onMounted(async () => {
  await authStore.loadMe();
  fillForm();
});

watch(() => authStore.user, fillForm);

function openAvatarPicker() {
  if (!avatarUploading.value) {
    avatarInput.value?.click();
  }
}

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
  successMessage.value = "";
  avatarUploading.value = true;
  localAvatarPreview.value = URL.createObjectURL(file);

  try {
    const result = await uploadAvatar(file);
    form.avatarUrl = result.url;
    localAvatarPreview.value = normalizeAvatarUrl(result.url);
    authStore.setUser(await updateProfile(form));
    successMessage.value = "头像已更新";
  } catch (error) {
    localAvatarPreview.value = "";
    errorMessage.value = error instanceof Error ? error.message : "头像上传失败";
  } finally {
    avatarUploading.value = false;
    input.value = "";
  }
}

async function submit() {
  errorMessage.value = "";
  successMessage.value = "";
  try {
    authStore.setUser(await updateProfile(form));
    successMessage.value = "资料已更新";
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "保存失败";
  }
}
</script>
