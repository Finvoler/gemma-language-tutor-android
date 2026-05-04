package com.copilot.gemmatutor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.FileProvider;
import androidx.core.text.HtmlCompat;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.res.ResourcesCompat;

import java.io.InputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "gemma_tutor_prefs";
    private static final String PREF_HISTORY = "history_entries";
    private static final String PREF_THINKING_ENABLED = "thinking_enabled";
    private static final String PREF_ACCELERATOR = "accelerator";
    private static final String PREF_MAX_TOKENS = "max_tokens";
    private static final String PREF_TOP_K = "top_k";
    private static final String PREF_TOP_P = "top_p";
    private static final String PREF_TEMPERATURE = "temperature";
    private static final String PREF_TTS_VOICE = "tts_voice";
    private static final int REQUEST_IMPORT_MODEL = 1001;
    private static final int REQUEST_PICK_IMAGE = 1002;
    private static final int REQUEST_CAPTURE_IMAGE = 1003;
    private static final int REQUEST_RECORD_AUDIO = 1004;
    private static final int REQUEST_CAMERA_PERMISSION = 1005;
    private static final int VOICE_TARGET_COMPOSER = 1;
    private static final int VOICE_TARGET_CALL = 2;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LlmEngine llmEngine;
    private SherpaOnnxTts sherpaOnnxTts;
    private SpeechRecognizer speechRecognizer;
    private Typeface uiRegularTypeface;
    private Typeface uiMediumTypeface;

    private FrameLayout root;
    private LinearLayout chatList;
    private ScrollView scrollView;
    private EditText inputBox;
    private TextView modelStatus;
    private ImageButton conversationButton;
    private ImageButton openChatButton;
    private ImageButton voiceCallButton;
    private ImageButton sendButton;
    private ImageButton micButton;
    private ImageButton voiceCallMicButton;
    private FrameLayout selectedImageChip;
    private ImageView selectedImagePreview;
    private TextView selectedImageRemove;
    private TextView imageStatus;
    private FrameLayout overlay;
    private FrameLayout historyOverlay;
    private FrameLayout voiceCallOverlay;
    private TextView overlayTitle;
    private TextView overlayMessage;
    private Button overlayButton;
    private Button overlayDownloadButton;
    private LinearLayout historyList;
    private TextView historyEmptyView;
    private LinearLayout voiceCallTranscriptList;
    private ScrollView voiceCallScrollView;
    private TextView voiceCallStatusView;
    private TextView voiceCallHintView;

    private Bitmap selectedImage;
    private Uri pendingCameraUri;
    private boolean conversationMode;
    private boolean openChatMode;
    private boolean thinkingEnabled = false;
    private String configAccelerator = "GPU";
    private int configMaxTokens = 4096;
    private int configTopK = 64;
    private float configTopP = 0.95f;
    private float configTemperature = 1.0f;
    private String selectedTtsVoiceKey = SherpaOnnxTts.VoiceProfile.PIPER_AMY.getKey();
    private boolean voiceCallOpen;
    private boolean restoreConversationModeAfterCall;
    private boolean loadingDotsRunning;
    private boolean voiceInputActive;
    private int voiceInputTarget = VOICE_TARGET_COMPOSER;
    private int dotCount;
    private int nextHistoryId = 1;
    private int currentHistoryId = -1;

    private final StringBuilder transcript = new StringBuilder();
    private final ArrayList<ChatMessage> currentConversationMessages = new ArrayList<>();
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().escapeHtml(false).build();
    private final ArrayList<HistoryEntry> historyEntries = new ArrayList<>();
    private final Runnable hideImageStatusRunnable = () -> imageStatus.setVisibility(View.GONE);
    private final Runnable voiceIndicatorRunnable = new Runnable() {
        @Override
        public void run() {
            if (!voiceInputActive) return;
            dotCount = (dotCount + 1) % 4;
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < dotCount; i++) dots.append('.');
            if (voiceInputTarget == VOICE_TARGET_CALL) {
                if (voiceCallHintView != null) voiceCallHintView.setText("正在听你说话" + dots);
                if (voiceCallMicButton != null) voiceCallMicButton.setAlpha(dotCount % 2 == 0 ? 1.0f : 0.78f);
            } else {
                if (modelStatus != null) modelStatus.setText("语音输入中" + dots);
                if (micButton != null) micButton.setAlpha(dotCount % 2 == 0 ? 1.0f : 0.78f);
            }
            mainHandler.postDelayed(this, 360);
        }
    };

    private static final class HistoryEntry {
        final int id;
        String title;
        String preview;
        String detail;
        String messagesJson;

        HistoryEntry(int id, String title, String preview, String detail, String messagesJson) {
            this.id = id;
            this.title = title;
            this.preview = preview;
            this.detail = detail;
            this.messagesJson = messagesJson;
        }
    }

    private static final class ChatMessage {
        final String role;
        final String text;

        ChatMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        llmEngine = new LlmEngine(this);
        loadUiPreferences();
        loadUiTypefaces();
        loadHistoryEntries();
        buildUi();
        sherpaOnnxTts = new SherpaOnnxTts(this, resolveSelectedTtsVoiceProfile());
        setupSpeechRecognizer();
        String welcome = "你好，我是你的本地语言学习助手。导入或下载 Gemma 4 E2B 模型后，我可以离线处理翻译、近义词、图片文字和英语口语练习。";
        addAssistantMessage(welcome, false);
        recordConversationMessage("assistant", welcome);
        loadModelIfAvailable();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(247, 244, 239));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(14), dp(14), dp(14), dp(10));
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(4), dp(2), dp(4), dp(10));
        main.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        ImageButton historyButton = makeToolbarIconBtn(R.drawable.ic_nav_menu, false);
        historyButton.setOnClickListener(v -> openHistoryPanel());
        titleRow.addView(historyButton, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView title = new TextView(this);
        title.setText("语言助手");
        title.setTextColor(Color.rgb(36, 53, 47));
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.leftMargin = dp(10);
        titleRow.addView(title, titleParams);

        Button importButton = makeCompactPillButton("模型", false);
        importButton.setOnClickListener(v -> showModelConfigDialog());
        titleRow.addView(importButton, new LinearLayout.LayoutParams(-2, dp(38)));

        modelStatus = new TextView(this);
        modelStatus.setText("正在检查本地模型");
        modelStatus.setTextColor(Color.rgb(98, 94, 84));
        modelStatus.setTextSize(13);
        header.addView(modelStatus, new LinearLayout.LayoutParams(-1, -2));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);
        chatList.setPadding(0, dp(6), 0, dp(6));
        scrollView.addView(chatList, new ScrollView.LayoutParams(-1, -2));
        main.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        imageStatus = new TextView(this);
        imageStatus.setTextColor(Color.rgb(83, 95, 88));
        imageStatus.setTextSize(12);
        imageStatus.setVisibility(View.GONE);
        imageStatus.setPadding(dp(12), dp(8), dp(12), dp(8));
        imageStatus.setBackground(makeRoundBg(Color.argb(186, 228, 235, 229), dp(14), Color.rgb(206, 218, 208), 1));
        main.addView(imageStatus, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(12), dp(10), dp(12), dp(10));
        composer.setBackground(makeRoundBg(Color.WHITE, dp(24), Color.rgb(226, 219, 207), 1));
        main.addView(composer, new LinearLayout.LayoutParams(-1, -2));

        inputBox = new EditText(this);
        inputBox.setHint("输入问题或继续追问");
        inputBox.setTextColor(Color.rgb(35, 43, 39));
        inputBox.setHintTextColor(Color.rgb(139, 132, 119));
        inputBox.setTextSize(15);
        inputBox.setTypeface(Typeface.DEFAULT);
        inputBox.setMinLines(1);
        inputBox.setMaxLines(4);
        inputBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        inputBox.setHorizontallyScrolling(false);
        inputBox.setVerticalScrollBarEnabled(true);
        inputBox.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        inputBox.setGravity(Gravity.TOP | Gravity.START);
        inputBox.setPadding(0, dp(2), 0, dp(4));
        inputBox.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, -2);
        composer.addView(inputBox, inputParams);

        // Toolbar row: left group [+, chat, call, image-chip] | spacer | right group [mic, send]
        LinearLayout toolbarRow = new LinearLayout(this);
        toolbarRow.setGravity(Gravity.CENTER_VERTICAL);
        toolbarRow.setPadding(0, dp(4), 0, 0);
        composer.addView(toolbarRow, new LinearLayout.LayoutParams(-1, -2));

        // Left: image upload
        ImageButton plusButton = makeToolbarIconBtn(android.R.drawable.ic_menu_add, false);
        plusButton.setOnClickListener(v -> showImageSourcePicker());
        toolbarRow.addView(plusButton, new LinearLayout.LayoutParams(dp(32), dp(32)));

        // Left: English conversation toggle
        conversationButton = makeToolbarIconBtn(android.R.drawable.ic_menu_info_details, false);
        conversationButton.setOnClickListener(v -> toggleConversationMode());
        LinearLayout.LayoutParams convoParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        convoParams.leftMargin = dp(6);
        toolbarRow.addView(conversationButton, convoParams);

        // Left: unrestricted normal LLM chat
        openChatButton = makeToolbarIconBtn(android.R.drawable.sym_action_chat, false);
        openChatButton.setOnClickListener(v -> toggleOpenChatMode());
        LinearLayout.LayoutParams openChatParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        openChatParams.leftMargin = dp(6);
        toolbarRow.addView(openChatButton, openChatParams);

        // Left: voice call toggle
        voiceCallButton = makeToolbarIconBtn(android.R.drawable.ic_menu_call, false);
        voiceCallButton.setOnClickListener(v -> openVoiceCallPanel());
        LinearLayout.LayoutParams callParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        callParams.leftMargin = dp(6);
        toolbarRow.addView(voiceCallButton, callParams);

        // Left: image thumbnail chip (hidden until image selected)
        selectedImageChip = new FrameLayout(this);
        selectedImageChip.setVisibility(View.GONE);
        selectedImageChip.setClipChildren(false);
        selectedImageChip.setBackground(makeRoundBg(Color.rgb(246, 241, 234), dp(14), Color.rgb(214, 205, 189), 1));
        selectedImagePreview = new ImageView(this);
        selectedImagePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER);
        selectedImageChip.addView(selectedImagePreview, previewParams);

        selectedImageRemove = new TextView(this);
        selectedImageRemove.setText("×");
        selectedImageRemove.setGravity(Gravity.CENTER);
        selectedImageRemove.setTextColor(Color.WHITE);
        selectedImageRemove.setTextSize(10);
        selectedImageRemove.setMinimumWidth(0);
        selectedImageRemove.setMinimumHeight(0);
        selectedImageRemove.setBackground(makeRoundBg(Color.rgb(190, 74, 74), dp(9), Color.rgb(190, 74, 74), 0));
        selectedImageRemove.setOnClickListener(v -> clearSelectedImage());
        FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.END | Gravity.TOP);
        removeParams.rightMargin = dp(-4);
        removeParams.topMargin = dp(-4);
        selectedImageChip.addView(selectedImageRemove, removeParams);
        LinearLayout.LayoutParams selectedParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        selectedParams.leftMargin = dp(6);
        toolbarRow.addView(selectedImageChip, selectedParams);

        // Flexible spacer pushes mic+send to the right
        View toolbarSpacer = new View(this);
        toolbarRow.addView(toolbarSpacer, new LinearLayout.LayoutParams(0, 0, 1));

        // Right: mic input
        micButton = makeToolbarIconBtn(android.R.drawable.ic_btn_speak_now, false);
        micButton.setOnClickListener(v -> startVoiceInput(VOICE_TARGET_COMPOSER));
        toolbarRow.addView(micButton, new LinearLayout.LayoutParams(dp(32), dp(32)));

        // Right: send (filled dark)
        sendButton = makeToolbarIconBtn(android.R.drawable.ic_menu_send, true);
        sendButton.setOnClickListener(v -> sendCurrentMessage());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(34), dp(32));
        sendParams.leftMargin = dp(6);
        toolbarRow.addView(sendButton, sendParams);

        buildOverlay();
        buildHistoryOverlay();
        buildVoiceCallOverlay();
        refreshConversationChip();
        refreshOpenChatChip();
        refreshVoiceCallChip();
        renderHistoryList();
        setContentView(root);
        applyTypefaceRecursively(root);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets != null ? insets.getSystemWindowInsetTop() : 0;
            int bottomInset = insets != null ? insets.getSystemWindowInsetBottom() : 0;
            main.setPadding(dp(14), Math.max(dp(22), topInset + dp(10)), dp(14), Math.max(dp(22), bottomInset + dp(10)));
            if (voiceCallOverlay != null) {
                voiceCallOverlay.setPadding(0, 0, 0, Math.max(dp(8), bottomInset));
            }
            if (historyOverlay != null) {
                historyOverlay.setPadding(0, 0, 0, Math.max(0, bottomInset - dp(4)));
            }
            return view.onApplyWindowInsets(insets);
        });
        root.requestApplyInsets();
    }

    private void buildHistoryOverlay() {
        historyOverlay = new FrameLayout(this);
        historyOverlay.setVisibility(View.GONE);
        root.addView(historyOverlay, new FrameLayout.LayoutParams(-1, -1));

        View backdrop = new View(this);
        backdrop.setBackgroundColor(Color.argb(96, 36, 53, 47));
        backdrop.setOnClickListener(v -> closeHistoryPanel());
        historyOverlay.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(20), dp(16), dp(16));
        panel.setBackground(makeRoundBg(Color.rgb(255, 251, 245), dp(24), Color.rgb(225, 216, 201), 1));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams((int) (getResources().getDisplayMetrics().widthPixels * 0.76f), -1, Gravity.START);
        panelParams.topMargin = dp(8);
        panelParams.bottomMargin = dp(8);
        panelParams.leftMargin = dp(8);
        historyOverlay.addView(panel, panelParams);

        LinearLayout historyHeader = new LinearLayout(this);
        historyHeader.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(historyHeader, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleCopy = new LinearLayout(this);
        titleCopy.setOrientation(LinearLayout.VERTICAL);
        historyHeader.addView(titleCopy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView kicker = new TextView(this);
        kicker.setText("聊天历史");
        kicker.setTextColor(Color.rgb(117, 110, 98));
        kicker.setTextSize(11);
        titleCopy.addView(kicker);

        TextView historyTitle = new TextView(this);
        historyTitle.setText("最近记录");
        historyTitle.setTextColor(Color.rgb(36, 53, 47));
        historyTitle.setTypeface(Typeface.DEFAULT_BOLD);
        historyTitle.setTextSize(19);
        titleCopy.addView(historyTitle);

        Button newChatButton = makeCompactPillButton("新对话", false);
        newChatButton.setOnClickListener(v -> {
            startFreshChat();
            closeHistoryPanel();
            Toast.makeText(this, "已开始新对话", Toast.LENGTH_SHORT).show();
        });
        historyHeader.addView(newChatButton, new LinearLayout.LayoutParams(-2, dp(36)));

        ScrollView historyScroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.topMargin = dp(14);
        panel.addView(historyScroll, scrollParams);

        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        historyScroll.addView(historyList, new ScrollView.LayoutParams(-1, -2));

        historyEmptyView = new TextView(this);
        historyEmptyView.setText("还没有聊天记录。发送几条消息后，这里会自动出现历史。\n\n右侧删除按钮支持确认删除。记录多了会在这里滚动，不会跑出屏幕。");
        historyEmptyView.setTextColor(Color.rgb(111, 107, 93));
        historyEmptyView.setTextSize(13);
        historyEmptyView.setLineSpacing(dp(4), 1.0f);
        historyList.addView(historyEmptyView, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildVoiceCallOverlay() {
        voiceCallOverlay = new FrameLayout(this);
        voiceCallOverlay.setVisibility(View.GONE);
        voiceCallOverlay.setBackgroundColor(Color.argb(246, 244, 238, 229));
        root.addView(voiceCallOverlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(18), dp(18), dp(18));
        voiceCallOverlay.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        shell.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        topBar.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView kicker = new TextView(this);
        kicker.setText("语音通话");
        kicker.setTextColor(Color.rgb(117, 110, 98));
        kicker.setTextSize(11);
        copy.addView(kicker);

        TextView title = new TextView(this);
        title.setText("英语口语实时对练");
        title.setTextColor(Color.rgb(36, 53, 47));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(20);
        copy.addView(title);

        Button closeButton = makeCompactPillButton("退出", false);
        closeButton.setOnClickListener(v -> closeVoiceCallPanel());
        topBar.addView(closeButton, new LinearLayout.LayoutParams(-2, dp(36)));

        voiceCallStatusView = new TextView(this);
        voiceCallStatusView.setText("使用本地语音识别 + 本地离线 TTS（当前音色：" + currentTtsVoiceDisplayName() + "）。你说完会直接发给 LLM，助手会语音回复。");
        voiceCallStatusView.setTextColor(Color.rgb(111, 107, 93));
        voiceCallStatusView.setTextSize(13);
        voiceCallStatusView.setLineSpacing(dp(4), 1.0f);
        voiceCallStatusView.setPadding(dp(14), dp(12), dp(14), dp(12));
        voiceCallStatusView.setBackground(makeRoundBg(Color.argb(190, 255, 252, 247), dp(16), Color.rgb(217, 206, 191), 1));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(14);
        shell.addView(voiceCallStatusView, statusParams);

        voiceCallScrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.topMargin = dp(14);
        shell.addView(voiceCallScrollView, scrollParams);

        voiceCallTranscriptList = new LinearLayout(this);
        voiceCallTranscriptList.setOrientation(LinearLayout.VERTICAL);
        voiceCallScrollView.addView(voiceCallTranscriptList, new ScrollView.LayoutParams(-1, -2));

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(-1, -2);
        dockParams.topMargin = dp(16);
        shell.addView(dock, dockParams);

        LinearLayout hintRow = new LinearLayout(this);
        hintRow.setGravity(Gravity.CENTER_VERTICAL);
        dock.addView(hintRow, new LinearLayout.LayoutParams(-2, -2));

        View dot = new View(this);
        dot.setBackground(makeRoundBg(Color.rgb(134, 163, 125), dp(4), Color.rgb(134, 163, 125), 0));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        hintRow.addView(dot, dotParams);

        voiceCallHintView = new TextView(this);
        voiceCallHintView.setText("点击下方麦克风开始说话");
        voiceCallHintView.setTextColor(Color.rgb(111, 107, 93));
        voiceCallHintView.setTextSize(13);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-2, -2);
        hintParams.leftMargin = dp(10);
        hintRow.addView(voiceCallHintView, hintParams);

        voiceCallMicButton = new ImageButton(this);
        voiceCallMicButton.setImageResource(android.R.drawable.ic_btn_speak_now);
        voiceCallMicButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        voiceCallMicButton.setPadding(dp(18), dp(18), dp(18), dp(18));
        voiceCallMicButton.setColorFilter(Color.rgb(36, 53, 47));
        voiceCallMicButton.setBackground(makeRoundBg(Color.rgb(255, 252, 247), dp(41), Color.rgb(214, 205, 189), 1));
        voiceCallMicButton.setOnClickListener(v -> startVoiceInput(VOICE_TARGET_CALL));
        LinearLayout.LayoutParams micParams = new LinearLayout.LayoutParams(dp(82), dp(82));
        micParams.topMargin = dp(14);
        dock.addView(voiceCallMicButton, micParams);
    }

    private void refreshVoiceCallChip() {
        if (voiceCallButton == null) return;
        boolean active = voiceCallOpen;
        voiceCallButton.setBackground(makeRoundBg(
                active ? Color.rgb(36, 53, 47) : Color.rgb(232, 241, 234), dp(16),
                active ? Color.rgb(36, 53, 47) : Color.rgb(195, 212, 200), 1));
        voiceCallButton.setColorFilter(active ? Color.WHITE : Color.rgb(49, 80, 69));
    }

    private void buildOverlay() {
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(210, 247, 244, 239));
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(24), dp(24), dp(24), dp(24));
        panel.setBackground(makeRoundBg(Color.WHITE, dp(22), Color.rgb(225, 216, 201), 1));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        params.leftMargin = dp(22);
        params.rightMargin = dp(22);
        overlay.addView(panel, params);

        overlayTitle = new TextView(this);
        overlayTitle.setTextColor(Color.rgb(36, 53, 47));
        overlayTitle.setTextSize(19);
        overlayTitle.setTypeface(Typeface.DEFAULT_BOLD);
        overlayTitle.setGravity(Gravity.CENTER);
        panel.addView(overlayTitle, new LinearLayout.LayoutParams(-1, -2));

        overlayMessage = new TextView(this);
        overlayMessage.setTextColor(Color.rgb(97, 91, 80));
        overlayMessage.setTextSize(14);
        overlayMessage.setGravity(Gravity.CENTER);
        overlayMessage.setPadding(0, dp(10), 0, dp(16));
        panel.addView(overlayMessage, new LinearLayout.LayoutParams(-1, -2));

        overlayDownloadButton = makePillButton("下载 Gemma 4 E2B", true);
        overlayDownloadButton.setOnClickListener(v -> downloadGemma4Model());
        panel.addView(overlayDownloadButton, new LinearLayout.LayoutParams(-1, dp(46)));

        overlayButton = makePillButton("选择本地模型文件", false);
        overlayButton.setOnClickListener(v -> openModelPicker());
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(-1, dp(46));
        importParams.topMargin = dp(8);
        panel.addView(overlayButton, importParams);
    }

    private void loadModelIfAvailable() {
        if (!ModelStore.hasModel(this)) {
            modelStatus.setText("未导入模型：可先体验界面，AI 生成需导入或下载 Gemma 4 E2B");
            showModelNeededOverlay();
            return;
        }
        showLoadingOverlay("正在加载 Gemma 4 E2B", "第一次会稍慢，模型载入后就能进行文本、图片和英语对话练习。", false);
        executor.execute(() -> {
            try {
                llmEngine.load(ModelStore.modelFile(this).getAbsolutePath(), configAccelerator, configMaxTokens);
                mainHandler.post(() -> {
                    hideOverlay();
                    modelStatus.setText("Gemma 4 E2B 已加载到 " + llmEngine.getActiveBackendName() + "，本地推理就绪");
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    modelStatus.setText("模型加载失败：" + compactError(ex));
                    showLoadingOverlay("模型没有加载成功", compactError(ex) + "\n可以重新导入 .task 或 .litertlm 文件。", true);
                });
            }
        });
    }

    private void showModelNeededOverlay() {
        showLoadingOverlay("需要 Gemma 4 E2B", "可直接下载 litert-community/gemma-4-E2B-it-litert-lm 的通用 LiteRT-LM 文件，也可以选择你已经下载好的 gemma-4-E2B-it.litertlm。", true);
    }

    private void showLoadingOverlay(String title, String message, boolean showButton) {
        overlay.setVisibility(View.VISIBLE);
        overlayTitle.setText(title);
        overlayMessage.setText(message);
        overlayButton.setVisibility(showButton ? View.VISIBLE : View.GONE);
        overlayDownloadButton.setVisibility(showButton ? View.VISIBLE : View.GONE);
        loadingDotsRunning = !showButton;
        dotCount = 0;
        if (loadingDotsRunning) animateLoadingTitle(title);
    }

    private void downloadGemma4Model() {
        showLoadingOverlay("正在下载 Gemma 4 E2B", "准备连接 Hugging Face。模型约 2.58 GB，请保持网络稳定。", false);
        executor.execute(() -> {
            try {
                ModelStore.downloadGemma4(this, (copiedBytes, totalBytes) -> mainHandler.post(() -> {
                    int percent = totalBytes > 0 ? (int) Math.min(99, (copiedBytes * 100L) / totalBytes) : 0;
                    overlayMessage.setText("正在下载 " + percent + "%\n" + formatBytes(copiedBytes) + " / " + formatBytes(totalBytes));
                    modelStatus.setText("正在下载 Gemma 4 E2B：" + percent + "%");
                }));
                mainHandler.post(() -> {
                    modelStatus.setText("Gemma 4 E2B 下载完成，开始加载");
                    loadModelIfAvailable();
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    modelStatus.setText("模型下载失败：" + compactError(ex));
                    showLoadingOverlay("下载失败", compactError(ex) + "\n你也可以在 Google AI Edge Gallery 或 Hugging Face 下载 gemma-4-E2B-it.litertlm 后手动选择文件。", true);
                });
            }
        });
    }

    private void animateLoadingTitle(String baseTitle) {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!loadingDotsRunning || overlay.getVisibility() != View.VISIBLE) return;
                dotCount = (dotCount + 1) % 4;
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < dotCount; i++) dots.append('.');
                overlayTitle.setText(baseTitle + dots);
                mainHandler.postDelayed(this, 420);
            }
        }, 420);
    }

    private void hideOverlay() {
        loadingDotsRunning = false;
        overlay.setVisibility(View.GONE);
    }

    private void openHistoryPanel() {
        historyOverlay.setVisibility(View.VISIBLE);
        renderHistoryList();
    }

    private void closeHistoryPanel() {
        historyOverlay.setVisibility(View.GONE);
    }

    private void openVoiceCallPanel() {
        hideKeyboard();
        if (openChatMode) {
            openChatMode = false;
            refreshOpenChatChip();
        }
        if (!conversationMode) {
            restoreConversationModeAfterCall = true;
            conversationMode = true;
            refreshConversationChip();
        } else {
            restoreConversationModeAfterCall = false;
        }
        voiceCallOpen = true;
        refreshVoiceCallChip();
        voiceCallOverlay.setVisibility(View.VISIBLE);
        if (voiceCallStatusView != null) {
            voiceCallStatusView.setText("使用本地语音识别 + 本地 TTS。你说完会直接发给 LLM，助手会语音回复。");
        }
        if (voiceCallHintView != null) {
            voiceCallHintView.setText("点击下方麦克风开始说话");
        }
        refreshVoiceCallTranscript();
    }

    private void closeVoiceCallPanel() {
        voiceCallOpen = false;
        refreshVoiceCallChip();
        if (voiceInputActive && speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {
            }
        }
        setVoiceInputActive(false);
        if (sherpaOnnxTts != null) sherpaOnnxTts.stop();
        if (voiceCallOverlay != null) voiceCallOverlay.setVisibility(View.GONE);
        if (restoreConversationModeAfterCall && conversationMode) {
            conversationMode = false;
            refreshConversationChip();
        }
        restoreConversationModeAfterCall = false;
    }

    private void refreshVoiceCallTranscript() {
        if (voiceCallTranscriptList == null) return;
        voiceCallTranscriptList.removeAllViews();

        if (currentConversationMessages.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("语音通话已接通。点击下方麦克风开始说话，识别后的文本和助手回复都会显示在这里。");
            empty.setTextColor(Color.rgb(111, 107, 93));
            empty.setTextSize(14);
            empty.setLineSpacing(dp(4), 1.0f);
            empty.setPadding(dp(14), dp(14), dp(14), dp(14));
            empty.setBackground(makeRoundBg(Color.argb(190, 255, 252, 247), dp(18), Color.rgb(214, 205, 189), 1));
            voiceCallTranscriptList.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        for (ChatMessage message : currentConversationMessages) {
            voiceCallTranscriptList.addView(buildVoiceCallTurn(message), new LinearLayout.LayoutParams(-1, -2));
        }
        mainHandler.postDelayed(() -> {
            if (voiceCallScrollView != null) voiceCallScrollView.fullScroll(View.FOCUS_DOWN);
        }, 60);
    }

    private View buildVoiceCallTurn(ChatMessage message) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(12), dp(13), dp(12));
        boolean user = "user".equals(message.role);
        card.setBackground(makeRoundBg(user ? Color.rgb(36, 53, 47) : Color.argb(210, 255, 252, 247), dp(18), user ? Color.rgb(36, 53, 47) : Color.rgb(214, 205, 189), 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);

        TextView role = new TextView(this);
        role.setText(user ? "YOU" : "TUTOR");
        role.setTextColor(user ? Color.argb(180, 248, 242, 234) : Color.rgb(117, 110, 98));
        role.setTextSize(11);
        role.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        card.addView(role);

        TextView copy = new TextView(this);
        copy.setTextColor(user ? Color.rgb(248, 242, 234) : Color.rgb(35, 43, 39));
        copy.setTextSize(14);
        copy.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-1, -2);
        copyParams.topMargin = dp(6);
        if (user) {
            copy.setText(message.text);
        } else {
            renderRichText(copy, message.text, false);
        }
        card.addView(copy, copyParams);

        TextView meta = new TextView(this);
        meta.setText(user ? "你的实时转写" : "Gemma 文本 + 本地 TTS 播放");
        meta.setTextColor(user ? Color.argb(166, 248, 242, 234) : Color.rgb(111, 107, 93));
        meta.setTextSize(11);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(6);
        card.addView(meta, metaParams);
        applyTypefaceRecursively(card);
        return card;
    }

    private void openModelPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_MODEL);
    }

    private void showImageSourcePicker() {
        String[] options = new String[]{"从相册选取", "直接拍摄"};
        new AlertDialog.Builder(this)
                .setTitle("选择图片来源")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openImagePicker();
                    } else {
                        openCameraCapture();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    private void openCameraCapture() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        try {
            File directory = new File(getCacheDir(), "camera");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("无法创建相机缓存目录");
            }
            File photoFile = File.createTempFile("capture-", ".jpg", directory);
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CAPTURE_IMAGE);
        } catch (Exception ex) {
            Toast.makeText(this, "无法打开相机：" + compactError(ex), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendCurrentMessage() {
        String text = inputBox.getText().toString().trim();
        boolean hasImage = selectedImage != null;
        if (text.isEmpty() && !hasImage) {
            Toast.makeText(this, "先输入内容或选择图片", Toast.LENGTH_SHORT).show();
            return;
        }
        hideKeyboard();
        inputBox.setText("");
        Bitmap imageForRequest = selectedImage;
        selectedImage = null;
        updateSelectedImageChip();
        imageStatus.setVisibility(View.GONE);
        mainHandler.removeCallbacks(hideImageStatusRunnable);

        String userText;
        if (!text.isEmpty()) {
            userText = text;
        } else if (openChatMode) {
            userText = "请先理解这张图片并直接回答。";
        } else {
            userText = "请翻译并解释这张图片。";
        }
        String promptTranscript = trimTranscript();
        addUserMessage(userText, imageForRequest);
        recordConversationMessage("user", userText);
        if (voiceCallOpen && voiceCallStatusView != null) {
            voiceCallStatusView.setText("已发送给 Gemma，正在生成并准备语音回复。");
        }
        TextView pending = addAssistantMessage("", true);
        pending.setText(thinkingEnabled ? "正在思考..." : "正在生成...");

        executor.execute(() -> {
            try {
                if (llmEngine.isLoaded()) {
                    llmEngine.configure(thinkingEnabled, configTopK, configTopP, configTemperature);
                    String systemPrompt, userMessage;
                    if (openChatMode) {
                        systemPrompt = TutorPrompts.getOpenChatSystemPrompt(imageForRequest != null);
                        userMessage = TutorPrompts.getOpenChatUserMessage(userText);
                    } else if (conversationMode) {
                        systemPrompt = TutorPrompts.getConversationSystemPrompt();
                        userMessage = TutorPrompts.getConversationUserMessage(userText);
                    } else {
                        systemPrompt = TutorPrompts.getStudySystemPrompt();
                        userMessage = TutorPrompts.getStudyUserMessage(userText, imageForRequest != null);
                    }
                    llmEngine.generateStreaming(systemPrompt, userMessage, imageForRequest, new LlmEngine.StreamListener() {
                        @Override
                        public void onChunk(String textChunk) {
                            String partial = sanitizeAssistantReply(textChunk);
                            mainHandler.post(() -> updateAssistantStreaming(pending, partial));
                        }

                        @Override
                        public void onComplete(String fullText) {
                            String finalAnswer = sanitizeAssistantReply(fullText);
                            if (conversationMode && !finalAnswer.trim().toLowerCase(Locale.US).startsWith("do you mean")) {
                                finalAnswer = "Do you mean \"" + userText + "\"? " + finalAnswer;
                            }
                            String finalMessage = finalAnswer;
                            mainHandler.post(() -> finishAssistantResponse(pending, userText, finalMessage));
                        }

                        @Override
                        public void onError(Exception ex) {
                            String answer = "这次生成失败：" + compactError(ex) + "\n\n你可以重新导入 Gemma 4 E2B 的 LiteRT-LM 模型文件，或先继续使用界面模板。";
                            mainHandler.post(() -> finishAssistantResponse(pending, userText, answer));
                        }
                    });
                } else {
                    String answer = openChatMode
                            ? TutorPrompts.buildFallbackOpenChatAnswer(text, imageForRequest != null)
                            : TutorPrompts.buildFallbackStudyAnswer(userText, conversationMode);
                    mainHandler.post(() -> finishAssistantResponse(pending, userText, answer));
                }
            } catch (Exception ex) {
                String answer = "这次生成失败：" + compactError(ex) + "\n\n你可以重新导入 Gemma 4 E2B 的 LiteRT-LM 模型文件，或先继续使用界面模板。";
                mainHandler.post(() -> finishAssistantResponse(pending, userText, answer));
            }
        });
    }

    private void toggleConversationMode() {
        if (!conversationMode) {
            startFreshChat(
                    "已进入日常英语对话训练。你可以说中文或英文，我会先用 Do you mean 改写成自然英语，再继续对话。",
                    true,
                    false
            );
        } else {
            startFreshChat("已回到学习解析模式。你可以继续做翻译、讲解、看图和语言学习。", false, false);
        }
    }

    private void refreshConversationChip() {
        if (conversationButton == null) return;
        conversationButton.setBackground(makeRoundBg(
                conversationMode ? Color.rgb(36, 53, 47) : Color.rgb(246, 241, 234), dp(16),
                Color.rgb(214, 205, 189), 1));
        conversationButton.setColorFilter(conversationMode ? Color.WHITE : Color.rgb(36, 53, 47));
    }

    private void toggleOpenChatMode() {
        if (!openChatMode) {
            startFreshChat(
                    "已进入自由聊天。现在模型会按正常多模态 LLM 方式直接回答，你可以普通聊天、上传图片，也可以点麦克风输入。",
                    false,
                    true
            );
        } else {
            startFreshChat("已回到学习解析模式。你可以继续做翻译、讲解、看图和语言学习。", false, false);
        }
    }

    private void refreshOpenChatChip() {
        if (openChatButton == null) return;
        openChatButton.setBackground(makeRoundBg(
                openChatMode ? Color.rgb(36, 53, 47) : Color.rgb(246, 241, 234), dp(16),
                Color.rgb(214, 205, 189), 1));
        openChatButton.setColorFilter(openChatMode ? Color.WHITE : Color.rgb(36, 53, 47));
    }

    private void updateSelectedImageChip() {
        if (selectedImageChip == null || selectedImagePreview == null) return;
        if (selectedImage == null) {
            selectedImageChip.setVisibility(View.GONE);
            return;
        }
        selectedImagePreview.setImageBitmap(selectedImage);
        selectedImageChip.setVisibility(View.VISIBLE);
    }

    private void clearSelectedImage() {
        selectedImage = null;
        updateSelectedImageChip();
        imageStatus.setVisibility(View.GONE);
        mainHandler.removeCallbacks(hideImageStatusRunnable);
    }

    private void startVoiceInput(int target) {
        voiceInputTarget = target;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        if (speechRecognizer == null) {
            Toast.makeText(this, "当前系统没有可用语音识别服务", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, (conversationMode || voiceCallOpen) ? "en-US" : Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        setVoiceInputActive(true);
        speechRecognizer.startListening(intent);
    }

    private void setVoiceInputActive(boolean active) {
        voiceInputActive = active;
        mainHandler.removeCallbacks(voiceIndicatorRunnable);
        if (active) {
            dotCount = 0;
            if (voiceInputTarget == VOICE_TARGET_CALL) {
                if (voiceCallMicButton != null) {
                    voiceCallMicButton.setBackground(makeRoundBg(Color.rgb(225, 236, 228), dp(41), Color.rgb(170, 191, 176), 1));
                    voiceCallMicButton.setAlpha(1f);
                }
                if (voiceCallStatusView != null) {
                    voiceCallStatusView.setText("识别中。结束后会直接把文本发给 Gemma，然后用本地 TTS 播放回复。");
                }
            } else if (micButton != null) {
                micButton.setBackground(makeRoundBg(Color.rgb(36, 53, 47), dp(17), Color.rgb(36, 53, 47), 1));
                micButton.setColorFilter(Color.WHITE);
                micButton.setAlpha(1f);
            }
            mainHandler.post(voiceIndicatorRunnable);
        } else {
            if (voiceCallMicButton != null) {
                voiceCallMicButton.setBackground(makeRoundBg(Color.rgb(255, 252, 247), dp(41), Color.rgb(214, 205, 189), 1));
                voiceCallMicButton.setAlpha(1f);
            }
            if (micButton != null) {
                micButton.setBackground(makeRoundBg(Color.rgb(248, 244, 238), dp(17), Color.rgb(214, 205, 189), 1));
                micButton.setColorFilter(Color.rgb(36, 53, 47));
                micButton.setAlpha(1f);
            }
            if (voiceCallOpen && voiceCallHintView != null) {
                voiceCallHintView.setText("回复已播报，点击麦克风继续");
            }
            modelStatus.setText(llmEngine.isLoaded() ? "Gemma 4 E2B 已加载到内存，本地推理就绪" : "未导入模型：可先体验界面，AI 生成需导入或下载 Gemma 4 E2B");
        }
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { setVoiceInputActive(true); }
            @Override public void onBeginningOfSpeech() { setVoiceInputActive(true); }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { setVoiceInputActive(false); }
            @Override public void onError(int error) {
                setVoiceInputActive(false);
                modelStatus.setText("语音输入未完成");
                if (voiceCallOpen && voiceCallStatusView != null) {
                    voiceCallStatusView.setText("这次语音识别没有完成，你可以直接再说一次。");
                }
            }
            @Override public void onEvent(int eventType, Bundle params) {}
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    inputBox.setText(matches.get(0));
                    inputBox.setSelection(inputBox.length());
                    if (voiceCallOpen && voiceCallStatusView != null) {
                        voiceCallStatusView.setText("识别到：" + matches.get(0));
                    }
                }
            }
            @Override public void onResults(Bundle results) {
                setVoiceInputActive(false);
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    inputBox.setText(matches.get(0));
                    inputBox.setSelection(inputBox.length());
                    if (voiceCallOpen && voiceCallHintView != null) {
                        voiceCallHintView.setText("已识别并发送");
                    }
                    sendCurrentMessage();
                }
            }
        });
    }

    private void setupTts() { /* replaced by SherpaOnnxTts */ }

    private SherpaOnnxTts.VoiceProfile resolveSelectedTtsVoiceProfile() {
        SherpaOnnxTts.VoiceProfile profile = SherpaOnnxTts.VoiceProfile.fromKey(selectedTtsVoiceKey);
        selectedTtsVoiceKey = profile.getKey();
        return profile;
    }

    private String currentTtsVoiceDisplayName() {
        return resolveSelectedTtsVoiceProfile().getDisplayName();
    }

    private void applySelectedTtsVoice() {
        if (sherpaOnnxTts != null) {
            sherpaOnnxTts.setVoiceProfile(resolveSelectedTtsVoiceProfile());
        }
        if (voiceCallStatusView != null) {
            voiceCallStatusView.setText("使用本地语音识别 + 本地离线 TTS（当前音色：" + currentTtsVoiceDisplayName() + "）。你说完会直接发给 LLM，助手会语音回复。");
        }
    }

    private void loadUiPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        thinkingEnabled = prefs.getBoolean(PREF_THINKING_ENABLED, false);
        configAccelerator = prefs.getString(PREF_ACCELERATOR, "GPU");
        configMaxTokens = prefs.getInt(PREF_MAX_TOKENS, 4096);
        configTopK = prefs.getInt(PREF_TOP_K, 64);
        configTopP = prefs.getFloat(PREF_TOP_P, 0.95f);
        configTemperature = prefs.getFloat(PREF_TEMPERATURE, 1.0f);
        selectedTtsVoiceKey = prefs.getString(PREF_TTS_VOICE, SherpaOnnxTts.VoiceProfile.PIPER_AMY.getKey());
    }

    private void persistUiPreferences() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_THINKING_ENABLED, thinkingEnabled)
                .putString(PREF_ACCELERATOR, configAccelerator)
                .putInt(PREF_MAX_TOKENS, configMaxTokens)
                .putInt(PREF_TOP_K, configTopK)
                .putFloat(PREF_TOP_P, configTopP)
                .putFloat(PREF_TEMPERATURE, configTemperature)
                .putString(PREF_TTS_VOICE, selectedTtsVoiceKey)
                .apply();
    }

    private void loadUiTypefaces() {
        uiRegularTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_cjk_sc_regular);
        uiMediumTypeface = ResourcesCompat.getFont(this, R.font.noto_sans_cjk_sc_medium);
        if (uiRegularTypeface == null) uiRegularTypeface = Typeface.SANS_SERIF;
        if (uiMediumTypeface == null) uiMediumTypeface = uiRegularTypeface;
    }

    private void applyTypeface(View view) {
        if (!(view instanceof TextView)) return;
        TextView textView = (TextView) view;
        Typeface current = textView.getTypeface();
        boolean bold = current != null && current.isBold();
        textView.setTypeface(bold ? uiMediumTypeface : uiRegularTypeface);
    }

    private void applyTypefaceRecursively(View view) {
        applyTypeface(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            applyTypefaceRecursively(group.getChildAt(index));
        }
    }

    private void showModelConfigDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this).create();

        ScrollView dialogScroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(24), dp(22), dp(20));
        panel.setBackgroundColor(Color.rgb(222, 232, 243));
        dialogScroll.addView(panel, new ScrollView.LayoutParams(-1, -2));

        // Title
        TextView titleText = new TextView(this);
        titleText.setText("Configurations");
        titleText.setTextColor(Color.rgb(15, 25, 45));
        titleText.setTextSize(22);
        titleText.setTypeface(uiMediumTypeface);
        panel.addView(titleText, new LinearLayout.LayoutParams(-1, -2));

        // Max tokens
        final int[] maxTokRef = {configMaxTokens};
        panel.addView(makeConfigSliderRow("Max tokens", 512, 8192, configMaxTokens, maxTokRef));

        // TopK
        final int[] topKRef = {configTopK};
        panel.addView(makeConfigSliderRow("TopK", 1, 100, configTopK, topKRef));

        // TopP (float 0-1)
        final float[] topPRef = {configTopP};
        panel.addView(makeConfigFloatSliderRow("TopP", 0f, 1f, configTopP, topPRef));

        // Temperature (float 0-2)
        final float[] tempRef = {configTemperature};
        panel.addView(makeConfigFloatSliderRow("Temperature", 0f, 2f, configTemperature, tempRef));

        // Accelerator
        LinearLayout accelSection = new LinearLayout(this);
        accelSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams accelSectionParams = new LinearLayout.LayoutParams(-1, -2);
        accelSectionParams.topMargin = dp(18);
        panel.addView(accelSection, accelSectionParams);

        TextView accelLabel = new TextView(this);
        accelLabel.setText("Accelerator");
        accelLabel.setTextColor(Color.rgb(15, 25, 45));
        accelLabel.setTextSize(16);
        accelLabel.setTypeface(uiMediumTypeface);
        accelSection.addView(accelLabel);

        LinearLayout accelRow = new LinearLayout(this);
        LinearLayout.LayoutParams accelRowParams = new LinearLayout.LayoutParams(-2, dp(40));
        accelRowParams.topMargin = dp(8);
        accelSection.addView(accelRow, accelRowParams);

        final String[] acceleratorRef = {configAccelerator};

        Button gpuBtn = new Button(this);
        gpuBtn.setAllCaps(false);
        gpuBtn.setText("GPU");
        gpuBtn.setTextSize(14);
        gpuBtn.setPadding(dp(16), 0, dp(16), 0);
        accelRow.addView(gpuBtn, new LinearLayout.LayoutParams(-2, dp(40)));

        Button cpuBtn = new Button(this);
        cpuBtn.setAllCaps(false);
        cpuBtn.setText("CPU");
        cpuBtn.setTextSize(14);
        cpuBtn.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams cpuBtnParams = new LinearLayout.LayoutParams(-2, dp(40));
        cpuBtnParams.leftMargin = dp(8);
        accelRow.addView(cpuBtn, cpuBtnParams);

        Runnable refreshAcceleratorButtons = () -> {
            boolean gpuSelected = "GPU".equalsIgnoreCase(acceleratorRef[0]);
            styleConfigSegmentButton(gpuBtn, gpuSelected);
            styleConfigSegmentButton(cpuBtn, !gpuSelected);
            gpuBtn.setText(gpuSelected ? "\u2713  GPU" : "GPU");
            cpuBtn.setText(!gpuSelected ? "\u2713  CPU" : "CPU");
        };
        gpuBtn.setOnClickListener(v -> {
            acceleratorRef[0] = "GPU";
            refreshAcceleratorButtons.run();
        });
        cpuBtn.setOnClickListener(v -> {
            acceleratorRef[0] = "CPU";
            refreshAcceleratorButtons.run();
        });
        refreshAcceleratorButtons.run();

        LinearLayout ttsSection = new LinearLayout(this);
        ttsSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ttsSectionParams = new LinearLayout.LayoutParams(-1, -2);
        ttsSectionParams.topMargin = dp(18);
        panel.addView(ttsSection, ttsSectionParams);

        TextView ttsLabel = new TextView(this);
        ttsLabel.setText("English TTS voice");
        ttsLabel.setTextColor(Color.rgb(15, 25, 45));
        ttsLabel.setTextSize(16);
        ttsLabel.setTypeface(uiMediumTypeface);
        ttsSection.addView(ttsLabel);

        final String[] ttsVoiceRef = {selectedTtsVoiceKey};

        LinearLayout ttsRow = new LinearLayout(this);
        LinearLayout.LayoutParams ttsRowParams = new LinearLayout.LayoutParams(-2, dp(40));
        ttsRowParams.topMargin = dp(8);
        ttsSection.addView(ttsRow, ttsRowParams);

        Button amyBtn = new Button(this);
        amyBtn.setAllCaps(false);
        amyBtn.setText("Amy");
        amyBtn.setTextSize(14);
        amyBtn.setPadding(dp(16), 0, dp(16), 0);
        ttsRow.addView(amyBtn, new LinearLayout.LayoutParams(-2, dp(40)));

        Button sarahBtn = new Button(this);
        sarahBtn.setAllCaps(false);
        sarahBtn.setText("af_sarah");
        sarahBtn.setTextSize(14);
        sarahBtn.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams sarahBtnParams = new LinearLayout.LayoutParams(-2, dp(40));
        sarahBtnParams.leftMargin = dp(8);
        ttsRow.addView(sarahBtn, sarahBtnParams);

        Runnable refreshTtsButtons = () -> {
            boolean amySelected = SherpaOnnxTts.VoiceProfile.PIPER_AMY.getKey().equals(ttsVoiceRef[0]);
            styleConfigSegmentButton(amyBtn, amySelected);
            styleConfigSegmentButton(sarahBtn, !amySelected);
            amyBtn.setText(amySelected ? "\u2713  Amy" : "Amy");
            sarahBtn.setText(!amySelected ? "\u2713  af_sarah" : "af_sarah");
        };
        amyBtn.setOnClickListener(v -> {
            ttsVoiceRef[0] = SherpaOnnxTts.VoiceProfile.PIPER_AMY.getKey();
            refreshTtsButtons.run();
        });
        sarahBtn.setOnClickListener(v -> {
            ttsVoiceRef[0] = SherpaOnnxTts.VoiceProfile.KOKORO_AF_SARAH.getKey();
            refreshTtsButtons.run();
        });
        refreshTtsButtons.run();

        TextView ttsHint = new TextView(this);
        ttsHint.setText("Amy 是当前 Piper 音色；af_sarah 使用 Kokoro 多说话人模型，听感会更明显区别于系统 TTS。\n切换音色后，新的语音回复会立即使用所选离线模型。\n注意：af_sarah 会显著增大 APK 体积。\n");
        ttsHint.setTextColor(Color.rgb(60, 80, 120));
        ttsHint.setTextSize(12);
        LinearLayout.LayoutParams ttsHintParams = new LinearLayout.LayoutParams(-1, -2);
        ttsHintParams.topMargin = dp(10);
        ttsSection.addView(ttsHint, ttsHintParams);

        // Enable thinking toggle
        LinearLayout thinkSection = new LinearLayout(this);
        thinkSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams thinkSectionParams = new LinearLayout.LayoutParams(-1, -2);
        thinkSectionParams.topMargin = dp(18);
        panel.addView(thinkSection, thinkSectionParams);

        TextView thinkLabel = new TextView(this);
        thinkLabel.setText("Enable thinking");
        thinkLabel.setTextColor(Color.rgb(15, 25, 45));
        thinkLabel.setTextSize(16);
        thinkLabel.setTypeface(uiMediumTypeface);
        thinkSection.addView(thinkLabel);

        Switch thinkSwitch = new Switch(this);
        thinkSwitch.setChecked(thinkingEnabled);
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(-2, -2);
        switchParams.topMargin = dp(8);
        thinkSection.addView(thinkSwitch, switchParams);

        // Model file actions
        LinearLayout modelRow = new LinearLayout(this);
        LinearLayout.LayoutParams modelRowParams = new LinearLayout.LayoutParams(-1, -2);
        modelRowParams.topMargin = dp(20);
        panel.addView(modelRow, modelRowParams);

        Button downloadBtn = makeCompactPillButton("\u4e0b\u8f7d\u6a21\u578b", false);
        downloadBtn.setOnClickListener(v -> { dialog.dismiss(); downloadGemma4Model(); });
        modelRow.addView(downloadBtn, new LinearLayout.LayoutParams(0, dp(38), 1));

        Button importBtn = makeCompactPillButton("\u5bfc\u5165\u6587\u4ef6", false);
        importBtn.setOnClickListener(v -> { dialog.dismiss(); openModelPicker(); });
        LinearLayout.LayoutParams importBtnParams = new LinearLayout.LayoutParams(0, dp(38), 1);
        importBtnParams.leftMargin = dp(8);
        modelRow.addView(importBtn, importBtnParams);

        // Footer buttons: Cancel | OK
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(20);
        panel.addView(footer, footerParams);

        Button cancelBtn = new Button(this);
        cancelBtn.setAllCaps(false);
        cancelBtn.setText("Cancel");
        cancelBtn.setTextColor(Color.rgb(25, 90, 210));
        cancelBtn.setTextSize(15);
        cancelBtn.setBackground(null);
        cancelBtn.setPadding(dp(8), 0, dp(8), 0);
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        footer.addView(cancelBtn, new LinearLayout.LayoutParams(-2, dp(44)));

        Button okBtn = new Button(this);
        okBtn.setAllCaps(false);
        okBtn.setText("OK");
        okBtn.setTextColor(Color.WHITE);
        okBtn.setTextSize(15);
        okBtn.setBackground(makeRoundBg(Color.rgb(25, 90, 210), dp(22), Color.rgb(25, 90, 210), 0));
        okBtn.setPadding(dp(20), 0, dp(20), 0);
        okBtn.setOnClickListener(v -> {
            boolean shouldReloadModel = !safeEquals(configAccelerator, acceleratorRef[0]) || configMaxTokens != maxTokRef[0];
            boolean shouldSwitchTtsVoice = !safeEquals(selectedTtsVoiceKey, ttsVoiceRef[0]);
            configAccelerator = acceleratorRef[0];
            configMaxTokens = maxTokRef[0];
            configTopK = topKRef[0];
            configTopP = topPRef[0];
            configTemperature = tempRef[0];
            thinkingEnabled = thinkSwitch.isChecked();
            selectedTtsVoiceKey = ttsVoiceRef[0];
            persistUiPreferences();
            dialog.dismiss();
            if (shouldSwitchTtsVoice) {
                applySelectedTtsVoice();
            }
            if (shouldReloadModel && ModelStore.hasModel(this)) {
                loadModelIfAvailable();
                Toast.makeText(this, "\u914d\u7f6e\u5df2\u4fdd\u5b58，\u6b63\u5728\u91cd\u65b0\u52a0\u8f7d\u6a21\u578b", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, shouldSwitchTtsVoice ? "TTS 音色已切换" : "\u914d\u7f6e\u5df2\u4fdd\u5b58", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams okBtnParams = new LinearLayout.LayoutParams(dp(90), dp(44));
        okBtnParams.leftMargin = dp(10);
        footer.addView(okBtn, okBtnParams);

        applyTypefaceRecursively(panel);
        dialog.setView(dialogScroll);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private LinearLayout makeConfigSliderRow(String label, int min, int max, int defaultVal, int[] outRef) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.topMargin = dp(16);
        section.setLayoutParams(sectionParams);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.rgb(15, 25, 45));
        labelView.setTextSize(16);
        labelView.setTypeface(uiMediumTypeface);
        section.addView(labelView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(6);
        section.addView(row, rowParams);

        TextView minView = new TextView(this);
        minView.setText(String.valueOf(min));
        minView.setTextColor(Color.rgb(55, 75, 120));
        minView.setTextSize(12);
        minView.setMinWidth(dp(32));
        row.addView(minView, new LinearLayout.LayoutParams(-2, -2));

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(Math.max(0, defaultVal - min));
        LinearLayout.LayoutParams sbParams = new LinearLayout.LayoutParams(0, -2, 1);
        sbParams.leftMargin = dp(8);
        sbParams.rightMargin = dp(8);
        row.addView(seekBar, sbParams);

        TextView valView = new TextView(this);
        valView.setText(String.valueOf(defaultVal));
        valView.setTextColor(Color.rgb(15, 25, 45));
        valView.setTextSize(13);
        valView.setGravity(Gravity.CENTER);
        valView.setPadding(dp(8), dp(4), dp(8), dp(4));
        valView.setBackground(makeRoundBg(Color.WHITE, dp(8), Color.rgb(195, 215, 235), 1));
        valView.setMinWidth(dp(54));
        row.addView(valView, new LinearLayout.LayoutParams(-2, dp(34)));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int val = min + progress;
                outRef[0] = val;
                valView.setText(String.valueOf(val));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        return section;
    }

    private LinearLayout makeConfigFloatSliderRow(String label, float min, float max, float defaultVal, float[] outRef) {
        int steps = Math.round((max - min) * 100);
        int defProgress = Math.max(0, Math.min(steps, Math.round((defaultVal - min) * 100)));

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.topMargin = dp(16);
        section.setLayoutParams(sectionParams);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.rgb(15, 25, 45));
        labelView.setTextSize(16);
        labelView.setTypeface(uiMediumTypeface);
        section.addView(labelView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(6);
        section.addView(row, rowParams);

        TextView minView = new TextView(this);
        minView.setText(String.format(Locale.US, "%.2f", min));
        minView.setTextColor(Color.rgb(55, 75, 120));
        minView.setTextSize(12);
        minView.setMinWidth(dp(36));
        row.addView(minView, new LinearLayout.LayoutParams(-2, -2));

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(steps);
        seekBar.setProgress(defProgress);
        LinearLayout.LayoutParams sbParams = new LinearLayout.LayoutParams(0, -2, 1);
        sbParams.leftMargin = dp(8);
        sbParams.rightMargin = dp(8);
        row.addView(seekBar, sbParams);

        TextView valView = new TextView(this);
        valView.setText(String.format(Locale.US, "%.2f", defaultVal));
        valView.setTextColor(Color.rgb(15, 25, 45));
        valView.setTextSize(13);
        valView.setGravity(Gravity.CENTER);
        valView.setPadding(dp(8), dp(4), dp(8), dp(4));
        valView.setBackground(makeRoundBg(Color.WHITE, dp(8), Color.rgb(195, 215, 235), 1));
        valView.setMinWidth(dp(54));
        row.addView(valView, new LinearLayout.LayoutParams(-2, dp(34)));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float val = min + progress / 100f;
                outRef[0] = val;
                valView.setText(String.format(Locale.US, "%.2f", val));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        return section;
    }

    private String sanitizeAssistantReply(String raw) {
        if (raw == null) return "";
        // Strip any leftover <think> tags as a safety net (thinking disabled at API level)
        String cleaned = raw
                .replaceAll("(?is)<think>.*?</think>", "")
                .replaceAll("(?is)<thinking>.*?</thinking>", "");
        return cleaned.trim();
    }

    private void speak(String text) {
        if (sherpaOnnxTts == null || text == null || text.trim().isEmpty()) return;
        sherpaOnnxTts.speak(text);
    }

    private void addUserMessage(String text, Bitmap bitmap) {
        LinearLayout row = messageRow(true);
        LinearLayout bubble = messageBubble(true);
        if (bitmap != null) {
            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(bitmap);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bubble.addView(imageView, new LinearLayout.LayoutParams(-1, dp(160)));
        }
        TextView message = messageText(text, true);
        bubble.addView(message);
        attachBubbleCopyListener(bubble, text);
        row.addView(bubble);
        applyTypefaceRecursively(row);
        chatList.addView(row);
        scrollToBottom();
    }

    private TextView addAssistantMessage(String text, boolean returnTextView) {
        LinearLayout row = messageRow(false);
        LinearLayout bubble = messageBubble(false);
        TextView message = messageText(text, false);
        renderRichText(message, text, false);
        bubble.setTag(text);
        attachBubbleCopyListener(bubble, null);
        bubble.addView(message);
        row.addView(bubble);
        applyTypefaceRecursively(row);
        chatList.addView(row);
        scrollToBottom();
        return returnTextView ? message : message;
    }

    /** Long-press anywhere on a chat bubble to copy its text to the clipboard. */
    private void attachBubbleCopyListener(LinearLayout bubble, String fixedText) {
        bubble.setOnLongClickListener(v -> {
            String raw = fixedText;
            if (raw == null) {
                Object tag = bubble.getTag();
                raw = tag instanceof String ? (String) tag : "";
            }
            if (raw.isEmpty()) return false;
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (cm == null) return false;
            cm.setPrimaryClip(android.content.ClipData.newPlainText("message", raw));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private LinearLayout messageRow(boolean user) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(user ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(6);
        row.setLayoutParams(params);
        return row;
    }

    private LinearLayout messageBubble(boolean user) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        int fill = user ? Color.rgb(36, 53, 47) : Color.WHITE;
        int stroke = user ? Color.rgb(36, 53, 47) : Color.rgb(226, 219, 207);
        bubble.setBackground(makeRoundBg(fill, dp(16), stroke, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int) (getResources().getDisplayMetrics().widthPixels * 0.82f), -2);
        bubble.setLayoutParams(params);
        return bubble;
    }

    private TextView messageText(String text, boolean user) {
        TextView view = new TextView(this);
        view.setTextColor(user ? Color.WHITE : Color.rgb(35, 43, 39));
        view.setTextSize(15);
        view.setLineSpacing(dp(2), 1.0f);
        view.setLinksClickable(true);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setTypeface(user ? uiMediumTypeface : uiRegularTypeface);
        if (user) view.setText(text);
        return view;
    }

    private void renderRichText(TextView view, String text, boolean user) {
        if (user) {
            view.setText(text);
            return;
        }
        try {
            Node node = markdownParser.parse(text == null ? "" : text);
            String html = htmlRenderer.render(node);
            Spanned rendered = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY);
            view.setText(rendered);
        } catch (Exception ex) {
            view.setText(text);
        }
    }

    private void updateAssistantStreaming(TextView view, String partialText) {
        view.setText(partialText == null ? "" : partialText);
        scrollToBottom();
    }

    private void finishAssistantResponse(TextView pending, String userText, String finalAnswer) {
        streamAssistantResponse(pending, finalAnswer, () -> {
            recordConversationMessage("assistant", finalAnswer);
            upsertHistoryEntry(userText, finalAnswer);
            scrollToBottom();
            if (voiceCallOpen && voiceCallStatusView != null) {
                voiceCallStatusView.setText("这一轮已经自动完成：语音识别 -> 直接发送 -> 文本显示 -> 本地 TTS 播放。");
            }
            if (voiceCallOpen) speak(finalAnswer);
        });
    }

    private void streamAssistantResponse(TextView view, String fullText, Runnable onComplete) {
        String safe = fullText == null ? "" : fullText;
        renderRichText(view, safe, false);
        // Store raw text in parent bubble tag so long-press copy can retrieve it.
        if (view.getParent() instanceof LinearLayout) {
            ((LinearLayout) view.getParent()).setTag(safe);
        }
        scrollToBottom();
        if (onComplete != null) onComplete.run();
    }

    private void streamAssistantResponse(TextView view, String fullText, int index, Runnable onComplete) {
        if (index >= fullText.length()) {
            renderRichText(view, fullText, false);
            if (onComplete != null) onComplete.run();
            return;
        }
        int chunk = Math.max(1, Math.min(6, fullText.length() / 80 + 1));
        int nextIndex = Math.min(fullText.length(), index + chunk);
        view.setText(fullText.substring(0, nextIndex));
        scrollToBottom();
        mainHandler.postDelayed(() -> streamAssistantResponse(view, fullText, nextIndex, onComplete), 22);
    }

    private void styleConfigSegmentButton(Button button, boolean selected) {
        button.setTextColor(selected ? Color.rgb(25, 90, 210) : Color.rgb(90, 95, 115));
        button.setBackground(makeRoundBg(
                selected ? Color.WHITE : Color.rgb(200, 215, 232),
                dp(20),
                selected ? Color.rgb(25, 90, 210) : Color.rgb(175, 195, 220),
                selected ? 2 : 1
        ));
    }

    private boolean safeEquals(String left, String right) {
        if (left == null) return right == null;
        return left.equals(right);
    }

    private Button makePillButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(uiMediumTypeface);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(36, 53, 47));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(makeRoundBg(primary ? Color.rgb(36, 53, 47) : Color.WHITE, dp(22), Color.rgb(36, 53, 47), 1));
        return button;
    }

    private Button makeCompactPillButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(12);
        button.setTypeface(uiMediumTypeface);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(36, 53, 47));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(makeRoundBg(primary ? Color.rgb(36, 53, 47) : Color.rgb(248, 244, 238), dp(18), Color.rgb(214, 205, 189), 1));
        return button;
    }

    private Button makeIconOnlyButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(16);
        button.setTypeface(uiMediumTypeface);
        button.setTextColor(Color.rgb(36, 53, 47));
        button.setPadding(0, 0, 0, 0);
        button.setGravity(Gravity.CENTER);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setBackground(makeRoundBg(Color.rgb(248, 244, 238), dp(17), Color.rgb(214, 205, 189), 1));
        return button;
    }

    /** Icon-only ImageButton for toolbar and overlay controls. */
    private ImageButton makeToolbarIconBtn(int drawableResId, boolean filled) {
        ImageButton btn = new ImageButton(this);
        btn.setImageResource(drawableResId);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btn.setPadding(dp(7), dp(7), dp(7), dp(7));
        if (filled) {
            btn.setBackground(makeRoundBg(Color.rgb(36, 53, 47), dp(16), Color.rgb(36, 53, 47), 0));
            btn.setColorFilter(Color.WHITE);
        } else {
            btn.setBackground(makeRoundBg(Color.rgb(248, 244, 238), dp(16), Color.rgb(214, 205, 189), 1));
            btn.setColorFilter(Color.rgb(36, 53, 47));
        }
        return btn;
    }

    private Button makeTinyTagButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(11);
        button.setTypeface(uiMediumTypeface);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setTextColor(Color.rgb(36, 53, 47));
        button.setBackground(makeRoundBg(Color.rgb(246, 241, 234), dp(16), Color.rgb(214, 205, 189), 1));
        return button;
    }

    private void showImageReadyBanner(String text) {
        imageStatus.setText(text);
        imageStatus.setVisibility(View.VISIBLE);
        mainHandler.removeCallbacks(hideImageStatusRunnable);
        mainHandler.postDelayed(hideImageStatusRunnable, 3200);
    }

    private void startFreshChat() {
        startFreshChat("已开始新的练习。你可以输入中文、英文、日语或上传图片。", false, false);
    }

    private void startFreshChat(String intro, boolean enableConversationMode, boolean enableOpenChatMode) {
        chatList.removeAllViews();
        transcript.setLength(0);
        currentHistoryId = -1;
        selectedImage = null;
        currentConversationMessages.clear();
        updateSelectedImageChip();
        imageStatus.setVisibility(View.GONE);
        conversationMode = enableConversationMode;
        openChatMode = enableOpenChatMode;
        refreshConversationChip();
        refreshOpenChatChip();
        addAssistantMessage(intro, false);
        recordConversationMessage("assistant", intro);
    }

    private void upsertHistoryEntry(String userText, String assistantText) {
        HistoryEntry target = null;
        for (HistoryEntry entry : historyEntries) {
            if (entry.id == currentHistoryId) {
                target = entry;
                break;
            }
        }
        if (target == null) {
            target = new HistoryEntry(nextHistoryId++, shorten(userText, 16), shorten(assistantText, 42), trimTranscript(), serializeConversationMessages());
            historyEntries.add(0, target);
            currentHistoryId = target.id;
        } else {
            target.preview = shorten(assistantText, 42);
            target.detail = trimTranscript();
            target.messagesJson = serializeConversationMessages();
            historyEntries.remove(target);
            historyEntries.add(0, target);
        }
        persistHistoryEntries();
        renderHistoryList();
    }

    private void renderHistoryList() {
        if (historyList == null) return;
        historyList.removeAllViews();
        if (historyEntries.isEmpty()) {
            historyList.addView(historyEmptyView, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (HistoryEntry entry : historyEntries) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setBackground(makeRoundBg(Color.rgb(255, 249, 242), dp(18), Color.rgb(214, 205, 189), 1));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(10);
            historyList.addView(row, rowParams);

            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

            TextView title = new TextView(this);
            title.setText(entry.title);
            title.setTextColor(Color.rgb(36, 53, 47));
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextSize(14);
            copy.addView(title);

            TextView preview = new TextView(this);
            preview.setText(entry.preview);
            preview.setTextColor(Color.rgb(111, 107, 93));
            preview.setTextSize(12);
            preview.setLineSpacing(dp(3), 1.0f);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
            previewParams.topMargin = dp(4);
            copy.addView(preview, previewParams);

            Button delete = makeCompactPillButton("删除", false);
            delete.setTextColor(Color.rgb(134, 83, 74));
            delete.setOnClickListener(v -> confirmDeleteHistory(entry));
            row.addView(delete, new LinearLayout.LayoutParams(-2, dp(32)));

            applyTypefaceRecursively(row);
            row.setOnClickListener(v -> restoreHistoryEntry(entry));
        }
    }

    private void restoreHistoryEntry(HistoryEntry entry) {
        chatList.removeAllViews();
        transcript.setLength(0);
        currentConversationMessages.clear();
        currentHistoryId = entry.id;
        selectedImage = null;
        updateSelectedImageChip();
        imageStatus.setVisibility(View.GONE);
        try {
            JSONArray array = new JSONArray(entry.messagesJson == null ? "[]" : entry.messagesJson);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                String role = item.optString("role", "assistant");
                String text = item.optString("text", "");
                if ("user".equals(role)) {
                    addUserMessage(text, null);
                } else {
                    addAssistantMessage(text, false);
                }
                recordConversationMessage(role, text);
            }
        } catch (Exception ex) {
            String fallback = entry.detail == null || entry.detail.trim().isEmpty() ? entry.preview : entry.detail;
            addAssistantMessage(fallback, false);
            recordConversationMessage("assistant", fallback);
        }
        closeHistoryPanel();
        if (voiceCallOpen) refreshVoiceCallTranscript();
        Toast.makeText(this, "已恢复该对话，可继续聊天", Toast.LENGTH_SHORT).show();
    }

    private void confirmDeleteHistory(HistoryEntry entry) {
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(16));
        panel.setBackground(makeRoundBg(Color.rgb(255, 252, 247), dp(22), Color.rgb(217, 206, 191), 1));

        TextView title = new TextView(this);
        title.setText("删除“" + entry.title + "”？");
        title.setTextColor(Color.rgb(36, 53, 47));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(18);
        panel.addView(title);

        TextView copy = new TextView(this);
        copy.setText("删除后不会影响其他聊天记录，但这条记录本身无法恢复。");
        copy.setTextColor(Color.rgb(111, 107, 93));
        copy.setTextSize(13);
        copy.setLineSpacing(dp(4), 1.0f);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-1, -2);
        copyParams.topMargin = dp(10);
        panel.addView(copy, copyParams);

        LinearLayout actions = new LinearLayout(this);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.topMargin = dp(16);
        panel.addView(actions, actionsParams);

        Button cancel = makeCompactPillButton("取消", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(36), 1));

        Button confirm = makeCompactPillButton("确认删除", true);
        confirm.setBackground(makeRoundBg(Color.rgb(127, 77, 67), dp(18), Color.rgb(127, 77, 67), 1));
        confirm.setOnClickListener(v -> {
            historyEntries.remove(entry);
            if (currentHistoryId == entry.id) currentHistoryId = -1;
            persistHistoryEntries();
            renderHistoryList();
            dialog.dismiss();
            Toast.makeText(this, "记录已删除", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(36), 1);
        confirmParams.leftMargin = dp(10);
        actions.addView(confirm, confirmParams);

        dialog.setView(panel);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void loadHistoryEntries() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String raw = prefs.getString(PREF_HISTORY, "");
        if (raw == null || raw.trim().isEmpty()) return;
        try {
            JSONArray array = new JSONArray(raw);
            int maxId = 0;
            historyEntries.clear();
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                HistoryEntry entry = new HistoryEntry(
                        item.optInt("id", nextHistoryId++),
                        item.optString("title", "未命名记录"),
                        item.optString("preview", ""),
                    item.optString("detail", ""),
                    item.optString("messagesJson", "[]")
                );
                historyEntries.add(entry);
                if (entry.id > maxId) maxId = entry.id;
            }
            nextHistoryId = Math.max(nextHistoryId, maxId + 1);
        } catch (Exception ignored) {
        }
    }

    private void persistHistoryEntries() {
        JSONArray array = new JSONArray();
        for (HistoryEntry entry : historyEntries) {
            try {
                JSONObject item = new JSONObject();
                item.put("id", entry.id);
                item.put("title", entry.title);
                item.put("preview", entry.preview);
                item.put("detail", entry.detail);
                item.put("messagesJson", entry.messagesJson == null ? "[]" : entry.messagesJson);
                array.put(item);
            } catch (Exception ignored) {
            }
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_HISTORY, array.toString())
                .apply();
    }

    private static String shorten(String value, int maxLength) {
        if (value == null) return "未命名记录";
        String normalized = value.replace('\n', ' ').trim();
        if (normalized.isEmpty()) return "未命名记录";
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "…";
    }

    private GradientDrawable makeRoundBg(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void scrollToBottom() {
        mainHandler.postDelayed(() -> scrollView.fullScroll(View.FOCUS_DOWN), 80);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(inputBox.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    private String trimTranscript() {
        String value = transcript.toString();
        if (value.length() <= 900) return value;

        int start = Math.max(0, value.length() - 900);
        int lineStart = value.indexOf('\n', start);
        if (lineStart >= 0 && lineStart + 1 < value.length()) {
            start = lineStart + 1;
        }
        return value.substring(start);
    }

    private void appendTranscript(String role, String text) {
        transcript.append(role).append(": ").append(text).append('\n');
        if (transcript.length() > 1800) {
            transcript.delete(0, transcript.length() - 1200);
        }
    }

    private String serializeConversationMessages() {
        JSONArray array = new JSONArray();
        for (ChatMessage message : currentConversationMessages) {
            try {
                JSONObject item = new JSONObject();
                item.put("role", message.role);
                item.put("text", message.text);
                array.put(item);
            } catch (Exception ignored) {
            }
        }
        return array.toString();
    }

    private void recordConversationMessage(String role, String text) {
        currentConversationMessages.add(new ChatMessage(role, text));
        appendTranscript("user".equals(role) ? "You" : "Tutor", text);
        if (voiceCallOpen) refreshVoiceCallTranscript();
    }

    private static String compactError(Exception ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        String message = cause.getMessage();
        if (message == null || message.trim().isEmpty()) message = cause.getClass().getSimpleName();
        return message.length() > 220 ? message.substring(0, 220) : message;
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "未知大小";
        double mb = bytes / 1024.0 / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == REQUEST_IMPORT_MODEL) {
            if (data == null || data.getData() == null) return;
            Uri uri = data.getData();
            showLoadingOverlay("正在导入模型", "模型文件较大，复制到应用私有目录后会立即加载。", false);
            executor.execute(() -> {
                try {
                    ModelStore.importModel(this, uri);
                    mainHandler.post(this::loadModelIfAvailable);
                } catch (Exception ex) {
                    mainHandler.post(() -> showLoadingOverlay("模型导入失败", compactError(ex), true));
                }
            });
        } else if (requestCode == REQUEST_PICK_IMAGE) {
            if (data == null || data.getData() == null) return;
            Uri uri = data.getData();
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                selectedImage = BitmapFactory.decodeStream(input);
                updateSelectedImageChip();
                showImageReadyBanner("已选择图片，发送后会识别并翻译图片内容。");
            } catch (Exception ex) {
                Toast.makeText(this, "图片读取失败：" + compactError(ex), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CAPTURE_IMAGE && pendingCameraUri != null) {
            try (InputStream input = getContentResolver().openInputStream(pendingCameraUri)) {
                selectedImage = BitmapFactory.decodeStream(input);
                updateSelectedImageChip();
                showImageReadyBanner("已拍摄图片，发送后会识别并翻译图片内容。");
            } catch (Exception ex) {
                Toast.makeText(this, "图片读取失败：" + compactError(ex), Toast.LENGTH_SHORT).show();
            } finally {
                pendingCameraUri = null;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceInput(voiceInputTarget);
        } else if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCameraCapture();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        mainHandler.removeCallbacks(voiceIndicatorRunnable);
        mainHandler.removeCallbacks(hideImageStatusRunnable);
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (sherpaOnnxTts != null) sherpaOnnxTts.shutdown();
        if (llmEngine != null) llmEngine.close();
    }
}