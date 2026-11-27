# 设计说明

## 交互

折叠态：右侧 50dp 圆球。
展开态：340dp 卡片，输入框 + 状态行 + 摘要区 + 统计脚注。

## 检索流程

1. 取 ReadBook.book / ReadBook.durChapterIndex / book.durChapterPos；
2. 从当前章往前扫，直到第 1 章、600 章或 8 秒预算用尽；
3. 当前章只取到 durChapterPos 为止（防剧透）；
4. 每章用 BookHelp.getContent 读本地缓存，未缓存则跳过（不联网）；
5. 命中位置前后各取 60/80 字作为片段，每章最多 6 处；
6. 全部命中按章节排序后等距抽样到 48 条（首条必留），避免长书上下文爆炸。

## 前置内容过滤

标题正则命中：序、序言、前言、引言、目录、内容简介、简介、作品相关、作者的话、上架感言、
感言、公告、通知、请假、说明、完本感言、新书感言、推荐票、月票、致读者、更新说明。
另外：isVolume 直接跳过；前 3 章且正文少于 600 字视为版权页/目录残片。
楔子、引子、序章保留（通常含真实剧情）。

## 摘要

- 只发片段，不发全文；
- 提示词要求"只依据片段、150~250 字、不预测剧情、信息不足要明说"；
- 温度 0.3，max_tokens 800。

## 缓存

trace_cache_v1 SharedPreferences，key = bookUrl + 人名。
7 天过期；阅读位置变化超过 3 章失效。

## 为什么用 ContentProvider 接入

不改 legado 任何现有 Kotlin 文件，只在 AndroidManifest 加一个 provider 标签。
进程启动时注册 ActivityLifecycleCallbacks，识别到 ReadBookActivity 就挂载悬浮层。
好处：升级 legado 时冲突面最小；坏处：多一个空 provider。

## 已知风险

- BookHelp.getContent 是同步读缓存，600 章约 1~3 秒，已在 IO 线程；
- 部分书源章节缓存是加密/分片格式，可能读不到，表现为"找不到"；
- 悬浮层挂在 decorView 上，个别机型手势导航区域可能遮挡，后续可加拖拽；
- 大模型可能"知道"原著后续剧情，提示词已限制，但仍需在 UI 上提示"仅基于已读内容"。
