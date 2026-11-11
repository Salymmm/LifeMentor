# LifeQuest · AI 人生模拟器 × 人生教练

> Your Life, Your Game —— 面向大学毕业过渡期青年的 AI 人生经营与成长系统。
> 本仓库为 MVP 2.0 代码初版：功能完整可跑、真实 LLM 接入、全部数据本地存储。

## 三步上手

1. **打开项目**：Android Studio → `Open` → 选择 `LifeMentor/`，等待首次 Gradle 同步（约 1–3 分钟）。
2. **配置免费 API Key**（约 3 分钟，只需一次）：
   - 打开 [open.bigmodel.cn](https://open.bigmodel.cn)（智谱开放平台）注册/登录
   - 用户中心 → **API Keys** → 新建并复制
   - App 内「设置 → 大模型接口」粘贴 → 保存 → 点「测试连接」看到 ✓
   - 默认模型 `glm-4-flash` 长期免费；也支持 SiliconFlow / DeepSeek / 本机 Ollama（设置页一键预设）
3. **运行**：手机开 USB 调试或启动模拟器，点绿色 ▶ Run。首次进入会走 4 步「创建人生角色」向导。

## 已实现功能（对应 PRD P0 全覆盖）

| 页面 | 功能 | PRD 对应 |
|---|---|---|
| 🏠 主页 | 角色卡（头像/等级/XP 进度）、今日行动勾选得 XP、五维成长雷达图、愿景展示 | P0 成长面板 |
| 🎯 目标 | 新增目标（归属五大领域）、**AI 三层拆解**（季度里程碑→周任务→每日行动）、勾选执行、快速添加行动、删除/完成目标 | P0 AI 目标拆解 |
| 🎓 导师 | 五位人格化导师多轮聊天（先生/引路人/树洞/教练/账房），共享角色+画像+目标+本周进度的上下文，历史记录本地保存，**P1 自定义第六导师已做** | P0+P1 |
| 📖 档案 | 职业规划（生成/增量更新/版本历史/复制/导出 .md）、每周 AI 复盘（聚合本周数据）、成就墙 | P0 每周复盘 + 人生档案 |
| ⚙️ 设置 | 角色编辑、职业画像编辑（9 字段）、API 配置、清空全部数据 | P0 人生角色 |

**安全边界（PRD 第八节要求，已内置）**：树洞不做诊断+自伤关键词触发 12356 热线卡片；账房/教练固定免责（不构成投资建议/医疗意见）；XP 体系只奖励不惩罚（断签不清零、取消勾选不扣分）。

## 技术说明

- Kotlin + Jetpack Compose (Material3)，5 tab 导航，无 XML 布局
- 存储：应用私有目录 JSON 文件（`profile/settings/role/goals/tasks/xp/chats/plans/reviews`），仓库层集中在 `GameStore` / `AppStorage`，**后续迁移 Room 只需替换这两个文件**
- LLM：OkHttp + OpenAI 兼容 `/chat/completions`，Base URL/模型/Key 用户可配；目标拆解走严格 JSON 输出 + 自动重试一次
- 无第三方图表/Markdown 库：雷达图 Canvas 自绘，Markdown 渲染器自研（`MarkdownText.kt`）
- 工具链：AGP 9.3.2 / Gradle 9.5.0 / Kotlin 2.2.10 / Compose BOM 2026.02.01，compileSdk 37，minSdk 26
- 已通过 `./gradlew assembleDebug` 真实构建验证（无设备依赖）
- 规模：34 个 Kotlin 文件 / 约 3500 行

## 给接手的同学

- 分工与砍价说明见《开发计划.md》（含 P1/P2 待办与技术入口、风险边界清单）
- 已知简化（第一版故意为之，勿当 bug 修）：
  - 导师"协作" = 共享上下文注入，无 Agent 互调；跨领域联动写在各导师 persona 里
  - 技能树 = 写死的五维雷达，无自定义分支/图形编辑
  - 人生模拟器（分支推演）未做，建议从聊天内「推演模式」prompt 起步
  - 提醒无系统通知/日历同步（需 POST_NOTIFICATIONS + WorkManager，见计划文档）
  - JSON 文件存储在数据量极大时有性能上限，迁移 Room 的路线已预留
- 建议第一步真机验证清单：创建角色 → 设置填 Key → 建目标点拆解（观察 JSON 是否稳定）→ 勾任务看 XP/雷达 → 和树洞聊一句 → 生成职业规划 → 生成每周复盘 → 导出 .md
