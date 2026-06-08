<template>
  <main class="page-section">
    <button class="text-button back-button" type="button" @click="router.push('/jobs')">返回职位列表</button>

    <p v-if="loading" class="empty-state">正在加载岗位详情...</p>
    <p v-else-if="errorMessage" class="form-error">{{ errorMessage }}</p>

    <section v-else-if="detail && position" class="job-detail-layout">
      <div class="job-detail-main">
        <article class="job-detail-card">
          <div class="job-detail-top">
            <div>
              <p class="eyebrow">职位详情</p>
              <h1>{{ position.jobTitle }}</h1>
              <div class="job-summary-row">
                <span>{{ formatPlace(position.city, position.district) }}</span>
                <span>{{ position.experienceReq || "经验不限" }}</span>
                <span>{{ position.educationReq || "学历不限" }}</span>
                <span>{{ position.workType || "全职" }}</span>
              </div>
            </div>
            <strong class="detail-salary">{{ formatSalary(position) }}</strong>
          </div>

          <div class="detail-tags">
            <span v-for="tag in tags" :key="tag">{{ tag }}</span>
          </div>

          <section class="detail-section">
            <h2>职位描述</h2>
            <pre class="detail-text">{{ position.jobDescription || "该岗位暂未填写详细职位描述。" }}</pre>
          </section>

          <section class="detail-section">
            <h2>任职要求</h2>
            <pre class="detail-text">{{ position.jobRequirement || "该岗位暂未填写任职要求。" }}</pre>
          </section>

          <section class="detail-section">
            <h2>福利待遇</h2>
            <p class="detail-text-inline">{{ position.welfareTags || "福利待遇待 HR 进一步补充。" }}</p>
          </section>
        </article>

         <!-- 加在这里：岗位匹配分析面板 -->
          <JobMatchPanel
            v-if="position?.id"
            :job-id="String(position.id)"
          />

        <section class="company-detail-card">
          <div class="company-title-row">
            <div class="company-logo">
              <img v-if="company?.logoUrl" :src="company.logoUrl" alt="公司 Logo" />
              <span v-else>{{ companyInitial }}</span>
            </div>
            <div>
              <p class="eyebrow">公司信息</p>
              <h2>{{ company?.companyName || position.companyName || "未命名公司" }}</h2>
              <p>{{ formatCompanyMeta }}</p>
            </div>
          </div>

          <p class="company-description">{{ company?.description || "公司简介暂未填写，后续可在后台公司管理中补充。" }}</p>

          <div class="company-info-grid">
            <div>
              <span>所在地区</span>
              <strong>{{ formatPlace(company?.city || position.city, company?.district || position.district) }}</strong>
            </div>
            <div>
              <span>详细地址</span>
              <strong>{{ company?.address || "公司地址待补充" }}</strong>
            </div>
            <div>
              <span>融资阶段</span>
              <strong>{{ company?.financingStage || "未填写" }}</strong>
            </div>
            <div>
              <span>发展前景</span>
              <strong>{{ company?.prospectScore ? `${company.prospectScore} 分` : "暂无评分" }}</strong>
            </div>
          </div>
        </section>
      </div>

      <aside class="job-detail-side">
        <section class="hr-panel">
          <div class="hr-profile">
            <div class="hr-avatar">HR</div>
            <div>
              <h3>{{ company?.companyName || position.companyName || "招聘方" }} HR</h3>
              <p>今日活跃，可先发送沟通意向。</p>
            </div>
          </div>

          <div class="detail-action-grid">
            <button class="secondary-button detail-icon-button" type="button" :disabled="favoriteLoading" @click="handleFavorite">
              <el-icon>
                <StarFilled v-if="detail.favorited" />
                <Star v-else />
              </el-icon>
              {{ detail.favorited ? "已收藏" : "收藏" }}
            </button>
            <button class="primary-button detail-icon-button" type="button" :disabled="messageLoading" @click="handleCommunicate">
              <el-icon><ChatDotRound /></el-icon>
              立即沟通
            </button>
          </div>

          <p class="favorite-count">已有 {{ detail.favoriteCount }} 人收藏该岗位</p>

          <!-- 新增：HR 打招呼语生成组件 -->
          <JobGreetingPanel
            v-if="position?.id"
            :job-id="String(position.id)"
            @use="messageContent = $event"
          />

          <label class="message-field">
            <span>沟通消息</span>
            <textarea
              v-model.trim="messageContent"
              maxlength="500"
              placeholder="可以为空，系统会自动生成一句招呼语。"
            />
          </label>

          <div v-if="latestMessage" class="message-result">
            <b>已发送给 {{ latestMessage.receiverName }}</b>
            <p>{{ latestMessage.content }}</p>
          </div>
        </section>

        <section class="detail-side-card">
          <p class="eyebrow">岗位来源</p>
          <h3>{{ position.source || "平台发布" }}</h3>
          <p>发布时间：{{ position.publishTime || "暂无" }}</p>
          <a v-if="position.sourceUrl" :href="position.sourceUrl" target="_blank" rel="noreferrer">查看来源链接</a>
        </section>
      </aside>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ChatDotRound, Star, StarFilled } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { communicateWithHr, getFrontPositionDetail, toggleJobFavorite } from "../api/job";
import type { JobMessageInfo, PositionDetailInfo, PositionInfo } from "../api/types";
import { useAuthStore } from "../stores/auth";
import JobMatchPanel from "../components/JobMatchPanel.vue";
import JobGreetingPanel from "../components/JobGreetingPanel.vue";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const detail = ref<PositionDetailInfo | null>(null);
const latestMessage = ref<JobMessageInfo | null>(null);
const loading = ref(false);
const favoriteLoading = ref(false);
const messageLoading = ref(false);
const errorMessage = ref("");
const messageContent = ref("");

const positionId = computed(() => String(route.params.id || ""));
const position = computed(() => detail.value?.position);
const company = computed(() => detail.value?.company);
const companyInitial = computed(() => (company.value?.companyName || position.value?.companyName || "企").slice(0, 1));
const formatCompanyMeta = computed(() =>
  [company.value?.industry, company.value?.companySize, company.value?.financingStage].filter(Boolean).join(" · ") ||
  "公司信息待补充"
);
const tags = computed(() => {
  if (!position.value) {
    return [];
  }
  // 1. 数据库里技能和福利使用逗号保存，详情页拆成标签，用户扫一眼就能看到关键词。
  return [
    position.value.jobCategory,
    position.value.workType,
    ...splitTags(position.value.skillKeywords),
    ...splitTags(position.value.welfareTags)
  ]
    .filter(Boolean)
    .slice(0, 12) as string[];
});

async function loadDetail() {
  if (!positionId.value) {
    errorMessage.value = "岗位ID不存在";
    return;
  }

  loading.value = true;
  errorMessage.value = "";
  try {
    // 1. 详情接口一次返回岗位、公司和收藏状态，避免页面分多次请求造成闪烁。
    detail.value = await getFrontPositionDetail(positionId.value);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "岗位详情加载失败";
  } finally {
    loading.value = false;
  }
}

function ensureLogin() {
  if (authStore.isLogin) {
    return true;
  }
  // 1. 收藏和立即沟通必须登录，带 redirect 回来，登录后继续看当前岗位详情。
  router.push({ path: "/login", query: { redirect: route.fullPath } });
  return false;
}

async function handleFavorite() {
  if (!detail.value || !ensureLogin()) {
    return;
  }

  favoriteLoading.value = true;
  try {
    const state = await toggleJobFavorite(positionId.value);
    detail.value.favorited = state.favorited;
    detail.value.favoriteCount = state.favoriteCount;
    ElMessage.success(state.favorited ? "岗位已收藏" : "已取消收藏");
  } catch (error) {
    if (error instanceof Error && error.message.includes("请先登录")) {
      ensureLogin();
      return;
    }
    ElMessage.error(error instanceof Error ? error.message : "收藏操作失败");
  } finally {
    favoriteLoading.value = false;
  }
}

async function handleCommunicate() {
  if (!detail.value || !ensureLogin()) {
    return;
  }

  messageLoading.value = true;
  try {
    // 1. 消息内容允许为空，后端会自动生成“我对该岗位感兴趣”的默认招呼语。
    latestMessage.value = await communicateWithHr(positionId.value, {
      content: messageContent.value || undefined
    });
    messageContent.value = "";
    ElMessage.success("已向 HR 发送沟通消息");
  } catch (error) {
    if (error instanceof Error && error.message.includes("请先登录")) {
      ensureLogin();
      return;
    }
    ElMessage.error(error instanceof Error ? error.message : "沟通消息发送失败");
  } finally {
    messageLoading.value = false;
  }
}

function formatPlace(city?: string, district?: string) {
  return [city, district].filter(Boolean).join(" · ") || "地点待补充";
}

function formatSalary(job: PositionInfo) {
  if (!job.minSalary && !job.maxSalary) {
    return "薪资面议";
  }
  const min = job.minSalary ? Math.round(job.minSalary / 1000) : 0;
  const max = job.maxSalary ? Math.round(job.maxSalary / 1000) : 0;
  const months = job.salaryMonths && job.salaryMonths > 12 ? ` · ${job.salaryMonths}薪` : "";
  if (min && max) return `${min}-${max}K${months}`;
  if (min) return `${min}K起${months}`;
  return `${max}K以内${months}`;
}

function splitTags(value?: string) {
  if (!value) {
    return [];
  }
  return value
    .split(/[,，、]/)
    .map(item => item.trim())
    .filter(Boolean);
}

onMounted(loadDetail);
</script>
