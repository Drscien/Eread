# Eread 人物速查

> 面向长篇小说阅读的人物检索、概述与关系梳理工具
> 版本 1.0.0 · Android 8.0+

Eread 不修改 legado 与 Readest，通过 legado 的本地 Web 服务、本地导入的 TXT/EPUB，或屏幕识别来获取阅读位置，在阅读过程中完成人物回溯、摘要生成与关系整理。

## 功能

- **双读取模式**
  - Legado 模式：通过 legado Web 服务读取书架、章节与阅读位置，片段可跳转；
  - Readest 模式：导入同一本 EPUB/TXT，本地全文检索，位置由截屏识别或手动选章得到。
- **双截屏方式**
  - OCR 识别：无障碍截屏，速度快、无状态栏提示；
  - 屏幕录制：MediaProjection，授权一次可反复取帧；
  - 两种方式可分别开启 AI 辅助。
- **人物消歧**
  - 保留「·」结构与「三世/四世」；
  - 同名短名每次询问，不自动绑定；
  - 全书人名索引，支持重建。
- **本地检索**
  - 精确子串匹配，不经过 AI；
  - 按当前阅读位置统计命中；
  - 命中数、展示上限、每章上限可调。
- **AI 概述**
  - 最新 / 增量 / 回读三种模式；
  - 详细档案按重要性控制篇幅；
  - 用户可自定义文风与结构。
- **关系网**
  - 主人物 + 多选次人物；
  - 全书扫描共现片段；
  - 自动写入反向关系；
  - 关系时间线查看。
- **数据管理**
  - 按书聚合档案；
  - 同名书合并；
  - 多选导出 TXT/Word、系统分享、删除。
- **个性化**
  - AI 文风提示词；
  - 概述温度与长度；
  - 回读密度、命中参数、关系共现参数；
  - 名称识别降噪参数。
- **隐私**
  - API Key 使用 Android Keystore 加密；
  - 关闭系统备份；
  - 截屏不落盘；
  - 仅通过 HTTP 读取 legado，不写入。

## 安装

从 [Releases](https://github.com/Drscien/Eread/releases) 下载 APK 安装。

首次使用：

1. 设置 → 模型：填写接口地址与 API Key；
2. 设置 → 悬浮窗：授予悬浮窗权限，选择截屏方式；
3. 运行页选择 Legado / Readest 模式，启动悬浮窗。

## 文档

- [基本功能使用说明](docs/基本功能使用说明.md)
- [使用说明书](docs/使用说明书.md)
- [设计说明书](docs/设计手册.md)

## 构建

依赖 JDK 21 与 Android SDK（compileSdk 36）。

    cd standalone
    ./gradlew assembleDebug

## 版本

当前版本：1.0.0

更新记录见 [CHANGELOG.md](CHANGELOG.md)。

## 许可与声明

- 本项目不包含 legado 或 Readest 的代码；
- legado 为 GPLv3 项目，Readest 为 AGPLv3 项目，Eread 仅通过其公开接口或用户导入的文件工作；
- AI 生成的概述与档案可能存在错误，请以原文为准。

作者：Venus（启明星）
问题反馈：[Issues](https://github.com/Drscien/Eread/issues)
