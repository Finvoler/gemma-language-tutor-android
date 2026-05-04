const stateButtons = document.querySelectorAll('.state-pill');
const body = document.body;

const stateTitle = document.getElementById('stateTitle');
const stateCaption = document.getElementById('stateCaption');
const modelStatus = document.getElementById('modelStatus');
const historyToggle = document.getElementById('historyToggle');
const plusAction = document.getElementById('plusAction');
const conversationToggle = document.getElementById('conversationToggle');
const voiceCallToggle = document.getElementById('voiceCallToggle');
const voiceAction = document.getElementById('voiceAction');
const userBubble = document.getElementById('userBubble');
const assistantBubble = document.getElementById('assistantBubble');
const thinkingBubble = document.getElementById('thinkingBubble');
const imageBanner = document.getElementById('imageBanner');
const imageCard = document.getElementById('imageCard');
const selectedImageChip = document.getElementById('selectedImageChip');
const userText = document.getElementById('userText');
const assistantText = document.getElementById('assistantText');
const composerInput = document.getElementById('composerInput');
const overlay = document.getElementById('overlay');
const overlayTitle = document.getElementById('overlayTitle');
const overlayCopy = document.getElementById('overlayCopy');
const overlayPrimary = document.getElementById('overlayPrimary');
const overlaySecondary = document.getElementById('overlaySecondary');
const actionSheet = document.getElementById('actionSheet');
const sheetBackdrop = document.getElementById('sheetBackdrop');
const sheetClose = document.getElementById('sheetClose');
const chooseAlbum = document.getElementById('chooseAlbum');
const chooseCamera = document.getElementById('chooseCamera');
const historyLayer = document.getElementById('historyLayer');
const historyBackdrop = document.getElementById('historyBackdrop');
const historyScroll = document.getElementById('historyScroll');
const newChatButton = document.getElementById('newChatButton');
const confirmLayer = document.getElementById('confirmLayer');
const confirmBackdrop = document.getElementById('confirmBackdrop');
const confirmTitle = document.getElementById('confirmTitle');
const confirmCopy = document.getElementById('confirmCopy');
const confirmCancel = document.getElementById('confirmCancel');
const confirmDelete = document.getElementById('confirmDelete');
const voiceCallLayer = document.getElementById('voiceCallLayer');
const voiceCallClose = document.getElementById('voiceCallClose');
const voiceCallStatus = document.getElementById('voiceCallStatus');
const voiceCallScroll = document.getElementById('voiceCallScroll');
const voiceCallHint = document.getElementById('voiceCallHint');
const voiceCallMic = document.getElementById('voiceCallMic');
const toast = document.getElementById('toast');

let imageBannerTimer = null;
let voiceTimer = null;
let toastTimer = null;
let imageSelected = false;
let pendingDeleteId = null;
let voiceCallTimer = null;
let voiceCallTurns = [];
let historyItems = [
    {
        id: 1,
        title: '和教授约时间',
        preview: 'Would you have time tomorrow to talk about the project?',
        state: 'conversation',
        userText: '我明天要和教授约时间讨论 project，英语怎么自然一点？',
        assistantText: 'Do you mean: I need to ask my professor for a time to discuss the project tomorrow. How can I say it more naturally in English?\n\n你可以直接说: Would you have time tomorrow to talk about the project?'
    },
    {
        id: 2,
        title: '菜单图片翻译',
        preview: '先逐项翻译，再挑三道第一次点更稳的菜。',
        state: 'vision',
        userText: '帮我把这张菜单翻成中文，并挑出适合第一次点的三样。',
        assistantText: '先逐项翻译，再给推荐理由。比如第一次点可以优先选招牌主菜、一个安全配菜、一个不容易踩雷的汤。'
    },
    {
        id: 3,
        title: '英文邮件润色',
        preview: '把语气收得更礼貌，但不要太像模板。',
        state: 'study',
        userText: '帮我把这封英文邮件改得更礼貌，但不要太模板化。',
        assistantText: '可以。更自然的方向不是一味加 many thanks，而是先明确请求，再降低语气强度，最后补一句 context。'
    },
    {
        id: 4,
        title: '日语句子解释',
        preview: '重点区分助词语感，不只做直译。',
        state: 'study',
        userText: '解释一下这句日语里 は 和 が 的语气差别。',
        assistantText: '这类题不能只做字面翻译，要把焦点信息和对比语感拆开讲。'
    },
    {
        id: 5,
        title: '论文摘要改写',
        preview: '保留学术语气，但减少生硬翻译腔。',
        state: 'study',
        userText: '把这个摘要改得更像自然学术英语。',
        assistantText: '我会保留 technical meaning，但把冗余被动句和直译型连接词收掉。'
    },
    {
        id: 6,
        title: '旅行对话练习',
        preview: '先 Do you mean，再继续自然英语回复。',
        state: 'conversation',
        userText: '我想问酒店能不能晚点退房，怎么说自然一点？',
        assistantText: 'Do you mean: I want to ask the hotel whether I can check out later. How can I say it naturally?\n\n你可以说: Would it be possible to have a late check-out?'
    }
];

const states = {
    welcome: {
        title: '首次打开 / 模型下载引导',
        caption: '还没导入模型时的首页状态。',
        status: '未导入模型：可先体验界面，AI 生成需导入或下载 Gemma 4 E2B',
        conversationActive: false,
        userVisible: false,
        assistantVisible: false,
        thinkingVisible: false,
        imageVisible: false,
        overlayVisible: true,
        overlayTitle: '需要 Gemma 4 E2B',
        overlayCopy: '可直接下载 litert-community/gemma-4-E2B-it-litert-lm 的通用 LiteRT-LM 文件，也可以选择你已经下载好的 gemma-4-E2B-it.litertlm。',
        overlayPrimary: '下载 Gemma 4 E2B',
        overlaySecondary: '选择本地模型文件',
        placeholder: '输入问题或继续追问'
    },
    study: {
        title: '学习解析 / 文本翻译与解释',
        caption: '默认学习模式，偏解析、翻译和表达说明。',
        status: 'Gemma 4 E2B 已加载到内存，本地推理就绪',
        conversationActive: false,
        userVisible: true,
        assistantVisible: true,
        thinkingVisible: false,
        imageVisible: false,
        overlayVisible: false,
        userText: '把这段英文翻成自然中文，并解释为什么这样表达。',
        assistantText: '当然。\n\nDo you mean: Please translate this English sentence into natural Chinese and explain why it is phrased this way?\n\n如果你在练英语表达，这样问会更自然，因为它同时明确了两个目标：翻译结果和表达逻辑。',
        placeholder: '输入问题或继续追问'
    },
    conversation: {
        title: '英语对话 / 口语训练状态',
        caption: '先做 Do you mean 改写，再继续自然对话。',
        status: 'Gemma 4 E2B 已加载到内存，本地推理就绪',
        conversationActive: true,
        userVisible: true,
        assistantVisible: true,
        thinkingVisible: false,
        imageVisible: false,
        overlayVisible: false,
        userText: '我明天要和教授约时间讨论 project，英语怎么自然一点？',
        assistantText: 'Do you mean: I need to ask my professor for a time to discuss the project tomorrow. How can I say it more naturally in English?\n\n你可以直接说: Would you have time tomorrow to talk about the project? 我也可以继续帮你把它改成更礼貌或更口语的版本。',
        placeholder: '说一句中文或英文，我会先帮你改写成自然英语'
    },
    vision: {
        title: '图片翻译 / 菜单与截图理解',
        caption: '上传图片后，界面会显示图片状态横幅和图像气泡。',
        status: 'Gemma 4 E2B 已加载到内存，本地推理就绪',
        conversationActive: false,
        userVisible: true,
        assistantVisible: true,
        thinkingVisible: true,
        imageVisible: true,
        overlayVisible: false,
        userText: '帮我把这张菜单翻成中文，并挑出适合第一次点的三样。',
        assistantText: '这类图片请求适合用更像助手的结构回答：先做逐项翻译，再给推荐理由。\n\n如果你愿意，我下一轮可以把这块界面再做得更“编辑感”一点，比如让图片卡更克制、消息层次更锋利。',
        placeholder: '输入问题，或继续追问这张图片里的内容'
    }
};

function autosizeComposer() {
    composerInput.style.height = '24px';
    composerInput.style.height = `${Math.min(composerInput.scrollHeight, 94)}px`;
}

function openActionSheet() {
    actionSheet.classList.remove('hidden');
}

function closeActionSheet() {
    actionSheet.classList.add('hidden');
}

function openHistoryLayer() {
    historyLayer.classList.remove('hidden');
}

function closeHistoryLayer() {
    historyLayer.classList.add('hidden');
}

function openConfirmFor(item) {
    pendingDeleteId = item.id;
    confirmTitle.textContent = `删除“${item.title}”？`;
    confirmCopy.textContent = '删除后不会影响其他聊天记录，但这条记录本身无法恢复。';
    confirmLayer.classList.remove('hidden');
}

function closeConfirm() {
    pendingDeleteId = null;
    confirmLayer.classList.add('hidden');
}

function seedVoiceCallTurns() {
    voiceCallTurns = [
        {
            role: 'assistant',
            text: '语音通话已接通。你直接开口说英语或中文都可以，我会先识别文本，再把回复语音播出来。',
            meta: '本地 TTS 回放'
        }
    ];

    if (!userBubble.classList.contains('hidden')) {
        voiceCallTurns.push({ role: 'user', text: userText.textContent, meta: '你的实时转写' });
    }
    if (!assistantBubble.classList.contains('hidden')) {
        voiceCallTurns.push({ role: 'assistant', text: assistantText.textContent, meta: 'Gemma 文本 + TTS' });
    }
}

function renderVoiceCallTurns() {
    voiceCallScroll.innerHTML = '';
    voiceCallTurns.forEach((turn) => {
        const item = document.createElement('article');
        item.className = `call-turn ${turn.role}`;
        item.innerHTML = `
            <span class="call-turn-role">${turn.role === 'user' ? 'YOU' : 'TUTOR'}</span>
            <p class="call-turn-copy">${turn.text}</p>
            <span class="call-turn-meta">${turn.meta}</span>
        `;
        voiceCallScroll.appendChild(item);
    });
    voiceCallScroll.scrollTop = voiceCallScroll.scrollHeight;
}

function openVoiceCallLayer() {
    if (body.dataset.state === 'welcome') {
        renderState('conversation');
    }
    seedVoiceCallTurns();
    renderVoiceCallTurns();
    voiceCallHint.textContent = '点击下方麦克风开始说话';
    voiceCallStatus.textContent = '使用本地语音识别 + 本地 TTS。你说完会直接发给 LLM，助手会语音回复。';
    voiceCallLayer.classList.remove('hidden');
}

function closeVoiceCallLayer() {
    clearTimeout(voiceCallTimer);
    voiceCallMic.classList.remove('recording');
    voiceCallLayer.classList.add('hidden');
}

function showToast(message) {
    clearTimeout(toastTimer);
    toast.textContent = message;
    toast.classList.remove('hidden');
    toastTimer = window.setTimeout(() => toast.classList.add('hidden'), 1800);
}

function showImageBanner(text) {
    clearTimeout(imageBannerTimer);
    imageBanner.textContent = text;
    imageBanner.classList.remove('hidden');
    imageBannerTimer = window.setTimeout(() => imageBanner.classList.add('hidden'), 3200);
}

function setImageSelected(active) {
    imageSelected = active;
    selectedImageChip.classList.toggle('hidden', !active);
}

function activateImageSelection(sourceLabel) {
    setImageSelected(true);
    closeActionSheet();
    showImageBanner(`已选择图片来源：${sourceLabel}。发送后会识别并翻译图片内容。`);
    if (body.dataset.state === 'study' || body.dataset.state === 'welcome') {
        renderState('vision');
    }
}

function renderHistory() {
    historyScroll.innerHTML = '';
    historyItems.forEach((item) => {
        const row = document.createElement('article');
        row.className = 'history-item';

        const copy = document.createElement('div');
        copy.className = 'history-item-copy';
        copy.innerHTML = `<p class="history-item-title">${item.title}</p><p class="history-item-preview">${item.preview}</p>`;

        const del = document.createElement('button');
        del.className = 'delete-chip';
        del.textContent = '删除';
        del.addEventListener('click', (event) => {
            event.stopPropagation();
            openConfirmFor(item);
        });

        row.appendChild(copy);
        row.appendChild(del);
        row.addEventListener('click', () => {
            renderState(item.state);
            userText.textContent = item.userText;
            assistantText.textContent = item.assistantText;
            closeHistoryLayer();
            showToast(`已恢复：${item.title}`);
        });
        historyScroll.appendChild(row);
    });
}

function startVoiceAnimation() {
    window.clearTimeout(voiceTimer);
    voiceAction.classList.add('recording');
    modelStatus.textContent = '语音输入中，识别完成后会直接发送给 LLM';
    composerInput.value = '正在听你说话…';
    autosizeComposer();
    voiceTimer = window.setTimeout(() => {
        voiceAction.classList.remove('recording');
        modelStatus.textContent = states[body.dataset.state].status;
        composerInput.value = '';
        autosizeComposer();
        showToast('语音输入完毕，已发送');
    }, 1800);
}

function startVoiceCallFlow() {
    clearTimeout(voiceCallTimer);
    voiceCallMic.classList.add('recording');
    voiceCallHint.textContent = '正在听你说话';
    voiceCallStatus.textContent = '识别中。结束后会直接把文本发给 Gemma，然后用本地 TTS 播放回复。';
    voiceCallTimer = window.setTimeout(() => {
        voiceCallMic.classList.remove('recording');
        voiceCallTurns.push({
            role: 'user',
            text: 'Can we move our meeting to tomorrow afternoon?',
            meta: '你的实时转写'
        });
        voiceCallTurns.push({
            role: 'assistant',
            text: 'Do you mean: Can we move our meeting to tomorrow afternoon?\n\nSure. You can also say: Would it be possible to move our meeting to tomorrow afternoon?',
            meta: 'Gemma 文本 + 本地 TTS 播放'
        });
        renderVoiceCallTurns();
        voiceCallHint.textContent = '回复已播报，点击麦克风继续';
        voiceCallStatus.textContent = '这一轮已经自动完成：语音识别 -> 直接发送 -> 文本显示 -> TTS 播放。';
    }, 1800);
}

function renderState(name) {
    const state = states[name];
    body.dataset.state = name;

    stateButtons.forEach((button) => {
        button.classList.toggle('active', button.dataset.stateTarget === name);
    });

    stateTitle.textContent = state.title;
    stateCaption.textContent = state.caption;
    modelStatus.textContent = state.status;
    conversationToggle.classList.toggle('active', state.conversationActive);

    userBubble.classList.toggle('hidden', !state.userVisible);
    assistantBubble.classList.toggle('hidden', !state.assistantVisible);
    thinkingBubble.classList.toggle('hidden', !state.thinkingVisible);
    imageCard.classList.toggle('hidden', !state.imageVisible);

    if (state.userText) userText.textContent = state.userText;
    if (state.assistantText) assistantText.textContent = state.assistantText;

    overlay.classList.toggle('active', state.overlayVisible);
    overlay.classList.toggle('hidden', !state.overlayVisible);
    if (state.overlayVisible) {
        overlayTitle.textContent = state.overlayTitle;
        overlayCopy.textContent = state.overlayCopy;
        overlayPrimary.textContent = state.overlayPrimary;
        overlaySecondary.textContent = state.overlaySecondary;
    }

    composerInput.placeholder = state.placeholder;
    composerInput.value = '';
    autosizeComposer();
    closeActionSheet();
    closeHistoryLayer();
    closeConfirm();
    closeVoiceCallLayer();
    voiceAction.classList.remove('recording');

    if (state.imageVisible) {
        setImageSelected(true);
        showImageBanner('已选择图片，发送后会识别并翻译图片内容。');
    } else {
        setImageSelected(false);
        imageBanner.classList.add('hidden');
    }
}

stateButtons.forEach((button) => {
    button.addEventListener('click', () => renderState(button.dataset.stateTarget));
});

composerInput.addEventListener('input', autosizeComposer);
plusAction.addEventListener('click', openActionSheet);
conversationToggle.addEventListener('click', () => {
    renderState(body.dataset.state === 'conversation' ? 'study' : 'conversation');
});
historyToggle.addEventListener('click', openHistoryLayer);
historyBackdrop.addEventListener('click', closeHistoryLayer);
sheetBackdrop.addEventListener('click', closeActionSheet);
sheetClose.addEventListener('click', closeActionSheet);
chooseAlbum.addEventListener('click', () => activateImageSelection('相册'));
chooseCamera.addEventListener('click', () => activateImageSelection('拍摄'));
voiceAction.addEventListener('click', startVoiceAnimation);
voiceCallToggle.addEventListener('click', openVoiceCallLayer);
voiceCallClose.addEventListener('click', closeVoiceCallLayer);
voiceCallMic.addEventListener('click', startVoiceCallFlow);
confirmBackdrop.addEventListener('click', closeConfirm);
confirmCancel.addEventListener('click', closeConfirm);
confirmDelete.addEventListener('click', () => {
    if (pendingDeleteId == null) return;
    historyItems = historyItems.filter((entry) => entry.id !== pendingDeleteId);
    closeConfirm();
    renderHistory();
    showToast('记录已删除');
});
newChatButton.addEventListener('click', () => {
    closeHistoryLayer();
    setImageSelected(false);
    renderState('study');
    showToast('已开始新对话');
});

renderHistory();
renderState('welcome');