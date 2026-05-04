# v0.1.0 Release Notes

这是当前 Android Gemma 语言助手的首个归档版 Release，对应一个可构建、可安装、并且已经把本轮开发问题过程补充进仓库文档的稳定节点。

## 本次 Release 包含

- 当前 Android 应用完整源码快照。
- Gemma 4 模型下载说明与本地导入说明。
- sherpa-onnx 离线 TTS 集成。
- Amy (Piper) 与 af_sarah (Kokoro) 两个音色选择。
- 语音通话场景下的 TTS 稳定性修复。
- 问题日志、维护手册、重建历史说明。

## 已明确记录到仓库的问题

- sherpa-onnx Java API 形态与预期不一致。
- APK 压缩 .onnx / .bin 导致 native 读取异常。
- MODE_STATIC 对长文本播报不稳定。
- 一次性生成整段语音更容易触发闪退。
- espeak-ng-data 直接走 assets 路径不稳定。
- stop 期间跨线程 release 或粗暴 interrupt 存在崩溃风险。
- TTS 与 Gemma 大模型权重不适合直接进入普通 GitHub 仓库。

## 交付说明

- 本 Release 附带 debug APK，便于直接回装验证。
- Gemma 4 主模型与超大 TTS 权重未打进仓库，需要按 README 下载。
- 部分历史提交为 reconstructed 里程碑，用于补齐维护线索，不等同于最初开发阶段的真实逐次提交。