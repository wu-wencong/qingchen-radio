package com.qingchen.radio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * 播放服务（Android 4.0 版）。
 *
 * 用系统自带 MediaPlayer，不用 ExoPlayer / Media3：
 *  - Media3 最低要求 Android 5.0，装不进 4.0 的车机；
 *  - MediaPlayer 系统内置，不增加包体积，低配车机内存占用最小；
 *  - 播 HTTP 上的 MP3 直播流是它最擅长的场景，从 Android 1.0 就支持。
 *
 * 全文件不使用 lambda，只用匿名内部类，避免任何 desugar 依赖。
 */
public class RadioService extends Service {

    private static final String TAG = "RadioService";

    public static final String ACTION_TOGGLE = "com.qingchen.radio.action.TOGGLE";
    /** ★ 开机自启专用：不等用户点，自己开播 */
    public static final String ACTION_AUTO_PLAY = "com.qingchen.radio.action.AUTO_PLAY";

    private static final String CHANNEL_ID = "radio_playback";
    private static final int NOTIF_ID = 1011;

    /**
     * 开机后等一会儿再拉流。
     * 车机刚启动时网络通常还没连上、音频通道也还没就绪，
     * 立刻开播大概率失败，多次失败反而更容易被系统判定为异常应用。
     */
    private static final int AUTO_PLAY_DELAY_MS = 4000;

    public static final int S_IDLE = 0;
    public static final int S_CONNECTING = 1;
    public static final int S_PLAYING = 2;
    public static final int S_PAUSED = 3;

    /**
     * 状态回调。Activity 在 onResume 里挂上、onPause 里摘掉，
     * 摘干净就不会泄漏。这种写法比 bindService 简单，单页面场景完全够用。
     */
    public interface StateListener {
        void onStateChanged(int state, int sourceIndex);
    }

    public static volatile StateListener listener;

    private static volatile int sState = S_IDLE;
    private static volatile int sSourceIndex = RadioConfig.DEFAULT_INDEX;

    public static int getState() {
        return sState;
    }

    public static int getSourceIndex() {
        return sSourceIndex;
    }

    private MediaPlayer player;
    private AudioManager audioManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private boolean foreground = false;
    /** 用户意图：true 表示"想让它响着"，用于暂停/重连时判断要不要继续 */
    private boolean wantPlaying = false;
    /** 已经排了开机自动播放，还没到点 */
    private boolean pendingAutoPlay = false;

    private final AudioManager.OnAudioFocusChangeListener focusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        // 别的应用要长期占用（导航、通话），让路
                        stopPlayback();
                    } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pausePlayback();
                    }
                }
            };

    private final Runnable autoPlayRunnable = new Runnable() {
        @Override
        public void run() {
            if (!pendingAutoPlay) {
                return;
            }
            pendingAutoPlay = false;
            Log.i(TAG, "开机延迟到期，开始播放");
            requestFocus();
            startPlayback(sSourceIndex);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createChannelIfNeeded();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 只要是被 startForegroundService 拉起来的，就必须尽快 startForeground，
        // 否则 Android 8+ 会抛 ForegroundServiceDidNotStartInTimeException。
        ensureForeground();

        // 系统重启服务的场景（intent 为空）：不自动出声，等用户自己点
        if (intent == null) {
            setState(S_PAUSED);
            return START_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_AUTO_PLAY.equals(action)) {
            // 开机自启：先占住前台和音频焦点，延迟几秒再真正拉流
            requestFocus();
            setState(S_CONNECTING);
            updateNotification();
            pendingAutoPlay = true;
            handler.removeCallbacks(autoPlayRunnable);
            handler.postDelayed(autoPlayRunnable, AUTO_PLAY_DELAY_MS);

        } else if (ACTION_TOGGLE.equals(action)) {
            // 用户一旦手动操作，就取消还没执行的开机自动播放
            pendingAutoPlay = false;
            handler.removeCallbacks(autoPlayRunnable);

            if (sState == S_PLAYING || sState == S_CONNECTING) {
                pausePlayback();
            } else {
                requestFocus();
                startPlayback(sSourceIndex);
            }

        } else if (sState != S_PLAYING) {
            requestFocus();
            startPlayback(sSourceIndex);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;   // 不使用 bind，Activity 通过静态回调取状态
    }

    // ==================== 播放控制 ====================

    private void startPlayback(int index) {
        sSourceIndex = index;
        wantPlaying = true;
        setState(S_CONNECTING);
        updateNotification();
        releasePlayerQuietly();

        MediaPlayer mp = new MediaPlayer();
        // 必须指定 STREAM_MUSIC：不指定的话车机上可能走通话通道，出现没声音或声音极小
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player = mp;

        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer m) {
                if (!wantPlaying) {
                    return;
                }
                retryCount = 0;
                m.start();
                setState(S_PLAYING);
                updateNotification();
            }
        });

        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer m, int what, int extra) {
                Log.w(TAG, "播放出错 what=" + what + " extra=" + extra);
                handleError();
                return true;   // 自己处理，不让系统弹错误框
            }
        });

        mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer m, int what, int extra) {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    setState(S_CONNECTING);
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    setState(S_PLAYING);
                }
                return false;
            }
        });

        try {
            mp.setDataSource(RadioConfig.SOURCES[index].url);
            mp.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "打开音源失败: " + RadioConfig.SOURCES[index].url, e);
            handleError();
        }
    }

    /** 断线处理：同一条线路先重试 2 次，还不行就换下一条（主源 → 备用源） */
    private void handleError() {
        releasePlayerQuietly();

        if (!wantPlaying) {
            setState(S_PAUSED);
            return;
        }

        retryCount++;
        setState(S_CONNECTING);
        updateNotification();

        final int nextIndex;
        if (retryCount <= 2) {
            nextIndex = sSourceIndex;
        } else {
            retryCount = 0;
            nextIndex = (sSourceIndex + 1) % RadioConfig.SOURCES.length;
            Log.i(TAG, "重试多次失败，切换到线路 " + nextIndex);
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (wantPlaying) {
                    startPlayback(nextIndex);
                }
            }
        }, 3000);
    }

    private void pausePlayback() {
        wantPlaying = false;
        pendingAutoPlay = false;
        handler.removeCallbacks(autoPlayRunnable);
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.pause();
                }
            } catch (Exception ignored) {
            }
        }
        setState(S_PAUSED);
        updateNotification();
    }

    private void stopPlayback() {
        wantPlaying = false;
        pendingAutoPlay = false;
        handler.removeCallbacksAndMessages(null);
        releasePlayerQuietly();
        setState(S_IDLE);
        abandonFocus();
        if (foreground) {
            stopForeground(true);
            foreground = false;
        }
        stopSelf();
    }

    private void releasePlayerQuietly() {
        if (player != null) {
            try {
                player.reset();
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    // ==================== 音频焦点 ====================

    private void requestFocus() {
        if (audioManager == null) {
            return;
        }
        try {
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        } catch (Exception ignored) {
        }
    }

    private void abandonFocus() {
        if (audioManager == null) {
            return;
        }
        try {
            audioManager.abandonAudioFocus(focusListener);
        } catch (Exception ignored) {
        }
    }

    // ==================== 前台服务与通知 ====================

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "电台播放",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("清晨音乐台播放控制");
        nm.createNotificationChannel(ch);
    }

    private void ensureForeground() {
        if (foreground) {
            updateNotification();
            return;
        }
        try {
            startForeground(NOTIF_ID, buildNotification());
            foreground = true;
        } catch (Exception e) {
            Log.e(TAG, "startForeground 失败", e);
        }
    }

    private void updateNotification() {
        if (!foreground) {
            return;
        }
        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIF_ID, buildNotification());
            }
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification() {
        boolean playing = (sState == S_PLAYING);

        PendingIntent contentPI = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), pendingFlags());

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }

        b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(RadioConfig.STATION_NAME)
                .setContentText(getString(playing ? R.string.notif_playing : R.string.notif_paused))
                .setContentIntent(contentPI)
                .setOngoing(playing);

        // 通知栏播放/暂停按钮：addAction 从 Android 4.1（API 16）开始才有
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            Intent toggleIntent = new Intent(this, RadioService.class);
            toggleIntent.setAction(ACTION_TOGGLE);

            PendingIntent togglePI;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8+ 后台不能直接 startService，必须用 getForegroundService
                togglePI = PendingIntent.getForegroundService(this, 1, toggleIntent, pendingFlags());
            } else {
                togglePI = PendingIntent.getService(this, 1, toggleIntent, pendingFlags());
            }

            b.addAction(android.R.drawable.ic_media_pause,
                    getString(playing ? R.string.notif_toggle_pause : R.string.notif_toggle_play),
                    togglePI);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return b.build();
        } else {
            // Android 4.0 的 Builder 还没有 build()，用老的 getNotification()
            return b.getNotification();
        }
    }

    private int pendingFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.FLAG_UPDATE_CURRENT;
    }

    // ==================== 状态广播 ====================

    private void setState(int state) {
        sState = state;
        StateListener l = listener;
        if (l != null) {
            try {
                l.onStateChanged(state, sSourceIndex);
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== 生命周期 ====================

    /** 从最近任务里划掉 App 时彻底停掉，避免"关不掉" */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopPlayback();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releasePlayerQuietly();
        abandonFocus();
        super.onDestroy();
    }
}
