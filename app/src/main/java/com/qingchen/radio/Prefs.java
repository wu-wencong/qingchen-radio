package com.qingchen.radio;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 极简偏好设置。
 *
 * 只有一项：开机是否自动播放。
 * 用 getSharedPreferences 而不是 PreferenceManager —— 后者在 API 29 已废弃，
 * 前者从 API 1 就有，两个版本都能用、行为一致。
 */
public final class Prefs {

    private static final String FILE = "qingchen_radio";
    private static final String KEY_AUTO_PLAY = "auto_play_on_boot";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** 默认开启：用户要的就是"插上钥匙就出声" */
    public static boolean isAutoPlayOnBoot(Context c) {
        return sp(c).getBoolean(KEY_AUTO_PLAY, true);
    }

    public static void setAutoPlayOnBoot(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_AUTO_PLAY, on).apply();
    }

    private Prefs() {
    }
}
