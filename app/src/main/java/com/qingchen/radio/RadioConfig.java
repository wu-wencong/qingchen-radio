package com.qingchen.radio;

/**
 * 音源配置 —— 换源只改这个文件。
 *
 * 2026-11-11 实测真实码率（拉流折算，不看文件名）：
 *   蜻蜓 64k.mp3      → 147~159 kbps   ★ 主源（文件名写 64k，实际是高码率 MP3）
 *   喜马拉雅 64.m3u8  → 65 kbps        （HLS / aac）
 *   喜马拉雅 24.m3u8  → 25 kbps        （很糊，别用）
 *   喜马拉雅 128.m3u8 → 返回 {"ret":2011}，是空壳，不可用
 *
 * 为什么主源用 MP3 直链而不是 HLS：
 *   HTTP + MP3 是 Android 4.0 自带播放器最稳的组合；
 *   HLS(m3u8) 能不能播要看车机厂商有没有实现，很多车机根本播不了。
 *
 * 为什么全部用 http 而不是 https：
 *   Android 4.0 / 4.1 的 TLS 只支持到 1.0，用 https 会握手失败、直接没声音。
 */
public final class RadioConfig {

    public static final String STATION_NAME = "清晨音乐台";

    public static final class Source {
        public final String name;
        public final String url;
        /** 实测码率（kbps），只用于界面展示，不参与播放逻辑 */
        public final int kbps;

        public Source(String name, String url, int kbps) {
            this.name = name;
            this.url = url;
            this.kbps = kbps;
        }
    }

    public static final Source[] SOURCES = new Source[]{
            // 主源：高码率 MP3 直链，Android 4.0 原生就能播
            new Source("高清线路 · MP3", "http://lhttp.qingting.fm/live/4915/64k.mp3", 150),
            // 备源：HLS，Android 5.0 以上且厂商实现了 HLS 才播得动
            new Source("备用线路 · HLS", "http://live.xmcdn.com/live/1011/64.m3u8", 65)
    };

    public static final int DEFAULT_INDEX = 0;

    private RadioConfig() {
    }
}
