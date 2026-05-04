# 问题与修复日志

这个文件记录本项目在集成 Gemma 4 与 sherpa-onnx 离线 TTS 过程中，已经确认过的关键问题、实际症状、根因和最终处理方式。后续维护时，优先从这里回看历史背景，再决定是否重构。

## 1. sherpa-onnx Java API 误判为 builder 风格

- 症状：按常见 Java builder 方式初始化 TTS 配置时，代码无法正常适配当前依赖版本。
- 根因：当前 Android 侧使用的 sherpa-onnx Java API 更接近 Kotlin data class setter 风格，不是链式 builder。
- 处理：改为显式创建 OfflineTtsConfig、OfflineTtsModelConfig、OfflineTtsVitsModelConfig、OfflineTtsKokoroModelConfig，再逐个 set。
- 维护建议：升级 sherpa-onnx 版本前，先对照官方 Android 示例确认 API 形态，不要凭旧经验直接改调用方式。

## 2. APK 内 .onnx / .bin 被压缩，native 无法稳定读取

- 症状：TTS 初始化失败、无声、或者 native 层直接崩溃。
- 根因：模型文件和 voices.bin 打包进 APK 后被压缩，底层读取行为与预期不一致。
- 处理：在 app/build.gradle 中加入 noCompress，显式保留 onnx 与 bin 原始存储。
- 维护建议：今后新增任何 sherpa-onnx 依赖文件时，都先检查是否需要 noCompress。

## 3. AudioTrack 使用 MODE_STATIC 处理长语音不稳定

- 症状：长文本播报时容易卡顿、内存压力增大，甚至在极端情况下触发播放异常。
- 根因：MODE_STATIC 更适合短音频，长文本 TTS 一次性生成后再整体播放，风险更高。
- 处理：改为 AudioTrack.MODE_STREAM，分块写入 PCM 数据。
- 维护建议：后续不要再把长文本整段塞回静态缓冲；如果要优化流畅度，优先从 chunk 大小和队列策略入手。

## 4. 一次性 generate 整段长文本时，native 更容易闪退

- 症状：进入语音通话后，Gemma 回复一长段文字，TTS 在生成或播报前后直接闪退。
- 根因：底层生成长文本语音时资源压力过大，长句和复杂标点混在一起时更容易暴露问题。
- 处理：在 SherpaOnnxTts 中增加文本清洗、总长度限制、分句分段生成；每段生成后立即播放，而不是等整段完成。
- 维护建议：MAX_TOTAL_SPEECH_CHARS 与 MAX_CHUNK_CHARS 是稳定性参数，不要只为了“读得更多”就直接放大。

## 5. espeak-ng-data 直接走 assets 路径不稳定

- 症状：部分机型上 TTS 初始化失败，或者没有明显报错但就是不出声。
- 根因：espeak-ng-data 目录直接通过 assets 虚拟路径传给 native 层，在 Android 上并不总是可靠。
- 处理：启动 TTS 时先把 espeak-ng-data 复制到应用私有目录，再把真实绝对路径传给 sherpa-onnx。
- 维护建议：只要是 native 依赖目录，优先考虑复制到 filesDir 后再传路径，不要假设 assets 虚拟路径总能工作。

## 6. stop() 期间跨线程 release AudioTrack / interrupt generate 有崩溃风险

- 症状：语音通话刚开始或刚停止时更容易出现闪退，尤其是在新的播报任务抢占旧任务时。
- 根因：旧实现里 stop 过程会更激进地打断生成与播放，导致底层对象在并发切换时状态不一致。
- 处理：不再跨线程强行 release AudioTrack，不再直接 interrupt native generate；改为 cancel(false) 配合 pause/flush 和状态位停止。
- 维护建议：后续如果改动 stop 逻辑，先在真机上反复测试“连续点击语音通话开启/关闭”和“连续多轮回复播报”。

## 7. TTS 权重过大，无法直接进普通 GitHub 仓库

- 症状：Kokoro 模型超过 GitHub 普通仓库单文件限制，直接推送会失败。
- 根因：app/src/main/assets/tts-model-kokoro-en/model.onnx 约 330 MB，远超普通 git push 可接受大小。
- 处理：仓库只保留源码、脚本和文档；通过 scripts/download-tts-models.ps1 在 clone 后拉取模型。
- 维护建议：不要把大权重重新 git add 回仓库；如果未来必须托管，改用 Release、LFS 或独立对象存储。

## 8. Gemma 4 主模型不适合直接打包进 APK

- 症状：如果尝试把 2.58 GB 的 litertlm 模型直接放进 App，会导致构建、分发和安装都变得非常不稳定。
- 根因：模型过大，不适合普通 APK 交付路径。
- 处理：保留应用内下载与首次导入两种方式，仓库中不提交模型文件本体。
- 维护建议：任何发布流程都应继续保持“APK 与大模型分离”。

## 9. 这个目录原本不是 git 仓库，真实中间历史不可回放

- 症状：用户希望把“每一次修改”都恢复为提交记录，但项目最早开发时并没有持续 git 提交。
- 根因：缺少原始版本控制历史，无法无损还原几十次真实编辑顺序。
- 处理：本仓库采用 reconstructed 提交重建阶段性里程碑，再用新增文档把问题过程补齐。
- 维护建议：后续任何修复都直接走真实 git commit，不要继续依赖事后重建。

## 重点维护入口

- TTS 初始化与播放：app/src/main/java/com/copilot/gemmatutor/SherpaOnnxTts.java
- 语音通话与设置持久化：app/src/main/java/com/copilot/gemmatutor/MainActivity.java
- APK 打包规则：app/build.gradle
- TTS 模型下载：scripts/download-tts-models.ps1
- 模型与仓库说明：README.md