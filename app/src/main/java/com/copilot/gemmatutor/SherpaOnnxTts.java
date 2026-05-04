package com.copilot.gemmatutor;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import android.util.Log;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Offline TTS wrapper using sherpa-onnx with vits-piper-en_US-amy-medium model.
 * Model files should be placed in assets/tts-model/.
 */
public class SherpaOnnxTts {

    private static final String TAG = "SherpaOnnxTts";
    private static final int MAX_TOTAL_SPEECH_CHARS_PIPER = 700;
    private static final int FIRST_CHUNK_CHARS_PIPER = 180;
    private static final int NEXT_CHUNK_CHARS_PIPER = 180;

    public enum VoiceProfile {
        PIPER_AMY("piper_amy", "Amy (Piper)", "tts-model", "en_US-amy-medium.onnx", 0);

        private final String key;
        private final String displayName;
        private final String assetDir;
        private final String modelFile;
        private final int speakerId;

        VoiceProfile(String key, String displayName, String assetDir, String modelFile, int speakerId) {
            this.key = key;
            this.displayName = displayName;
            this.assetDir = assetDir;
            this.modelFile = modelFile;
            this.speakerId = speakerId;
        }

        public String getKey() {
            return key;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getAssetDir() {
            return assetDir;
        }

        public String getModelFile() {
            return modelFile;
        }

        public int getSpeakerId() {
            return speakerId;
        }

        public static VoiceProfile fromKey(String key) {
            for (VoiceProfile profile : values()) {
                if (profile.key.equals(key)) {
                    return profile;
                }
            }
            return PIPER_AMY;
        }
    }

    private final Context appContext;
    private final AssetManager assetManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService prewarmExecutor = Executors.newSingleThreadExecutor();

    private OfflineTts tts;
    private VoiceProfile loadedProfile;
    private VoiceProfile warmedProfile;
    private AudioTrack audioTrack;
    private Future<?> currentTask;
    private Future<?> warmupTask;
    private volatile boolean stopped;
    private volatile VoiceProfile voiceProfile;
    private float amySpeed = 1.0f;
    private float amyVivacity = 1.0f;

    public SherpaOnnxTts(Context context) {
        this(context, VoiceProfile.PIPER_AMY);
    }

    public SherpaOnnxTts(Context context, VoiceProfile voiceProfile) {
        this.appContext = context.getApplicationContext();
        this.assetManager = appContext.getAssets();
        this.voiceProfile = voiceProfile == null ? VoiceProfile.PIPER_AMY : voiceProfile;
    }

    public SherpaOnnxTts(AssetManager assetManager) {
        this(assetManager, VoiceProfile.PIPER_AMY);
    }

    public SherpaOnnxTts(AssetManager assetManager, VoiceProfile voiceProfile) {
        this.appContext = null;
        this.assetManager = assetManager;
        this.voiceProfile = voiceProfile == null ? VoiceProfile.PIPER_AMY : voiceProfile;
    }

    public synchronized void setAmyStyle(float speed, float vivacity) {
        float safeSpeed = clamp(speed, 0.75f, 1.35f);
        float safeVivacity = clamp(vivacity, 0.70f, 1.40f);
        if (Math.abs(amySpeed - safeSpeed) < 0.001f && Math.abs(amyVivacity - safeVivacity) < 0.001f) {
            return;
        }
        amySpeed = safeSpeed;
        amyVivacity = safeVivacity;
        stop();
        releaseTtsLocked();
    }

    // Lazy-initialize the TTS engine on first use to avoid blocking the UI thread.
    private synchronized OfflineTts getOrInitTts(VoiceProfile profile) {
        if (tts == null || loadedProfile != profile) {
            releaseTtsLocked();

            String dataDir = resolveDataDir(profile);
            OfflineTtsVitsModelConfig vitsConfig = new OfflineTtsVitsModelConfig();
            vitsConfig.setModel(profile.getAssetDir() + "/" + profile.getModelFile());
            vitsConfig.setLexicon("");
            vitsConfig.setTokens(profile.getAssetDir() + "/tokens.txt");
            vitsConfig.setDataDir(dataDir);
            vitsConfig.setDictDir("");
            vitsConfig.setNoiseScale(clamp(0.667f * amyVivacity, 0.3f, 1.2f));
            vitsConfig.setNoiseScaleW(clamp(0.8f * amyVivacity, 0.35f, 1.4f));
            vitsConfig.setLengthScale(clamp(1.0f / amySpeed, 0.72f, 1.45f));

            OfflineTtsModelConfig modelConfig = new OfflineTtsModelConfig();
            modelConfig.setVits(vitsConfig);
            modelConfig.setNumThreads(1);
            modelConfig.setDebug(false);
            modelConfig.setProvider("cpu");

            OfflineTtsConfig config = new OfflineTtsConfig();
            config.setModel(modelConfig);
            config.setMaxNumSentences(1);
            tts = new OfflineTts(assetManager, config);
            loadedProfile = profile;
        }
        return tts;
    }

    private String resolveDataDir(VoiceProfile profile) {
        String assetDataDir = profile.getAssetDir() + "/espeak-ng-data";
        if (appContext == null) {
            return assetDataDir;
        }
        File targetDir = new File(new File(appContext.getFilesDir(), "sherpa-onnx-tts"), assetDataDir);
        try {
            copyAssetDirectoryIfNeeded(assetDataDir, targetDir);
            return targetDir.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy " + assetDataDir + ", falling back to asset path", e);
            return assetDataDir;
        }
    }

    private void copyAssetDirectoryIfNeeded(String assetPath, File targetDir) throws IOException {
        String[] children = assetManager.list(assetPath);
        if (children == null) return;
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Could not create " + targetDir.getAbsolutePath());
        }
        for (String child : children) {
            String childAssetPath = assetPath + "/" + child;
            File childTarget = new File(targetDir, child);
            String[] nested = assetManager.list(childAssetPath);
            if (nested != null && nested.length > 0) {
                copyAssetDirectoryIfNeeded(childAssetPath, childTarget);
            } else if (!childTarget.exists() || childTarget.length() == 0) {
                copyAssetFile(childAssetPath, childTarget);
            }
        }
    }

    private void copyAssetFile(String assetPath, File targetFile) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent.getAbsolutePath());
        }
        try (InputStream input = assetManager.open(assetPath);
             OutputStream output = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    public synchronized void setVoiceProfile(VoiceProfile voiceProfile) {
        VoiceProfile resolved = voiceProfile == null ? VoiceProfile.PIPER_AMY : voiceProfile;
        if (this.voiceProfile == resolved) {
            return;
        }
        stop();
        this.voiceProfile = resolved;
        releaseTtsLocked();
    }

    public VoiceProfile getVoiceProfile() {
        return voiceProfile;
    }

    public synchronized void prepare() {
        prepare(voiceProfile);
    }

    public synchronized void prepare(VoiceProfile requestedProfile) {
        VoiceProfile profile = requestedProfile == null ? voiceProfile : requestedProfile;
        if (profile == null) {
            profile = VoiceProfile.PIPER_AMY;
        }
        if (warmedProfile == profile) {
            return;
        }
        if (warmupTask != null && !warmupTask.isDone()) {
            return;
        }
        VoiceProfile targetProfile = profile;
        warmupTask = prewarmExecutor.submit(() -> warmUpProfile(targetProfile));
    }

    /** Speak the given text asynchronously. Cancels any ongoing speech first. */
    public void speak(String text) {
        speak(text, null);
    }

    public void speak(String text, VoiceProfile requestedProfile) {
        if (text == null || text.trim().isEmpty()) return;
        stop();
        stopped = false;
        VoiceProfile resolvedProfile = requestedProfile == null ? voiceProfile : requestedProfile;
        currentTask = executor.submit(() -> {
            try {
                VoiceProfile profile = resolvedProfile == null ? VoiceProfile.PIPER_AMY : resolvedProfile;
                OfflineTts engine = getOrInitTts(profile);
                List<String> chunks = splitForSpeech(text, profile);
                for (String chunk : chunks) {
                    if (stopped) return;
                    GeneratedAudio audio = engine.generate(chunk, profile.getSpeakerId(), 1.0f);
                    warmedProfile = profile;
                    if (stopped) return;
                    playAudio(audio.getSamples(), audio.getSampleRate());
                }
            } catch (Throwable t) {
                Log.e(TAG, "TTS speak failed", t);
            }
        });
    }

    private void warmUpProfile(VoiceProfile profile) {
        try {
            OfflineTts engine = getOrInitTts(profile);
            engine.generate("Hi.", profile.getSpeakerId(), 1.0f);
            warmedProfile = profile;
        } catch (Throwable t) {
            Log.w(TAG, "TTS prewarm failed for " + profile.getKey(), t);
        }
    }

    private List<String> splitForSpeech(String text, VoiceProfile profile) {
        String cleaned = cleanForSpeech(text);
        List<String> chunks = new ArrayList<>();
        if (cleaned.isEmpty()) return chunks;
        if (cleaned.length() > MAX_TOTAL_SPEECH_CHARS_PIPER) {
            cleaned = cleaned.substring(0, MAX_TOTAL_SPEECH_CHARS_PIPER).trim() + ".";
        }

        String[] sentences = cleaned.split("(?<=[.!?。！？])\\s+");
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            String safeSentence = sentence.trim();
            if (safeSentence.isEmpty()) continue;
            int activeLimit = getChunkCharLimit(profile, chunks.isEmpty() && current.length() == 0);
            if (safeSentence.length() > activeLimit) {
                flushChunk(chunks, current);
                splitLongSentence(chunks, safeSentence, profile);
                continue;
            }
            if (current.length() > 0 && current.length() + 1 + safeSentence.length() > activeLimit) {
                flushChunk(chunks, current);
                activeLimit = getChunkCharLimit(profile, chunks.isEmpty());
            }
            if (current.length() > 0) current.append(' ');
            current.append(safeSentence);
        }
        flushChunk(chunks, current);
        return chunks;
    }

    private String cleanForSpeech(String text) {
        if (text == null) return "";
        return text
                .replaceAll("(?is)```.*?```", " ")
                .replaceAll("(?is)<thinking>.*?</thinking>", " ")
                .replaceAll("https?://\\S+", " ")
                .replace('*', ' ')
                .replace('#', ' ')
                .replace('`', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void splitLongSentence(List<String> chunks, String sentence, VoiceProfile profile) {
        int start = 0;
        while (start < sentence.length()) {
            int limit = getChunkCharLimit(profile, chunks.isEmpty());
            int end = Math.min(sentence.length(), start + limit);
            int breakAt = findBreakPosition(sentence, start, end);
            chunks.add(sentence.substring(start, breakAt).trim());
            start = breakAt;
            while (start < sentence.length() && Character.isWhitespace(sentence.charAt(start))) start++;
        }
    }

    private int getChunkCharLimit(VoiceProfile profile, boolean firstChunk) {
        return firstChunk ? FIRST_CHUNK_CHARS_PIPER : NEXT_CHUNK_CHARS_PIPER;
    }

    private int findBreakPosition(String text, int start, int maxEnd) {
        for (int i = maxEnd - 1; i > start + 40; i--) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch) || ch == ',' || ch == ';' || ch == ':' || ch == '，' || ch == '；' || ch == '：') {
                return i + 1;
            }
        }
        return maxEnd;
    }

    private void flushChunk(List<String> chunks, StringBuilder current) {
        if (current.length() == 0) return;
        chunks.add(current.toString().trim());
        current.setLength(0);
    }

    private void playAudio(float[] samples, int sampleRate) {
        // Convert normalised float [-1, 1] to 16-bit PCM.
        short[] pcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            float clamped = Math.max(-1f, Math.min(1f, samples[i]));
            pcm[i] = (short) (clamped * 32767);
        }

        int minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) {
            Log.e(TAG, "getMinBufferSize returned " + minBuf);
            return;
        }
        // Use MODE_STREAM with a fixed small ring-buffer so longer audio never
        // exceeds the Android static-buffer limit.
        int bufBytes = Math.max(minBuf * 4, 32768);

        AudioTrack track;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bufBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "AudioTrack creation failed", e);
            return;
        }

        synchronized (this) {
            audioTrack = track;
        }

        if (stopped) {
            track.release();
            synchronized (this) { if (audioTrack == track) audioTrack = null; }
            return;
        }

        track.play();

        // Stream PCM in chunks; write() blocks when the ring-buffer is full.
        int chunkShorts = bufBytes / 2;
        int offset = 0;
        while (!stopped && offset < pcm.length) {
            int toWrite = Math.min(chunkShorts, pcm.length - offset);
            int written = track.write(pcm, offset, toWrite);
            if (written < 0) {
                Log.e(TAG, "AudioTrack.write returned " + written);
                break;
            }
            if (written == 0) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) { break; }
                continue;
            }
            offset += written;
        }

        // Let the hardware buffer drain before releasing the track.
        if (!stopped) {
            long drainMs = (long) (bufBytes / 2) * 1000L / sampleRate + 200;
            try { Thread.sleep(drainMs); } catch (InterruptedException ignored) {}
        }

        try { track.stop(); } catch (Exception ignored) {}
        try { track.release(); } catch (Exception ignored) {}
        synchronized (this) { if (audioTrack == track) audioTrack = null; }
    }

    /** Stop any ongoing speech immediately. */
    public void stop() {
        stopped = true;
        if (currentTask != null) {
            currentTask.cancel(false);
            currentTask = null;
        }
        synchronized (this) {
            if (audioTrack != null) {
                try { audioTrack.pause(); } catch (Exception ignored) {}
                try { audioTrack.flush(); } catch (Exception ignored) {}
            }
        }
    }

    /** Release all resources. Call this in Activity.onDestroy(). */
    public void shutdown() {
        stop();
        executor.shutdownNow();
        prewarmExecutor.shutdownNow();
        synchronized (this) {
            releaseTtsLocked();
        }
    }

    private void releaseTtsLocked() {
        if (tts != null) {
            tts.release();
            tts = null;
        }
        loadedProfile = null;
        warmedProfile = null;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
