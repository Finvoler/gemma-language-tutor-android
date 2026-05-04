# 维护手册

这个文件面向后续维护者，目的是在再次出现“语音无声、语音通话闪退、模型拉取失败、仓库推不上去”时，能尽快定位问题。

## 一、先确认当前问题属于哪一类

### 1. 语音通话开启后立即闪退

优先检查：

- SherpaOnnxTts.stop() 是否被改回了更激进的 interrupt 或跨线程 release。
- speak() 是否还保留分段 generate，而不是重新改成一次性整段生成。
- AudioTrack 是否仍然使用 MODE_STREAM。

### 2. 不闪退但没有声音

优先检查：

- TTS 模型目录是否已经通过 scripts/download-tts-models.ps1 下载齐全。
- app/build.gradle 是否仍保留 noCompress "onnx", "bin"。
- espeak-ng-data 是否仍通过 filesDir 真实路径传入。

### 3. GitHub 推送失败或仓库变得过大

优先检查：

- 是否误把 .onnx、.bin 或 Gemma 主模型加入 git。
- .gitignore 是否被改坏。
- 是否把下载产物、_download_tmp、models 重新纳入版本控制。

## 二、推荐排查顺序

1. 先看 README，确认当前仓库约定没有被破坏。
2. 再看 docs/ISSUES_AND_FIXES.md，对照历史问题是否同类复发。
3. 检查 SherpaOnnxTts.java 当前逻辑是否仍保留以下稳定性策略：
   - 分段 generate
   - MODE_STREAM
   - dataDir 复制到 filesDir
   - stop 时不强 interrupt native generate
4. 如果涉及构建失败，再检查 app/build.gradle 与本地 SDK/Gradle。

## 三、本地常用命令

### 1. 下载 TTS 资源

```powershell
.\scripts\download-tts-models.ps1
```

### 2. 构建 APK

```powershell
.\scripts\build-apk.ps1
```

### 3. 安装到真机

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 四、修改 TTS 相关代码时的底线

- 不要把 stop 流程重新改成“看到取消就立刻强杀一切对象”。
- 不要把长文本一次性 generate 回来再整体播放。
- 不要把 espeak-ng-data 从 filesDir 真实路径改回 assets 虚拟路径。
- 不要把大模型或大权重重新提交进 git。

## 五、真机回归建议

每次改动以下任一文件后，都建议在真机回归：

- app/src/main/java/com/copilot/gemmatutor/SherpaOnnxTts.java
- app/src/main/java/com/copilot/gemmatutor/MainActivity.java
- app/build.gradle

最少回归场景：

1. 切到 Amy，触发一次普通播报。
2. 切到 af_sarah，触发一次普通播报。
3. 打开语音通话，连续完成至少两轮识别和回复播报。
4. 中途关闭再重新打开语音通话，确认不会闪退。

## 六、仓库维护原则

- 以后每次真实改动都直接提交，不再依赖 reconstructed 历史补写。
- 发布 APK 时优先走 GitHub Release，避免散落在聊天记录或临时目录中。
- 文档里写清楚“源码提交了什么，模型需要另行下载什么”，这样后续接手者不会误判仓库缺文件。