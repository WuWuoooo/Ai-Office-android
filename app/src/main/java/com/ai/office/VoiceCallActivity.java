package com.ai.office;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceCallActivity extends BaseActivity {

    public static final String EXTRA_MODE = "mode"; // "voice" 或 "video"，仅作初始状态

    private static final int REQ_RECORD_AUDIO = 6101;
    private static final int REQ_CAMERA = 6102;
    private static final int REQ_PROJECTION_VOICE = 6103;

    // 音频参数（GLM-Realtime 协议）
    private static final int SAMPLE_RATE = 24000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_CHUNK_SAMPLES = 480;   // 20ms @24kHz
    private static final int AUDIO_CHUNK_BYTES = AUDIO_CHUNK_SAMPLES * 2;

    // 视频参数
    private static final long VIDEO_FRAME_INTERVAL = 500L;  // 视频帧间隔（2fps）
    private static final long SCREEN_FRAME_INTERVAL = 1000L; // 屏幕共享间隔（1fps）

    // UI
    private FrameLayout root;
    private SurfaceView surfaceView;
    private TextView tvStatus, tvAiCaption, tvUserCaption;
    private ImageView btnClose, btnMic, btnVideo, btnScreen, btnSwitchCamera, btnFlash;
    private LinearLayout topBar, bottomBar;

    // 通话状态
    private RealtimeClient realtime;
    private String sessionModel = "";
    private String sessionVoice = "";
    private String sessionProtocol = "openai";
    private boolean micOn = false;
    private boolean videoOn = false;
    private boolean screenShareOn = false;

    // 音频
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private Thread audioReadThread;
    private final AtomicBoolean audioRunning = new AtomicBoolean(false);
    private final Object audioPlayLock = new Object();

    // 视频
    private Camera camera;
    private int currentCameraId = 1;
    private boolean flashOn = false;
    private final Handler videoHandler = new Handler(Looper.getMainLooper());
    private Runnable videoFrameTask;

    // 屏幕共享
    private final Handler screenHandler = new Handler(Looper.getMainLooper());
    private Runnable screenFrameTask;

    // 字幕累积
    private final StringBuilder aiCaptionBuf = new StringBuilder();
    private final StringBuilder userCaptionBuf = new StringBuilder();

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean alive = true;
    private boolean connected = false;

    // 工具配置（从设置读，用于注册给 realtime session）
    private JSONArray sessionTools;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            alive = true;
            String mode = getIntent() == null ? "voice" : getIntent().getStringExtra(EXTRA_MODE);
            boolean wantVideo = "video".equals(mode);

            buildUi();
            prepareTools();

            if (Build.VERSION.SDK_INT >= 23) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
                } else {
                    startCall(wantVideo);
                }
            } else {
                startCall(wantVideo);
            }
        } catch (Throwable t) {
            Toast.makeText(this, "通话页初始化失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * 通话页专用系统栏：黑色背景 → 状态栏/导航栏透明 + 内容延伸到状态栏下，
     * 状态栏图标保持白色（不设 LIGHT_STATUS_BAR）。覆盖 BaseActivity 的浅色方案。
     */
    @Override
    protected void applySystemBars() {
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                getWindow().setStatusBarColor(0x00000000);
                getWindow().setNavigationBarColor(0x00000000);
            }
            View dv = getWindow().getDecorView();
            int flags = dv.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                   | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                   | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            // 通话页背景是黑色，强制浅色状态栏文字会看不见，因此清掉
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            dv.setSystemUiVisibility(flags);
        } catch (Throwable t) {}
    }

    /** 状态栏高度（px），用于给 topBar 加顶部 padding，避免内容被状态栏遮住 */
    private int statusBarHeightPx() {
        try {
            int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resId > 0) return getResources().getDimensionPixelSize(resId);
        } catch (Throwable t) {}
        return UiUtils.dp(this, 24);
    }

    /** 从设置读取当前启用工具，传给 realtime session */
    private void prepareTools() {
        try {
            sessionTools = AiClient.buildTools(
                    UiUtils.getBool(this, "allow_shell_tool", false),
                    UiUtils.getBool(this, "allow_accessibility_tool", false));
            try { PluginManager.registerTools(this, sessionTools); } catch (Throwable t) {}
        } catch (Throwable t) {
            sessionTools = new JSONArray();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCall(false);
            } else {
                Toast.makeText(this, "未获得录音权限，通话无法进行", Toast.LENGTH_LONG).show();
                finish();
            }
        } else if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVideoInternal();
            } else {
                Toast.makeText(this, "未获得相机权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PROJECTION_VOICE) {
            if (resultCode == RESULT_OK && data != null) {
                try {
                    Intent svc = new Intent(this, ProjectionService.class);
                    svc.putExtra("rc", resultCode);
                    svc.putExtra("data", data);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
                    else startService(svc);
                    Toast.makeText(this, "屏幕共享启动中，几秒后生效", Toast.LENGTH_SHORT).show();
                    screenHandler.postDelayed(new Runnable() {
                        @Override public void run() { startScreenShareInternal(); }
                    }, 1500);
                } catch (Throwable t) {
                    Toast.makeText(this, "启动投屏服务失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "屏幕共享授权被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ============================================================
    // UI 构建（全屏 FrameLayout，UI 叠加在 SurfaceView 上层）
    // ============================================================

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // 全屏 SurfaceView（底层）
        surfaceView = new SurfaceView(this);
        surfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {
                try { if (camera != null) camera.setPreviewDisplay(holder); } catch (Throwable t) {}
            }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                try { if (camera != null) camera.startPreview(); } catch (Throwable t) {}
            }
            @Override public void surfaceDestroyed(SurfaceHolder holder) {}
        });
        root.addView(surfaceView);

        // 半透明遮罩，让 UI 与视频明显区分
        View overlay = new View(this);
        overlay.setBackgroundColor(0x30000000);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        overlay.setClickable(false);
        overlay.setFocusable(false);
        root.addView(overlay);

        int statusH = statusBarHeightPx();

        // 顶部栏
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(UiUtils.dp(this, 12), statusH + UiUtils.dp(this, 8),
                          UiUtils.dp(this, 12), UiUtils.dp(this, 8));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        topBar.setLayoutParams(tlp);

// 关闭/返回按钮：ImageView + 固定 44dp 方形；透明背景，不画圆
btnClose = new ImageView(this);
btnClose.setImageResource(R.drawable.ic_keyboard_backspace_white);
final int closeSize = UiUtils.dp(this, 44);
btnClose.setLayoutParams(new LinearLayout.LayoutParams(closeSize, closeSize));
btnClose.setPadding(UiUtils.dp(this, 8), UiUtils.dp(this, 8),
                    UiUtils.dp(this, 8), UiUtils.dp(this, 8));
btnClose.setScaleType(ImageView.ScaleType.FIT_CENTER);
btnClose.setBackgroundColor(0x00000000);
btnClose.setClickable(true);
btnClose.setFocusable(true);
btnClose.setOnClickListener(new View.OnClickListener() {
    @Override public void onClick(View v) { hangUp(); }
});
topBar.addView(btnClose);

        View spacer1 = new View(this);
        spacer1.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        topBar.addView(spacer1);

        tvStatus = new TextView(this);
        tvStatus.setText("连接中…");
        tvStatus.setTextSize(14);
        tvStatus.setTextColor(0xFFFFFFFF);
        tvStatus.setShadowLayer(3f, 1f, 1f, 0xFF000000);
        tvStatus.setGravity(Gravity.CENTER);
        topBar.addView(tvStatus);

        View spacer2 = new View(this);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        topBar.addView(spacer2);

        // 右侧等宽占位，保持 tvStatus 居中
        View placeholder = new View(this);
        placeholder.setLayoutParams(new LinearLayout.LayoutParams(closeSize, closeSize));
        topBar.addView(placeholder);

        root.addView(topBar);

        // 底部字幕区
        LinearLayout captionBox = new LinearLayout(this);
        captionBox.setOrientation(LinearLayout.VERTICAL);
        captionBox.setGravity(Gravity.BOTTOM);
        captionBox.setPadding(UiUtils.dp(this, 24), 0, UiUtils.dp(this, 24), 0);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        clp.setMargins(0, 0, 0, UiUtils.dp(this, 180));
        captionBox.setLayoutParams(clp);

        tvUserCaption = new TextView(this);
        tvUserCaption.setTextSize(15);
        tvUserCaption.setTextColor(0xFFBBDFFF);
        tvUserCaption.setShadowLayer(4f, 1f, 1f, 0xFF000000);
        tvUserCaption.setGravity(Gravity.CENTER);
        tvUserCaption.setVisibility(View.GONE);
        captionBox.addView(tvUserCaption);

        tvAiCaption = new TextView(this);
        tvAiCaption.setTextSize(16);
        tvAiCaption.setTextColor(0xFFFFFFFF);
        tvAiCaption.setShadowLayer(4f, 1f, 1f, 0xFF000000);
        tvAiCaption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams aiLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        aiLp.setMargins(0, UiUtils.dp(this, 8), 0, 0);
        captionBox.addView(tvAiCaption, aiLp);

        root.addView(captionBox);

        // 底部按钮栏
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 20),
                             UiUtils.dp(this, 12), UiUtils.dp(this, 40));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        bottomBar.setLayoutParams(blp);

        // 麦克风（默认显示"关"状态的图标，通话开始后自动开启会切换）
        btnMic = makeCircleIconButton(R.drawable.ic_microphone_off_white, 64, 0xFF444444);
        btnMic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleMic(); }
        });
        bottomBar.addView(btnMic);

        View gap1 = new View(this);
        gap1.setLayoutParams(new LinearLayout.LayoutParams(UiUtils.dp(this, 16), 1));
        bottomBar.addView(gap1);

        // 视频（用相机图标代表）
        btnVideo = makeCircleIconButton(R.drawable.ic_camera_flip_outline_white, 64, 0xFF444444);
        btnVideo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleVideo(); }
        });
        bottomBar.addView(btnVideo);

        View gap2 = new View(this);
        gap2.setLayoutParams(new LinearLayout.LayoutParams(UiUtils.dp(this, 16), 1));
        bottomBar.addView(gap2);

        // 屏幕共享
        btnScreen = makeCircleIconButton(R.drawable.ic_monitor_screenshot_white, 64, 0xFF444444);
        btnScreen.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleScreenShare(); }
        });
        bottomBar.addView(btnScreen);

        // 右侧竖排辅助按钮（切摄像头、手电筒）
        LinearLayout auxBox = new LinearLayout(this);
        auxBox.setOrientation(LinearLayout.VERTICAL);
        auxBox.setGravity(Gravity.END);
        FrameLayout.LayoutParams auxLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        auxLp.setMargins(0, 0, UiUtils.dp(this, 12), 0);
        auxBox.setLayoutParams(auxLp);
        auxBox.setVisibility(View.GONE);

        btnSwitchCamera = makeCircleIconButton(R.drawable.ic_camera_flip_outline_white, 44, 0xAA444444);
        btnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchCamera(); }
        });
        auxBox.addView(btnSwitchCamera);

        View gap3 = new View(this);
        gap3.setLayoutParams(new LinearLayout.LayoutParams(1, UiUtils.dp(this, 12)));
        auxBox.addView(gap3);

        btnFlash = makeCircleIconButton(R.drawable.ic_flashlight_off_white, 44, 0xAA444444);
        btnFlash.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleFlash(); }
        });
        auxBox.addView(btnFlash);

        root.addView(auxBox);

        root.addView(bottomBar);

        setContentView(root);
    }

    /** 通用圆形图标按钮（ImageView + 固定方形 + oval 背景） */
    private ImageView makeCircleIconButton(int resId, int sizeDp, int bgColor) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(resId);
        int s = UiUtils.dp(this, sizeDp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
        iv.setLayoutParams(lp);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundResource(R.drawable.circle_btn_bg);
        try { iv.getBackground().setTint(bgColor); } catch (Throwable t) {}
        int p = UiUtils.dp(this, sizeDp / 4);
        iv.setPadding(p, p, p, p);
        iv.setClickable(true);
        iv.setFocusable(true);
        return iv;
    }

    private void setButtonTint(View btn, int color) {
        try {
            if (btn != null && btn.getBackground() != null) {
                btn.getBackground().setTint(color);
            }
        } catch (Throwable t) {}
    }

    private void updateStatus(String s) {
        if (tvStatus != null) tvStatus.setText(s);
    }

    // ============================================================
    // 连接与断开
    // ============================================================

    /** 启动通话：连接 Realtime，然后按需开启麦克风 / 视频 */
    private void startCall(boolean wantVideo) {
        try {
            String[] cfg = RealtimeConfig.resolve(this);
            String url = cfg[0];
            String model = cfg[1];
            String key = cfg[2];
            String protocol = cfg[3];
            String voice = cfg[4];

            sessionModel = model;
            sessionVoice = voice;
            sessionProtocol = protocol;

            if (url.length() == 0 || key.length() == 0 || model.length() == 0) {
                Toast.makeText(this, "实时通话配置不完整，请到设置里填写地址 / Key / 模型", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            realtime = new RealtimeClient();

            String authStyle = RealtimeConfig.authStyleOf(
                    UiUtils.getStr(this, "realtime_provider", "glm-realtime"));

            updateStatus("连接中…");
            realtime.connect(url, key, authStyle, protocol, new RealtimeClient.Listener() {
                @Override public void onConnected() {
                    ui.post(new Runnable() { @Override public void run() {
                        connected = true;
                        updateStatus("已连接");
                        sendSessionUpdate();
                        // 默认先开启麦克风
                        ui.postDelayed(new Runnable() {
                            @Override public void run() { if (alive && !micOn) toggleMic(); }
                        }, 600);
                    }});
                }

                @Override public void onAudioDelta(final String base64Pcm) {
                    playAudioChunk(base64Pcm);
                }

                @Override public void onTranscriptDelta(final String text) {
                    ui.post(new Runnable() { @Override public void run() {
                        aiCaptionBuf.append(text);
                        if (tvAiCaption != null) tvAiCaption.setText(aiCaptionBuf.toString());
                    }});
                }

                @Override public void onTextDelta(final String text) {
                    ui.post(new Runnable() { @Override public void run() {
                        aiCaptionBuf.append(text);
                        if (tvAiCaption != null) tvAiCaption.setText(aiCaptionBuf.toString());
                    }});
                }

                @Override public void onUserTranscript(final String text) {
                    ui.post(new Runnable() { @Override public void run() {
                        userCaptionBuf.append(text);
                        if (tvUserCaption != null) {
                            tvUserCaption.setVisibility(View.VISIBLE);
                            tvUserCaption.setText("你说：" + userCaptionBuf.toString());
                        }
                    }});
                }

                @Override public void onFunctionCall(final String callId, final String name, final String argsJson) {
                    ui.post(new Runnable() { @Override public void run() {
                        handleFunctionCall(callId, name, argsJson);
                    }});
                }

                @Override public void onResponseDone() {
                    ui.post(new Runnable() { @Override public void run() {
                        aiCaptionBuf.setLength(0);
                        ui.postDelayed(new Runnable() {
                            @Override public void run() {
                                if (tvAiCaption != null) tvAiCaption.setText("");
                            }
                        }, 3000);
                    }});
                }

                @Override public void onError(final String message) {
                    ui.post(new Runnable() { @Override public void run() {
                        updateStatus("错误: " + message);
                    }});
                }

                @Override public void onClosed(final int code, final String reason) {
                    ui.post(new Runnable() { @Override public void run() {
                        connected = false;
                        if (alive) updateStatus("连接已断开 (" + code + ")");
                    }});
                }
            });

            // 若初始就是视频模式，连接后自动开视频
            if (wantVideo) {
                ui.postDelayed(new Runnable() {
                    @Override public void run() { if (alive && !videoOn) toggleVideo(); }
                }, 1500);
            }
        } catch (Throwable t) {
            Toast.makeText(this, "启动通话失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /** 会话建立后发送 session.update */
    private void sendSessionUpdate() {
        if (realtime == null) return;
        try {
            String sysPrompt = UiUtils.getStr(this, "system_prompt", "");
            if (sysPrompt == null || sysPrompt.trim().length() == 0) {
                sysPrompt = "你是一个运行在手机上的 AI 办公助手。用户会通过语音与你交流，"
                        + "请用简洁自然的口语回答。当用户需要读取文件、联网搜索或操作手机时，"
                        + "可以调用工具。当前是实时通话，回答不要太长。";
            }
            realtime.updateSession(sessionModel, sessionVoice, sysPrompt, sessionTools);
        } catch (Throwable t) {}
    }

    private void hangUp() {
        alive = false;
        try { stopMicInternal(); } catch (Throwable t) {}
        try { stopVideoInternal(); } catch (Throwable t) {}
        try { stopScreenShareInternal(); } catch (Throwable t) {}
        try { if (realtime != null) realtime.close(); } catch (Throwable t) {}
        try { ProjectionController.stop(); } catch (Throwable t) {}
        finish();
    }

    // ============================================================
    // 麦克风
    // ============================================================

    private void toggleMic() {
        if (micOn) {
            stopMicInternal();
            setButtonTint(btnMic, 0xFF444444);
            if (btnMic != null) btnMic.setImageResource(R.drawable.ic_microphone_off_white);
            updateStatus("麦克风已关闭");
        } else {
            startMicInternal();
            setButtonTint(btnMic, 0xFF4A6CF7);
            if (btnMic != null) btnMic.setImageResource(R.drawable.ic_microphone_outline_white);
            updateStatus("通话中");
        }
        micOn = !micOn;
    }

    private void startMicInternal() {
        try {
            // 优先级：24k（GLM 原生）→ 16k（多数设备都支持，重采样到 24k）→ 48k
            int[] candidates = new int[]{24000, 16000, 48000};
            int chosenRate = 0;
            for (int i = 0; i < candidates.length; i++) {
                AudioRecord r = tryOpenMic(candidates[i]);
                if (r != null) {
                    audioRecord = r;
                    chosenRate = candidates[i];
                    break;
                }
            }
            if (audioRecord == null) {
                Toast.makeText(this, "麦克风初始化失败（设备不支持 24k/16k/48k）", Toast.LENGTH_LONG).show();
                return;
            }

            audioRecord.startRecording();
            audioRunning.set(true);

            final int fRate = chosenRate;
            audioReadThread = new Thread(new Runnable() {
                @Override public void run() {
                    byte[] buf = new byte[AUDIO_CHUNK_BYTES];
                    while (audioRunning.get() && alive) {
                        try {
                            int n = audioRecord.read(buf, 0, buf.length);
                            if (n <= 0) continue;
                            if (realtime == null || !connected) continue;
                            byte[] payload;
                            if (n == buf.length) {
                                payload = buf;
                            } else {
                                payload = new byte[n];
                                System.arraycopy(buf, 0, payload, 0, n);
                            }
                            if (fRate != SAMPLE_RATE) {
                                payload = resamplePcm16(payload, fRate, SAMPLE_RATE);
                            }
                            String b64 = Base64.encodeToString(payload, Base64.NO_WRAP);
                            realtime.appendAudio(b64);
                        } catch (Throwable t) {
                            break;
                        }
                    }
                }
            }, "audio-read");
            audioReadThread.start();
        } catch (Throwable t) {
            Toast.makeText(this, "启动麦克风失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopMicInternal() {
        audioRunning.set(false);
        try {
            if (audioRecord != null) {
                try { audioRecord.stop(); } catch (Throwable t) {}
                try { audioRecord.release(); } catch (Throwable t) {}
                audioRecord = null;
            }
        } catch (Throwable t) {}
        try {
            if (audioReadThread != null) {
                audioReadThread.interrupt();
                audioReadThread = null;
            }
        } catch (Throwable t) {}
    }

    // ============================================================
    // 播放 AI 音频
    // ============================================================

    private void playAudioChunk(String base64Pcm) {
        try {
            if (base64Pcm == null || base64Pcm.length() == 0) return;
            byte[] pcm = Base64.decode(base64Pcm, Base64.DEFAULT);
            if (pcm.length == 0) return;

            if (audioTrack == null) {
                int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT);
                int bufSize = Math.max(minBuf, AUDIO_CHUNK_BYTES * 8);
                audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT,
                        bufSize, AudioTrack.MODE_STREAM);
                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                    audioTrack = null;
                    return;
                }
                audioTrack.play();
            }

            synchronized (audioPlayLock) {
                if (audioTrack != null) {
                    audioTrack.write(pcm, 0, pcm.length);
                }
            }
        } catch (Throwable t) {
            // 忽略播放错误，不中断通话
        }
    }

    private void stopAudioPlayback() {
        try {
            synchronized (audioPlayLock) {
                if (audioTrack != null) {
                    try { audioTrack.stop(); } catch (Throwable t) {}
                    try { audioTrack.release(); } catch (Throwable t) {}
                    audioTrack = null;
                }
            }
        } catch (Throwable t) {}
    }

    // ============================================================
    // 视频
    // ============================================================

    private void toggleVideo() {
        if (videoOn) {
            stopVideoInternal();
            setButtonTint(btnVideo, 0xFF444444);
            if (!screenShareOn) updateStatus("摄像头已关闭");
        } else {
            if (Build.VERSION.SDK_INT >= 23
                    && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                return;
            }
            startVideoInternal();
            setButtonTint(btnVideo, 0xFF4A6CF7);
            updateStatus("视频已开启");
        }
        videoOn = !videoOn;
    }

    private void startVideoInternal() {
        try {
            stopVideoInternal();
            int numCams = Camera.getNumberOfCameras();
            if (numCams == 0) {
                Toast.makeText(this, "未找到摄像头", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentCameraId >= numCams) currentCameraId = 0;
            camera = Camera.open(currentCameraId);
            try {
                Camera.Parameters p = camera.getParameters();
                p.set("orientation", "portrait");
                try { camera.setDisplayOrientation(90); } catch (Throwable t) {}
                camera.setParameters(p);
            } catch (Throwable t) {}
            try {
                camera.setPreviewDisplay(surfaceView.getHolder());
            } catch (Throwable t) {}
            camera.startPreview();

            showAuxButtons(true);
            startVideoFrameLoop();
        } catch (Throwable t) {
            camera = null;
            Toast.makeText(this, "打开摄像头失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showAuxButtons(boolean show) {
        try {
            if (btnSwitchCamera == null) return;
            ViewGroup parent = (ViewGroup) btnSwitchCamera.getParent();
            if (parent != null) parent.setVisibility(show ? View.VISIBLE : View.GONE);
        } catch (Throwable t) {}
    }

    private void startVideoFrameLoop() {
        stopVideoFrameLoop();
        videoFrameTask = new Runnable() {
            @Override public void run() {
                if (!alive || !videoOn) return;
                try {
                    if (camera != null && realtime != null && connected) {
                        camera.takePicture(null, null, new Camera.PictureCallback() {
                            @Override public void onPictureTaken(byte[] data, Camera cam) {
                                try {
                                    if (data != null && realtime != null && connected) {
                                        String b64 = compressJpeg(data, 640, 60);
                                        if (b64 != null) realtime.appendImage(b64);
                                    }
                                } catch (Throwable t) {}
                                try { cam.startPreview(); } catch (Throwable t) {}
                            }
                        });
                    }
                } catch (Throwable t) {}
                if (alive && videoOn) videoHandler.postDelayed(this, VIDEO_FRAME_INTERVAL);
            }
        };
        videoHandler.postDelayed(videoFrameTask, VIDEO_FRAME_INTERVAL);
    }

    private void stopVideoFrameLoop() {
        try {
            if (videoFrameTask != null) {
                videoHandler.removeCallbacks(videoFrameTask);
                videoFrameTask = null;
            }
        } catch (Throwable t) {}
    }

    private void stopVideoInternal() {
        stopVideoFrameLoop();
        try {
            if (camera != null) {
                try { setFlashInternal(false); } catch (Throwable t) {}
                try { camera.stopPreview(); } catch (Throwable t) {}
                try { camera.release(); } catch (Throwable t) {}
                camera = null;
            }
        } catch (Throwable t) { camera = null; }
        flashOn = false;
        if (btnFlash != null) btnFlash.setImageResource(R.drawable.ic_flashlight_off_white);
        showAuxButtons(false);
    }

    /** 通用：把 JPEG 缩放后转 base64 */
    private String compressJpeg(byte[] jpeg, int maxPx, int quality) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while ((o.outWidth / sample) > maxPx * 2 || (o.outHeight / sample) > maxPx * 2) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o2);
            if (bmp == null) return null;

            // 前置摄像头默认横向，旋转 270 度
            try {
                if (currentCameraId == 1) {
                    Matrix m = new Matrix();
                    m.postRotate(270);
                    Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                    if (rotated != bmp) { bmp.recycle(); bmp = rotated; }
                }
            } catch (Throwable t) {}

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            try { bmp.recycle(); } catch (Throwable t) {}
            return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) { return null; }
    }

    private void switchCamera() {
        try {
            int numCams = Camera.getNumberOfCameras();
            if (numCams < 2) {
                Toast.makeText(this, "本设备只有 1 个摄像头", Toast.LENGTH_SHORT).show();
                return;
            }
            try { setFlashInternal(false); } catch (Throwable t) {}
            currentCameraId = (currentCameraId == 0) ? 1 : 0;
            if (videoOn) startVideoInternal();
            if (flashOn) {
                try { setFlashInternal(true); } catch (Throwable t) {}
            }
            Toast.makeText(this, currentCameraId == 0 ? "已切换为后置" : "已切换为前置", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "切换摄像头失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleFlash() {
        if (camera == null) {
            Toast.makeText(this, "相机未就绪", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasFlash()) {
            Toast.makeText(this, "当前摄像头无闪光灯", Toast.LENGTH_SHORT).show();
            return;
        }
        flashOn = !flashOn;
        boolean ok = setFlashInternal(flashOn);
        if (ok) {
            if (btnFlash != null) {
                btnFlash.setImageResource(flashOn
                        ? R.drawable.ic_flashlight_white
                        : R.drawable.ic_flashlight_off_white);
            }
            Toast.makeText(this, flashOn ? "手电筒已开" : "手电筒已关", Toast.LENGTH_SHORT).show();
        } else {
            flashOn = !flashOn;
            Toast.makeText(this, "手电筒切换失败", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean setFlashInternal(boolean on) {
        try {
            if (camera == null) return false;
            Camera.Parameters p = camera.getParameters();
            p.setFlashMode(on ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(p);
            return true;
        } catch (Throwable t) { return false; }
    }

    private boolean hasFlash() {
        try {
            if (camera == null) return false;
            Camera.Parameters p = camera.getParameters();
            List<String> modes = p.getSupportedFlashModes();
            return modes != null && modes.contains(Camera.Parameters.FLASH_MODE_TORCH);
        } catch (Throwable t) { return false; }
    }

    // ============================================================
    // 屏幕共享
    // ============================================================

    private void toggleScreenShare() {
        if (screenShareOn) {
            stopScreenShareInternal();
            setButtonTint(btnScreen, 0xFF444444);
            updateStatus("屏幕共享已停止");
            screenShareOn = false;
        } else {
            if (!ProjectionController.isReady()) {
                try {
                    android.media.projection.MediaProjectionManager mpm =
                            (android.media.projection.MediaProjectionManager)
                                    getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION_VOICE);
                } catch (Throwable t) {
                    Toast.makeText(this, "无法请求屏幕共享授权: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
                return;
            }
            startScreenShareInternal();
            setButtonTint(btnScreen, 0xFF4A6CF7);
            updateStatus("屏幕共享中");
            screenShareOn = true;
        }
    }

    private void startScreenShareInternal() {
        screenShareOn = true;
        stopScreenFrameLoop();
        screenFrameTask = new Runnable() {
            @Override public void run() {
                if (!alive || !screenShareOn) return;
                try {
                    if (ProjectionController.isReady() && realtime != null && connected) {
                        Bitmap bmp = ProjectionController.capture();
                        if (bmp != null) {
                            int maxW = 720;
                            if (bmp.getWidth() > maxW) {
                                float r = (float) maxW / bmp.getWidth();
                                Bitmap sc = Bitmap.createScaledBitmap(bmp, maxW,
                                        Math.max(1, Math.round(bmp.getHeight() * r)), true);
                                if (sc != bmp) bmp.recycle();
                                bmp = sc;
                            }
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            bmp.compress(Bitmap.CompressFormat.JPEG, 55, bos);
                            try { bmp.recycle(); } catch (Throwable t) {}
                            String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                            realtime.appendImage(b64);
                        }
                    }
                } catch (Throwable t) {}
                if (alive && screenShareOn) screenHandler.postDelayed(this, SCREEN_FRAME_INTERVAL);
            }
        };
        screenHandler.postDelayed(screenFrameTask, SCREEN_FRAME_INTERVAL);
    }

    private void stopScreenFrameLoop() {
        try {
            if (screenFrameTask != null) {
                screenHandler.removeCallbacks(screenFrameTask);
                screenFrameTask = null;
            }
        } catch (Throwable t) {}
    }

    private void stopScreenShareInternal() {
        screenShareOn = false;
        stopScreenFrameLoop();
        try { ProjectionController.stop(); } catch (Throwable t) {}
    }

    // ============================================================
    // 函数调用
    // ============================================================

    private void handleFunctionCall(String callId, String name, String argsJson) {
        try {
            final String fCallId = callId;
            final String fName = name;
            final String fArgs = argsJson;
            new Thread(new Runnable() {
                @Override public void run() {
                    String result;
                    try {
                        result = runToolInternal(fName, fArgs);
                    } catch (Throwable t) {
                        result = "工具执行失败: " + t.getMessage();
                    }
                    if (result != null && result.length() > 4000) {
                        result = result.substring(0, 4000) + "\n...(已截断)";
                    }
                    try {
                        JSONObject out = new JSONObject();
                        out.put("result", result == null ? "" : result);
                        if (realtime != null && connected) {
                            realtime.sendFunctionResult(fCallId, out.toString());
                        }
                    } catch (Throwable t) {}
                }
            }, "realtime-tool").start();
        } catch (Throwable t) {}
    }

    /** 复用 MainActivity 的工具分发逻辑 */
    private String runToolInternal(String tool, String args) {
        try {
            if (tool == null) return "未知工具";
            if (tool.startsWith("plugin_")) return PluginManager.execute(this, tool, args);
            if ("run_shell_command".equals(tool)) return ShellToolExecutor.execute(this, args);
            if ("run_js".equals(tool)) return JsToolExecutor.execute(this, args);
            if ("accessibility_control".equals(tool)) return AccessibilityController.execute(this, args);
            if ("web_search".equals(tool) || "fetch_url".equals(tool)) {
                WebToolExecutor web = new WebToolExecutor(this);
                return web.execute(tool, args);
            }
            FileToolExecutor f = new FileToolExecutor(this);
            return f.execute(tool, args);
        } catch (Throwable t) {
            return "工具执行失败: " + t.getMessage();
        }
    }

    // ============================================================
    // 生命周期
    // ============================================================

    @Override
    protected void onDestroy() {
        alive = false;
        try { stopMicInternal(); } catch (Throwable t) {}
        try { stopVideoInternal(); } catch (Throwable t) {}
        try { stopScreenShareInternal(); } catch (Throwable t) {}
        try { stopAudioPlayback(); } catch (Throwable t) {}
        try { if (realtime != null) realtime.close(); } catch (Throwable t) {}
        try {
            if (videoHandler != null) videoHandler.removeCallbacksAndMessages(null);
        } catch (Throwable t) {}
        try {
            if (screenHandler != null) screenHandler.removeCallbacksAndMessages(null);
        } catch (Throwable t) {}
        try {
            if (ui != null) ui.removeCallbacksAndMessages(null);
        } catch (Throwable t) {}
        super.onDestroy();
    }

    // ============================================================
    // 音频重采样（16k/48k → 24k）
    // ============================================================

    /** 简易线性插值重采样：把 src 从 srcRate 重采样到 dstRate。inPcm 为 int16 小端 */
    private static byte[] resamplePcm16(byte[] inPcm, int srcRate, int dstRate) {
        if (inPcm == null || inPcm.length < 4) return inPcm;
        if (srcRate == dstRate) return inPcm;
        try {
            int inSamples = inPcm.length / 2;
            int outSamples = (int) ((long) inSamples * dstRate / srcRate);
            if (outSamples <= 0) return inPcm;
            byte[] out = new byte[outSamples * 2];
            for (int i = 0; i < outSamples; i++) {
                double srcPos = (double) i * srcRate / dstRate;
                int i0 = (int) srcPos;
                int i1 = i0 + 1;
                if (i1 >= inSamples) i1 = inSamples - 1;
                double frac = srcPos - i0;

                int s0 = (short) ((inPcm[i0 * 2] & 0xFF) | ((inPcm[i0 * 2 + 1] & 0xFF) << 8));
                int s1 = (short) ((inPcm[i1 * 2] & 0xFF) | ((inPcm[i1 * 2 + 1] & 0xFF) << 8));
                int sv = (int) (s0 + (s1 - s0) * frac);
                if (sv > Short.MAX_VALUE) sv = Short.MAX_VALUE;
                if (sv < Short.MIN_VALUE) sv = Short.MIN_VALUE;
                out[i * 2] = (byte) (sv & 0xFF);
                out[i * 2 + 1] = (byte) ((sv >> 8) & 0xFF);
            }
            return out;
        } catch (Throwable t) { return inPcm; }
    }

    /** 尝试按给定采样率初始化 AudioRecord；失败返回 null */
    private AudioRecord tryOpenMic(int rate) {
        try {
            int minBuf = AudioRecord.getMinBufferSize(rate, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (minBuf <= 0) return null;
            int bufSize = Math.max(minBuf, AUDIO_CHUNK_BYTES * 4);
            AudioRecord r = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    rate, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize);
            if (r.getState() != AudioRecord.STATE_INITIALIZED) {
                try { r.release(); } catch (Throwable t) {}
                return null;
            }
            return r;
        } catch (Throwable t) { return null; }
    }
}