# 历史重建说明

这个仓库当前的提交历史由两部分组成：

- 一部分是真实存在的当前源码与文档提交。
- 一部分是为便于后续维护而补写的 reconstructed 里程碑提交。

## 当前阶段性提交映射

### 152ded6 docs: prepare GitHub repo and model download workflow

- 建立 README、.gitignore 和模型下载脚本。
- 解决“仓库不能直接携带超大模型文件”的交付问题。

### ae1a03a reconstructed: initial Android Gemma tutor app scaffold

- 表示项目最初 Android 主体框架已经存在。

### 3fc7b85 reconstructed: switch to sherpa-onnx offline TTS

- 表示项目从系统 TTS 转向 sherpa-onnx 离线 TTS 的阶段。

### b4f5739 reconstructed: add Amy and af_sarah voice selection

- 表示在设置中加入 Amy 与 af_sarah 两个音色，并做持久化。

### 833b8e7 reconstructed: harden TTS asset loading and voice-call playback

- 表示围绕 assets、dataDir、分段生成、播放稳定性的一轮集中加固。

### 59ac716 feat: add current Android app snapshot

- 当前完整 Android 源码快照。

## 为什么还要继续补文档型提交

因为真实的几十次中间编辑并没有原始 git 历史，所以只能通过以下方式提高可维护性：

1. 用 reconstructed 里程碑表达主要阶段。
2. 用 docs/ISSUES_AND_FIXES.md 记录实际踩过的坑。
3. 用 docs/MAINTENANCE_PLAYBOOK.md 记录后续排查顺序。
4. 用 Release 挂 APK，把某个稳定点固定下来。

## 后续建议

- 新问题一律真实提交，不再追加新的 reconstructed 空提交。
- 如果后续修复跨度较大，优先保证“一个问题一到两个提交”，而不是再次堆成一整个大快照。