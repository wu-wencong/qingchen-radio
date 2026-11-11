package com.qingchen.radio;

import java.util.Calendar;

/**
 * 今日节目表 —— 数据来自喜马拉雅「清晨音乐台」直播页公开的节目单。
 * 纯本地按手机时间算，不联网、不抓接口，所以老车机上也不会有任何卡顿。
 */
public final class ProgramSchedule {

    private static final int[] START = {0, 7, 9, 13, 17, 19, 20, 21, 22, 23};
    private static final int[] END = {7, 9, 13, 17, 19, 20, 21, 22, 23, 24};

    private static final String[] NAME = {
            "音乐慢吧", "早安音乐秀", "Music伴你行", "只听好歌不听话", "好音乐放肆听",
            "乐在点歌", "乐在点歌", "你的故事我的歌", "音乐爱上夜", "晚安城市"
    };

    private static final String[] HOST = {
            "", "", "", "", "", "晨晨", "晨晨", "微风", "微风", "微风"
    };

    private ProgramSchedule() {
    }

    /** 返回形如「Music伴你行  09:00–13:00」 */
    public static String current() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        for (int i = 0; i < START.length; i++) {
            if (hour >= START[i] && hour < END[i]) {
                String host = (HOST[i].length() == 0) ? "" : " · " + HOST[i];
                return NAME[i] + host + "  " + pad(START[i]) + ":00–" + pad(END[i]) + ":00";
            }
        }
        return "全天直播 · 好音乐不打烊";
    }

    private static String pad(int h) {
        if (h == 24) {
            return "00";
        }
        return (h < 10) ? "0" + h : String.valueOf(h);
    }
}
