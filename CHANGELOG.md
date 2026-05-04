# Changelog

## v0.1.0 - 2026-05-04

### Added

- Android Gemma 4 本地语言学习助手当前源码快照。
- sherpa-onnx 离线 TTS，当前保留 Amy (Piper) 并支持语速、活泼度调节。
- TTS 模型下载脚本与 Gemma 模型下载说明。
- 问题与修复日志、维护手册、重建时间线。

### Fixed

- 修正 sherpa-onnx Android 配置方式，适配当前 Java API。
- 修正 APK 中 .onnx / .bin 压缩导致的 native 读取异常。
- 将长文本语音播放从静态缓冲切换为流式播放。
- 将 TTS 改为分段生成，降低语音通话阶段的闪退概率。
- 将 espeak-ng-data 复制到应用私有目录后再传给 native 层。
- 调整 stop 流程，避免跨线程释放和粗暴中断带来的崩溃风险。

### Notes

- 由于项目最初没有持续 git 历史，本仓库包含 reconstructed 里程碑提交，它们用于表达阶段过程，不等同于原始逐次编辑记录。
- GitHub 仓库不包含 Gemma 4 主模型与超大 TTS 权重，需通过脚本或应用内下载获取。