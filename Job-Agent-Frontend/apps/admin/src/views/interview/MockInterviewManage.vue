<template>
  <main class="admin-page">
    <section class="page-header">
      <div>
        <p class="eyebrow">Mock Interview</p>
        <h1>模拟面试记录</h1>
        <p>查看用户 AI 面试会话、题目回答、音频文件和火山 ASR 识别结果。</p>
      </div>
      <el-button :loading="loading" @click="loadSessions">刷新</el-button>
    </section>

    <section class="table-card">
      <el-form :model="query" label-width="90px" class="filter-form">
        <el-row :gutter="12">
          <el-col :span="5">
            <el-form-item label="用户ID">
              <el-input v-model.trim="query.userId" clearable placeholder="用户ID" />
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="岗位ID">
              <el-input v-model.trim="query.jobId" clearable placeholder="岗位ID" />
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="状态">
              <el-select v-model="query.status" clearable placeholder="全部">
                <el-option label="进行中" value="IN_PROGRESS" />
                <el-option label="已完成" value="FINISHED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="关键词">
              <el-input v-model.trim="query.keyword" clearable placeholder="岗位/公司" />
            </el-form-item>
          </el-col>
          <el-col :span="3">
            <el-button type="primary" @click="search">查询</el-button>
          </el-col>
        </el-row>
      </el-form>

      <el-table v-loading="loading" :data="sessions" border stripe>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="jobTitle" label="岗位" min-width="180" />
        <el-table-column prop="companyName" label="公司" min-width="140" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="resumeId" label="简历ID" width="100" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'FINISHED' ? 'success' : 'warning'">
              {{ row.status === "FINISHED" ? "已完成" : "进行中" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="110">
          <template #default="{ row }">{{ row.currentIndex }}/{{ row.totalQuestionCount }}</template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="90" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" fixed="right" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadSessions"
          @current-change="loadSessions"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="面试详情" size="70%">
      <template v-if="detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="会话ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="岗位">{{ detail.jobTitle || "-" }}</el-descriptions-item>
          <el-descriptions-item label="公司">{{ detail.companyName || "-" }}</el-descriptions-item>
          <el-descriptions-item label="总分">{{ detail.totalScore ?? "-" }}</el-descriptions-item>
        </el-descriptions>

        <section class="detail-section">
          <h2>题目与回答</h2>
          <el-table :data="questionRows" border stripe>
            <el-table-column prop="sortNo" label="序号" width="80" />
            <el-table-column prop="questionType" label="类型" width="110" />
            <el-table-column prop="questionContent" label="题目" min-width="260" />
            <el-table-column prop="answerContent" label="回答/ASR文本" min-width="260" />
            <el-table-column prop="score" label="得分" width="90" />
            <el-table-column prop="level" label="等级" width="100" />
          </el-table>
        </section>

        <section class="detail-section">
          <h2>媒体与 ASR</h2>
          <el-table :data="detail.mediaRecords || []" border stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="questionId" label="题目ID" width="100" />
            <el-table-column prop="mediaType" label="类型" width="90" />
            <el-table-column prop="asrStatus" label="ASR状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.asrStatus === 'SUCCESS' ? 'success' : 'danger'">{{ row.asrStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="asrText" label="识别文本" min-width="260" />
            <el-table-column prop="asrError" label="错误" min-width="200" />
            <el-table-column label="音频" width="220">
              <template #default="{ row }">
                <audio v-if="row.fileUrl" :src="row.fileUrl" controls />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="170" />
          </el-table>
        </section>
      </template>
    </el-drawer>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { getMockInterviewSessionDetail, pageMockInterviewSessions } from "../../api/mockInterview";
import type { MockInterviewAnswerInfo, MockInterviewQuestionInfo, MockInterviewSessionInfo, MockInterviewSessionQuery } from "../../api/types";

const loading = ref(false);
const sessions = ref<MockInterviewSessionInfo[]>([]);
const total = ref(0);
const detailVisible = ref(false);
const detail = ref<MockInterviewSessionInfo | null>(null);

const query = reactive<MockInterviewSessionQuery>({
  pageNum: 1,
  pageSize: 10
});

const questionRows = computed(() => {
  const answerMap = new Map<number, MockInterviewAnswerInfo>();
  (detail.value?.answers || []).forEach(item => answerMap.set(item.questionId, item));
  return (detail.value?.questions || []).map((question: MockInterviewQuestionInfo) => {
    const answer = answerMap.get(question.id);
    return {
      ...question,
      answerContent: answer?.answerContent || "-",
      score: answer?.score,
      level: answer?.level
    };
  });
});

async function loadSessions() {
  loading.value = true;
  try {
    const page = await pageMockInterviewSessions(query);
    sessions.value = page.records || [];
    total.value = Number(page.total || 0);
  } finally {
    loading.value = false;
  }
}

function search() {
  query.pageNum = 1;
  loadSessions();
}

async function openDetail(row: MockInterviewSessionInfo) {
  detail.value = await getMockInterviewSessionDetail(row.id);
  detailVisible.value = true;
}

onMounted(loadSessions);
</script>

<style scoped>
.filter-form {
  margin-bottom: 12px;
}

.detail-section {
  margin-top: 20px;
}

.detail-section h2 {
  margin: 0 0 12px;
  font-size: 18px;
}

audio {
  width: 190px;
}
</style>
