package com.copilot.gemmatutor;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Channel;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.LogSeverity;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.MessageCallback;
import com.google.ai.edge.litertlm.SamplerConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class LlmEngine implements AutoCloseable {
    interface StreamListener {
        void onChunk(String text);
        void onComplete(String fullText);
        void onError(Exception ex);
    }

    private final Context appContext;
    private final Object conversationLock = new Object();
    private Engine engine;
    private Conversation activeConversation;
    private String lastConfigKey = null;
    private String lastSystemPrompt = null;
    private String lastError;
    private String activeBackendName = "CPU";

    // Generation config — changed via configure() from the Settings panel
    private boolean thinkingEnabled = false;
    private int configTopK = 64;
    private float configTopP = 0.95f;
    private float configTemperature = 1.0f;

    void configure(boolean thinkingEnabled, int topK, float topP, float temperature) {
        this.thinkingEnabled = thinkingEnabled;
        this.configTopK = topK;
        this.configTopP = topP;
        this.configTemperature = temperature;
    }

    LlmEngine(Context context) {
        this.appContext = context.getApplicationContext();
    }

    boolean isLoaded() {
        return engine != null && engine.isInitialized();
    }

    String getLastError() {
        return lastError;
    }

    String getActiveBackendName() {
        return activeBackendName;
    }

    void load(String modelPath, String backendName, int maxNumTokens) throws Exception {
        close();
        Engine.Companion.setNativeMinLogSeverity(LogSeverity.ERROR);
        Backend mainBackend = buildBackend(backendName);
        EngineConfig config = new EngineConfig(
                modelPath,
                mainBackend,
                mainBackend,
                null,
                maxNumTokens,
                1,
                appContext.getCacheDir().getAbsolutePath()
        );
        Engine created = new Engine(config);
        created.initialize();
        engine = created;
        activeBackendName = backendName == null ? "CPU" : backendName;
        lastConfigKey = null;
        lastSystemPrompt = null;
        lastError = null;
    }

    /**
     * Generate a streaming response.
     *
     * Creates a fresh Conversation per request (safest approach — avoids multi-turn
     * native state issues in LiteRT-LM). Uses CountDownLatch to block the calling
     * thread until inference fully completes before releasing the conversation,
     * preventing any "session already exists" race condition.
     */
    void generateStreaming(String systemPrompt, String userMessage, Bitmap image,
                           StreamListener listener) throws Exception {
        if (engine == null || !engine.isInitialized()) {
            throw new IllegalStateException("Gemma 4 E2B 模型还没有加载");
        }

        java.util.List<Channel> channels = thinkingEnabled ? null : Collections.<Channel>emptyList();
        java.util.Map<String, Object> extraContext = thinkingEnabled
                ? Collections.<String, Object>singletonMap("enable_thinking", "true")
                : Collections.<String, Object>emptyMap();

        Conversation conversation = null;
        try {
            synchronized (conversationLock) {
                // Close any lingering conversation before creating a new one.
                closeActiveConversationLocked();
                String sysText = (systemPrompt != null && !systemPrompt.isEmpty())
                        ? systemPrompt
                        : "You are a helpful multilingual assistant.";
                Contents system = Contents.Companion.of(sysText);
                ConversationConfig cfg = new ConversationConfig(
                        system,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        new SamplerConfig(configTopK, configTopP, configTemperature, 0),
                        true,
                        channels,
                        extraContext
                );
                conversation = engine.createConversation(cfg);
                activeConversation = conversation;
            }

            Contents contents;
            if (image == null) {
                contents = Contents.Companion.of(userMessage != null ? userMessage : "");
            } else {
                File imageFile = saveTempImage(image);
                contents = Contents.Companion.of(
                        new Content.ImageFile(imageFile.getAbsolutePath()),
                        new Content.Text(userMessage != null ? userMessage : "")
                );
            }

            // Block this thread until inference completes, so the next request cannot
            // start until we are safely out of sendMessageAsync.
            final CountDownLatch latch = new CountDownLatch(1);
            final StringBuilder fullText = new StringBuilder();
            final Conversation finalConversation = conversation;

            conversation.sendMessageAsync(contents, new MessageCallback() {
                @Override
                public void onMessage(Message message) {
                    String chunk = extractTextFromMessage(message);
                    if (chunk == null || chunk.isEmpty()) return;
                    fullText.append(chunk);
                    if (listener != null) listener.onChunk(fullText.toString());
                }

                @Override
                public void onDone() {
                    try {
                        if (listener != null) listener.onComplete(fullText.toString().trim());
                    } finally {
                        latch.countDown();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    lastError = throwable == null ? null : throwable.getMessage();
                    try {
                        Exception error = throwable instanceof Exception
                                ? (Exception) throwable
                                : new Exception(throwable);
                        if (listener != null) listener.onError(error);
                    } finally {
                        latch.countDown();
                    }
                }
            }, extraContext);

            // Wait for inference to finish (timeout = 3 min to guard against hangs).
            latch.await(180, TimeUnit.SECONDS);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new Exception("推理被中断", ie);
        } catch (Exception ex) {
            lastError = ex.getMessage();
            throw ex;
        } finally {
            // Always release the conversation after inference is done.
            // close() is synchronous and safely removes the session from LiteRT-LM.
            synchronized (conversationLock) {
                if (conversation != null && activeConversation == conversation) {
                    try { conversation.close(); } catch (Exception ignored) {}
                    activeConversation = null;
                }
            }
        }
    }

    /** Extract plain text from a LiteRT-LM Message (handles JSON wrapper or plain string). */
    private static String extractTextFromMessage(Message message) {
        if (message == null) return "";
        String raw = message.toString();
        if (raw == null || raw.isEmpty()) return "";
        // Use a trimmed copy ONLY for JSON detection — never trim the actual content,
        // because space-only tokens (" ") are valid and must not be discarded.
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(trimmed);
                org.json.JSONArray contentArr = json.optJSONArray("content");
                if (contentArr != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < contentArr.length(); i++) {
                        Object item = contentArr.opt(i);
                        if (item instanceof org.json.JSONObject) {
                            org.json.JSONObject obj = (org.json.JSONObject) item;
                            if ("text".equals(obj.optString("type"))) {
                                sb.append(obj.optString("text", ""));
                            }
                        } else if (item instanceof String) {
                            sb.append((String) item);
                        }
                    }
                    if (sb.length() > 0) return sb.toString();
                }
                String textField = json.optString("text", null);
                if (textField != null && !textField.isEmpty()) return textField;
            } catch (Exception ignored) {}
        }
        return raw; // Return original (with spaces), not the trimmed copy.
    }

    private File saveTempImage(Bitmap bitmap) throws Exception {
        File imageFile = new File(appContext.getCacheDir(), "gemma4-image-" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream output = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output);
        }
        return imageFile;
    }

    private Backend buildBackend(String backendName) {
        if ("GPU".equalsIgnoreCase(backendName)) {
            return new Backend.GPU();
        }
        return new Backend.CPU(4);
    }

    @Override
    public void close() {
        synchronized (conversationLock) {
            closeActiveConversationLocked();
        }
        if (engine != null) {
            engine.close();
            engine = null;
        }
        lastConfigKey = null;
        lastSystemPrompt = null;
    }

    private void closeActiveConversationLocked() {
        if (activeConversation == null) return;
        try { activeConversation.close(); } catch (Exception ignored) {}
        activeConversation = null;
    }
}