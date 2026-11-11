package com.qingchen.radio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 开机自启接收器。
 *
 * 车机的"开机"有两层意思，这里都接住了：
 *   1) 车机系统真正重启 → 收到 BOOT_COMPLETED；
 *   2) 只是熄火断电再通电（系统没重启，走的休眠唤醒）→ 部分车机会发 QUICKBOOT_POWERON。
 *
 * 收到广播后不直接建播放器，而是把活交给 RadioService：
 * 广播接收器只有 10 秒左右寿命，在这里做联网、建 MediaPlayer 会被系统掐死。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);
        if (!isBoot) {
            return;
        }

        // 用户在界面上把开关关掉了，就什么都不做
        if (!Prefs.isAutoPlayOnBoot(context)) {
            Log.i(TAG, "开机自启已关闭，跳过");
            return;
        }

        Log.i(TAG, "收到开机广播 " + action + "，启动播放服务");
        Intent svc = new Intent(context, RadioService.class);
        svc.setAction(RadioService.ACTION_AUTO_PLAY);

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                // Android 8+ 从后台（广播接收器）启动服务必须走前台服务入口
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception e) {
            // 个别车机 ROM 会拦截自启，失败就记日志，不影响手动打开
            Log.e(TAG, "启动播放服务失败，可能被系统自启策略拦截", e);
        }
    }
}
