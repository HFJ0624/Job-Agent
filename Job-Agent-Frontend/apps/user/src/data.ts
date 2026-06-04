export interface JobItem {
  id: number;
  title: string;
  company: string;
  city: string;
  district: string;
  salary: string;
  experience: string;
  education: string;
  tags: string[];
  highlight: string;
  hr: string;
  companyStage: string;
  companySize: string;
  matchScore: number;
}

export interface CompanyItem {
  id: number;
  name: string;
  industry: string;
  jobs: number;
  stage: string;
}

export const jobs: JobItem[] = [
  {
    id: 1,
    title: "Java 后端开发工程师",
    company: "星河智能科技",
    city: "上海",
    district: "徐汇区",
    salary: "18-28K",
    experience: "3-5年",
    education: "本科",
    tags: ["Spring Boot", "MySQL", "Redis", "AI 应用"],
    highlight: "业务增长稳定，AI Agent 新团队，技术栈现代",
    hr: "陈女士 · 技术招聘",
    companyStage: "B轮",
    companySize: "100-499人",
    matchScore: 92
  },
  {
    id: 2,
    title: "AI 应用开发工程师",
    company: "青岚数据",
    city: "杭州",
    district: "西湖区",
    salary: "20-35K",
    experience: "1-3年",
    education: "本科",
    tags: ["LangChain4j", "RAG", "向量检索", "Prompt"],
    highlight: "负责企业知识库和智能助手产品落地",
    hr: "周先生 · 研发负责人",
    companyStage: "A轮",
    companySize: "50-99人",
    matchScore: 88
  },
  {
    id: 3,
    title: "后端研发工程师",
    company: "柏舟云聘",
    city: "深圳",
    district: "南山区",
    salary: "15-25K",
    experience: "3-5年",
    education: "本科",
    tags: ["微服务", "RabbitMQ", "Docker", "高并发"],
    highlight: "招聘 SaaS 平台，业务贴近本项目场景",
    hr: "林女士 · HRBP",
    companyStage: "C轮",
    companySize: "500-999人",
    matchScore: 84
  },
  {
    id: 4,
    title: "全栈开发工程师",
    company: "一象简历",
    city: "北京",
    district: "海淀区",
    salary: "16-26K",
    experience: "1-3年",
    education: "本科",
    tags: ["Vue3", "TypeScript", "Spring Boot", "SSE"],
    highlight: "前后端都能参与，适合展示完整项目能力",
    hr: "王先生 · 技术经理",
    companyStage: "天使轮",
    companySize: "20-49人",
    matchScore: 79
  }
];

export const companies: CompanyItem[] = [
  { id: 1, name: "星河智能科技", industry: "人工智能", jobs: 28, stage: "B轮" },
  { id: 2, name: "青岚数据", industry: "企业服务", jobs: 16, stage: "A轮" },
  { id: 3, name: "柏舟云聘", industry: "招聘 SaaS", jobs: 42, stage: "C轮" }
];
