package com.copilot.gemmatutor;

final class TutorPrompts {
    private TutorPrompts() {}

    // ── System / user message API ────────────────────────────────────────────

    static String getStudySystemPrompt() {
        return "You are a concise language tutor. Always reply in Chinese. Never output pinyin.\n\n"
                + "For a single English word or short phrase:\n"
                + "  - If ONE clear meaning: output exactly two lines:\n"
                + "    **[Chinese meaning]** — [one-line English explanation]\n"
                + "    **Example:** [short English sentence] （[Chinese translation]）\n"
                + "  - If 2-3 DIFFERENT meanings: list each on its own line as **1.** **2.** then one Example line.\n"
                + "  - NEVER repeat the same meaning twice. NEVER number a line that says the same thing as another line.\n\n"
                + "For a sentence or Chinese text: give a clean direct Chinese translation only. No extra notes.\n"
                + "Keep all output short and non-redundant.";
    }

    static String getStudyUserMessage(String input, boolean hasImage) {
        String trimmed = input == null ? "" : input.trim();
        if (hasImage) {
            return "Image attached.\n" + trimmed;
        }
        return trimmed;
    }

    static String getConversationSystemPrompt() {
        return "You are an English speaking practice partner. The learner may type Chinese or English.\n"
                + "ALWAYS start your reply with: Do you mean \"[rewrite as natural spoken English]\"?\n"
                + "Then continue in 1-2 short natural English sentences. No vocabulary lectures. No lists.\n"
                + "Ask at most one short follow-up question. Keep replies short enough for TTS.";
    }

    static String getConversationUserMessage(String input) {
        return input == null ? "" : input.trim();
    }

    static String getOpenChatSystemPrompt(boolean hasImage) {
        return "You are a helpful general-purpose assistant running fully offline on Android. "
                + "Reply in the user's language. Be direct and concise."
                + (hasImage ? " The user may attach images; describe content, extract text, and answer their question." : "");
    }

    static String getOpenChatUserMessage(String input) {
        return input == null ? "" : input.trim();
    }

    // ── Legacy single-prompt API (kept for fallback strings) ─────────────────

    static String buildStudyPrompt(String input, boolean hasImage) {
        String trimmed = input == null ? "" : input.trim();
        String languageHint = detectLanguage(trimmed);
        boolean sentenceLike = looksLikeSentence(trimmed);
        boolean shortEnglish = isShortEnglishTerm(trimmed);
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a multilingual language tutor inside an offline Android app. ");
        prompt.append("Answer in concise, well-structured Chinese unless the user asks otherwise. ");
        prompt.append("Keep the answer short. Give the result first. No long lectures, no redundant synonyms, no extra sections unless needed. ");
        prompt.append("Only give advanced alternatives, nuance comparison, or extended explanation if the user explicitly asks a follow-up. ");

        if (shortEnglish) {
            prompt.append("The input is a single English word or short phrase. Output only 3 short parts: 1) English explanation in simple English, 2) Chinese meaning, 3) one short example sentence with Chinese translation. ");
            prompt.append("Do not add collocations, polysemy, or synonym lists unless asked. ");
        } else if (sentenceLike) {
            prompt.append("The input is sentence-like. Translate it directly first. Then briefly explain only the difficult words or phrases, at most 3 items. ");
            prompt.append("Do not expand into essay-style explanation. ");
        } else {
            prompt.append("If the input is not a full sentence, give a direct translation or core meaning first, then one brief note only if necessary. ");
        }

        if (hasImage) {
            prompt.append("The user has attached an image. If it contains text, extract the text and answer briefly using the same concise style. If it is a scene, describe only what is needed to answer the user's question. ");
        }
        prompt.append("Detected input language: ").append(languageHint).append(".\nUser input:\n").append(trimmed);
        return prompt.toString();
    }

    static String buildConversationPrompt(String userInput, String transcript) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an English speaking practice partner. The learner may type Chinese or English. ");
        prompt.append("Every single reply MUST begin exactly with: Do you mean \"");
        prompt.append("then rewrite the learner's message into natural spoken English, close the quote, and continue the conversation. ");
        prompt.append("After that, respond in very short natural English. Prefer 1 to 2 short sentences total. ");
        prompt.append("Do not give long explanations, vocabulary lectures, or multiple options unless the user asks. ");
        prompt.append("Ask at most one short follow-up question when it helps the conversation. Keep the reply short enough for TTS. Do not start with any other words.\n");
        if (transcript != null && !transcript.isEmpty()) {
            prompt.append("Recent conversation:\n").append(transcript).append("\n");
        }
        prompt.append("Learner message:\n").append(userInput == null ? "" : userInput.trim());
        return prompt.toString();
    }

    static String buildOpenChatPrompt(String userInput, boolean hasImage, String transcript) {
        String trimmed = userInput == null ? "" : userInput.trim();
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful general-purpose multimodal assistant running fully offline inside an Android app. ");
        prompt.append("Reply naturally in the user's language unless the user asks for another language. ");
        prompt.append("Be direct, useful, and conversational. ");
        prompt.append("You may answer normal chat, reasoning, coding, writing, image understanding, translation, and everyday questions. ");
        if (hasImage) {
            prompt.append("The user attached an image. Use it as part of your answer. Describe visible content, extract text if relevant, and respond to the user's request. ");
        }
        if (transcript != null && !transcript.isEmpty()) {
            prompt.append("Recent conversation:\n").append(transcript).append("\n");
        }
        prompt.append("User message:\n").append(trimmed);
        return prompt.toString();
    }

    static String buildFallbackStudyAnswer(String input, boolean conversationMode) {
        String text = input == null ? "" : input.trim();
        if (conversationMode) {
            return "Do you mean \"Could you say that again in simple English?\" Sure. Say one short sentence, and I will keep the reply brief.";
        }
        if (containsCjk(text)) {
            return "模型还没有加载。离线模板：\n\n英文翻译\n" + text + "\n\n需要更地道表达、近义替换或更高级说法时，你可以继续追问。";
        }
        return "模型还没有加载。离线模板：\n\n中文翻译\n这里会先给你直译。\n\n难词\n只解释必要的难词；更多内容可以继续追问。";
    }

    private static boolean isShortEnglishTerm(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return false;
        if (containsCjk(trimmed)) return false;
        if (looksLikeSentence(trimmed)) return false;
        String[] parts = trimmed.split("\\s+");
        return parts.length <= 3;
    }

    private static boolean looksLikeSentence(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.contains("?") || trimmed.contains("!") || trimmed.contains(".") || trimmed.contains("，") || trimmed.contains("。") || trimmed.contains("？") || trimmed.contains("！")) {
            return true;
        }
        String[] parts = trimmed.split("\\s+");
        return parts.length >= 4;
    }

    static String buildFallbackOpenChatAnswer(String input, boolean hasImage) {
        String text = input == null ? "" : input.trim();
        if (hasImage && text.isEmpty()) {
            return "模型还没有加载。我现在只能告诉你：图片已收到，但要做正常的多模态理解还需要先加载 Gemma 4 E2B。加载完成后，我可以直接看图聊天、描述内容、提取文字并回答问题。";
        }
        if (text.isEmpty()) {
            return "模型还没有加载。等 Gemma 4 E2B 加载完成后，这里就会变成正常的本地 LLM 聊天窗口，支持自由问答、图片理解和语音输入。";
        }
        return "模型还没有加载。当前只能返回占位回复：你刚才说的是“" + text + "”。加载 Gemma 4 E2B 后，这里会按普通多模态 LLM 的方式直接和你聊天。";
    }

    static boolean containsCjk(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\u4e00' && c <= '\u9fff') || (c >= '\u3040' && c <= '\u30ff')) {
                return true;
            }
        }
        return false;
    }

    private static String detectLanguage(String text) {
        if (text == null || text.isEmpty()) return "empty";
        boolean cjk = false;
        boolean kana = false;
        boolean latin = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') cjk = true;
            if (c >= '\u3040' && c <= '\u30ff') kana = true;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) latin = true;
        }
        if (kana) return "Japanese";
        if (cjk) return "Chinese";
        if (latin) return "English or another Latin-script language";
        return "unknown";
    }
}