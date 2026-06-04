<template>
  <main class="page-section">
    <section class="chat-shell">
      <aside class="chat-side">
        <h2>AI 求职助手</h2>
        <button>简历评分</button>
        <button>岗位匹配</button>
        <button>打招呼语生成</button>
      </aside>

      <section class="chat-main">
        <div class="message assistant">
          <b>Job-Agent</b>
          <p>你好，我可以帮你分析简历、匹配岗位、生成 HR 打招呼语，也可以根据你的求职偏好推荐岗位。</p>
        </div>
        <div class="message user">
          <p>帮我找一下上海 15K 以上、适合 Java 后端方向的岗位。</p>
        </div>
        <div class="message assistant">
          <b>Job-Agent</b>
          <p>可以。我会结合你的默认简历、期望城市、薪资范围和技能栈，优先推荐匹配度高且通勤更友好的岗位。</p>
        </div>

        <div class="chat-input">
          <input aria-label="向 AI 提问" placeholder="输入你的求职问题" />
          <button>发送</button>
        </div>
      </section>
    </section>
  </main>
</template>
