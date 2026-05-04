# Gemma 语言助手 Android

这是一个面向高端 Android 真机的本地语言学习助手。界面是聊天式入口，支持文本输入、图片上传、系统语音输入、英语对话训练模式，以及基于 sherpa-onnx 的离线英语 TTS。

## 核心模型

目标模型是 Google 2026 年 3 月 31 日发布的 `google/gemma-4-E2B-it`。移动端使用 LiteRT-LM 版本：`litert-community/gemma-4-E2B-it-litert-lm`，通用模型文件名是 `gemma-4-E2B-it.litertlm`，大小约 2.58 GB。

Gemma 4 官方/下载入口：

- Hugging Face 仓库：<https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm>
- 直接下载链接：<https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true>
- Google Gemma 模型页：<https://huggingface.co/google/gemma-4-E2B-it>

推荐做法：

1. 直接在 App 首屏点「下载 Gemma 4 E2B」，应用会从 Hugging Face 下载 `gemma-4-E2B-it.litertlm` 到私有目录并自动加载。
2. 或者打开 Hugging Face 的 `litert-community/gemma-4-E2B-it-litert-lm` 页面，下载 `gemma-4-E2B-it.litertlm`。
3. 安装本 APK 后，首次打开点「选择本地模型文件」，选择该模型文件。应用会复制到私有目录并加载。

不建议把 2.58 GB Gemma 模型直接打进 APK。直装 APK 会变得巨大，安装和传输都不稳定，因此当前版本默认采用 App 内下载或首次导入模型。

## 离线 TTS

当前版本使用 sherpa-onnx 离线 TTS，而不是 Android 系统 TextToSpeech。

- Amy：Piper 英文单说话人模型，资源目录为 `app/src/main/assets/tts-model`
- af_sarah：Kokoro 英文多说话人模型中的 `speaker id = 3`，资源目录为 `app/src/main/assets/tts-model-kokoro-en`
- TTS 运行时会把 `espeak-ng-data` 复制到应用私有目录后再传给 sherpa-onnx，避免 Android assets 路径导致的 native 初始化问题

这些 TTS 权重文件比较大，不能直接提交到普通 GitHub 仓库：

- `app/src/main/assets/tts-model-kokoro-en/model.onnx` 约 330 MB
- `app/src/main/assets/tts-model/en_US-amy-medium.onnx` 约 60 MB

因此仓库默认不跟踪这些模型文件。首次 clone 后，请先下载 TTS 资源：

```powershell
.\scripts\download-tts-models.ps1
```

相关模型来源：

- sherpa-onnx TTS Releases：<https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models>
- Kokoro 英文模型说明：<https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html>
- Piper/VITS 模型说明：<https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html>

## 功能

- 中文词语：自动转英文，列出从简单到高级的近义表达，并解释区别。
- 中文句子：整句翻译，解释关键词和自然表达。
- 英文：先翻译成中文，再解释一词多义、近义词、搭配和语气。
- 日语等其他语言：翻译成中文和英文，并解释可用词汇。
- 图片：上传图片后，让 Gemma 识别图片文字或场景并翻译讲解。
- 日常英语对话：按钮切换后，每次回答都会以 `Do you mean "..."?` 开头，先把你的话改写成自然英语，再继续引导对话。
- 语音输入：调用 Android SpeechRecognizer；对话模式下识别完成会自动发送。
- 离线 TTS：使用 sherpa-onnx，在设置中可切换 `Amy (Piper)` 与 `af_sarah (Kokoro)`。
- 语音通话：识别完成后自动发给 Gemma，回复文本落屏后再由本地离线 TTS 分段播报。

## 构建 APK

项目已经在本机下载了本地 Android SDK 和 Gradle，可以直接运行脚本构建。

如果是第一次 clone 本仓库，建议顺序是：

```powershell
.\scripts\download-tts-models.ps1
.\scripts\build-apk.ps1
```

在项目根目录执行：

```powershell
.\scripts\build-apk.ps1
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

APK 也已复制到项目根目录：

```text
Gemma语言助手-debug.apk
```

如果手机打开了 USB 调试并能被 `adb devices` 识别，可以安装：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 电脑端测试限制

Android 模拟器通常不能可靠测试 LiteRT-LM 设备端大模型推理，Google 文档也建议在高端真机上测。没有 Gemma 4 E2B 模型文件时，应用会进入界面模板模式，方便测试 UI、语音按钮和 TTS 初始化；真正的 Gemma 文本/图片推理、语音通话稳定性和离线 TTS 都建议在真机上测试。

## PC 端下载模型

如果想先在电脑上下载模型再传手机，可以运行：

```powershell
.\scripts\download-gemma4-model.ps1
```

输出文件：

```text
models\gemma-4-E2B-it.litertlm
```

## 仓库说明

这个 GitHub 仓库只提交源码、脚本和文档，不提交超大模型权重。

- Gemma 4 `.litertlm`：通过 App 内下载或 `scripts/download-gemma4-model.ps1` 获取
- sherpa-onnx TTS `.onnx` / `.bin`：通过 `scripts/download-tts-models.ps1` 获取
- 之所以这样做，是因为 GitHub 普通仓库对单文件大小有限制，Kokoro 主模型超过 100 MB，无法直接正常推送