package com.qingchen.radio;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

/**
 * 主播放页（Android 4.0 版）。
 *
 * 继承系统原生 Activity（不是 AppCompatActivity，那个最低要 Android 5.0），
 * 所以这个页面在 4.0 的车机上也能正常跑。
 */
public class MainActivity extends Activity {

    private TextView tvProgram;
    private TextView tvStatus;
    private Button btnPlay;
    private CheckBox cbAutoPlay;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable ticker;

    private final RadioService.StateListener stateListener = new RadioService.StateListener() {
        @Override
        public void onStateChanged(final int state, int sourceIndex) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshUi(state);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvProgram = (TextView) findViewById(R.id.tvProgram);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        btnPlay = (Button) findViewById(R.id.btnPlay);
        cbAutoPlay = (CheckBox) findViewById(R.id.cbAutoPlay);

        showSystemVersion();
        showQuality();

        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle();
            }
        });

        // 先 setChecked 再挂监听，否则初始化时会被当成一次"用户点击"写一遍存储
        cbAutoPlay.setChecked(Prefs.isAutoPlayOnBoot(this));
        cbAutoPlay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setAutoPlayOnBoot(MainActivity.this, isChecked);
            }
        });

        refreshUi(RadioService.getState());
        startTicker();
    }

    /**
     * 把车机真实系统版本直接打在界面上。
     * 标称"安卓 6.0"、实际是 4.4 的车机很常见，装上这个 App 就能一眼核实。
     */
    private void showSystemVersion() {
        TextView tv = (TextView) findViewById(R.id.tvVersion);
        int api = Build.VERSION.SDK_INT;

        String note;
        if (api < 14) {
            note = " · 系统过低，可能无法运行";
        } else if (api < 17) {
            note = " · 已启用 http 音源适配";
        } else if (api < 21) {
            note = " · 老系统，建议走 MP3 线路";
        } else {
            note = " · 兼容模式正常";
        }

        tv.setText(getString(R.string.version_prefix)
                + Build.VERSION.RELEASE
                + " (API " + api + ")" + note);
    }

    /** 音质档位直接读配置里的实测码率，不写死一个好听的数字 */
    private void showQuality() {
        TextView tv = (TextView) findViewById(R.id.tvQuality);
        RadioConfig.Source s = RadioConfig.SOURCES[RadioConfig.DEFAULT_INDEX];
        tv.setText(getString(R.string.quality_prefix)
                + s.kbps
                + getString(R.string.quality_suffix));
    }

    private void toggle() {
        Intent i = new Intent(this, RadioService.class);
        i.setAction(RadioService.ACTION_TOGGLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private void refreshUi(int state) {
        switch (state) {
            case RadioService.S_PLAYING:
                btnPlay.setText(R.string.action_pause);
                tvStatus.setText(R.string.status_playing);
                break;
            case RadioService.S_CONNECTING:
                btnPlay.setText(R.string.action_pause);
                tvStatus.setText(R.string.status_connecting);
                break;
            case RadioService.S_PAUSED:
                btnPlay.setText(R.string.action_play);
                tvStatus.setText(R.string.status_paused);
                break;
            default:
                btnPlay.setText(R.string.action_play);
                tvStatus.setText(R.string.status_idle);
                break;
        }
        tvProgram.setText(ProgramSchedule.current());
    }

    /** 每分钟刷新一次，跨整点自动切换到对应节目名 */
    private void startTicker() {
        ticker = new Runnable() {
            @Override
            public void run() {
                tvProgram.setText(ProgramSchedule.current());
                handler.postDelayed(this, 60000);
            }
        };
        handler.post(ticker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        RadioService.listener = stateListener;
        refreshUi(RadioService.getState());
    }

    @Override
    protected void onPause() {
        // 必须摘掉，否则 Service 持有 Activity 会造成内存泄漏
        RadioService.listener = null;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (ticker != null) {
            handler.removeCallbacks(ticker);
        }
        super.onDestroy();
    }
}
