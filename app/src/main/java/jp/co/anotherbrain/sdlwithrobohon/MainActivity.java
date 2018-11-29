package jp.co.anotherbrain.sdlwithrobohon;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toolbar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import jp.co.anotherbrain.sdlwithrobohon.customize.ScenarioDefinitions;
import jp.co.anotherbrain.sdlwithrobohon.util.VoiceUIManagerUtil;
import jp.co.anotherbrain.sdlwithrobohon.util.VoiceUIVariableUtil.VoiceUIVariableListHelper;
import jp.co.sharp.android.voiceui.VoiceUIManager;
import jp.co.sharp.android.voiceui.VoiceUIVariable;
import jp.co.sharp.android.rb.song.SongUtil;


public class MainActivity extends Activity implements MainActivityVoiceUIListener.MainActivityScenarioCallback {
    public static final String TAG = MainActivity.class.getSimpleName();
    /**
     * 歌実行結果通知用Action定義.
     */
    public static final String ACTION_RESULT_SONG = "jp.co.anotherbrain.sdlwithrobohon.action.RESULT_SONG";
    /**
     * 音声UI制御.
     */
    private VoiceUIManager mVoiceUIManager = null;
    /**
     * 音声UIイベントリスナー.
     */
    private MainActivityVoiceUIListener mMainActivityVoiceUIListener = null;
    /**
     * 音声UIの再起動イベント検知.
     */
    private VoiceUIStartReceiver mVoiceUIStartReceiver = null;
    /**
     * ホームボタンイベント検知.
     */
    private HomeEventReceiver mHomeEventReceiver;
    /**
     * Wakelock.
     */
    private android.os.PowerManager.WakeLock mWakelock;
    /**
     * 排他制御用.
     */
    private Object mLock = new Object();
    /**
     * プロジェクタ照射状態.
     */
    private boolean isProjected = false;
    /**
     * 歌実行結果取得用.
     */
    private SongResultReceiver mSongResultReceiver;
    /**
     * SDLイベント取得用.
     */
    private SDLEventReceiver mSDLEventReceiver;
    /**
     * 歌実行結果通知用Action定義.
     */
    public static final String ACTION_SDL_EVENT = "jp.co.anotherbrain.sdlwithrobohon.action.SDL_EVENT";
    public enum SDLEvents {
        RAIN,
        KINKON,
        START,
        SEATBELT,
        REVERSE,
        FUEL,
        KIRI_77777,
        KIRI_10000, // TODO: 10000km キリ番実装
        KIRI_12345, // TODO: 12345km キリ番実装
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.activity_main);

        //設定情報初期化
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        //タイトルバー設定.
        setupTitleBar();

        //ホームボタンの検知登録.
        mHomeEventReceiver = new HomeEventReceiver();
        IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeEventReceiver, filterHome);

        //VoiceUI再起動の検知登録.
        mVoiceUIStartReceiver = new VoiceUIStartReceiver();
        IntentFilter filter = new IntentFilter(VoiceUIManager.ACTION_VOICEUI_SERVICE_STARTED);
        registerReceiver(mVoiceUIStartReceiver, filter);

        //歌連携起動結果取得用レシーバー登録(歌利用時のみ).
        mSongResultReceiver = new SongResultReceiver();
        IntentFilter filterSong = new IntentFilter(ACTION_RESULT_SONG);
        registerReceiver(mSongResultReceiver, filterSong);

        //SDL起動
        if(BuildConfig.TRANSPORT.equals("MULTI") || BuildConfig.TRANSPORT.equals("MULTI_HB")) {
            SdlReceiver.queryForConnectedService(this);
        }else if(BuildConfig.TRANSPORT.equals("TCP")) {
            Intent proxyIntent = new Intent(this, SdlService.class);
            startService(proxyIntent);
        }

        //SDLイベントレシーバー登録
        mSDLEventReceiver = new SDLEventReceiver();
        IntentFilter filterSDLEvent = new IntentFilter(ACTION_SDL_EVENT);
        registerReceiver(mSDLEventReceiver, filterSDLEvent);

        //WakeLockの取得
        acquireWakeLock();

    }


    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume()");

        //VoiceUIManagerのインスタンス取得.
        if (mVoiceUIManager == null) {
            mVoiceUIManager = VoiceUIManager.getService(getApplicationContext());
        }
        //MainActivityVoiceUIListener生成.
        if (mMainActivityVoiceUIListener == null) {
            mMainActivityVoiceUIListener = new MainActivityVoiceUIListener(this);
        }
        //VoiceUIListenerの登録.
        VoiceUIManagerUtil.registerVoiceUIListener(mVoiceUIManager, mMainActivityVoiceUIListener);

        //Scene有効化.
        VoiceUIManagerUtil.enableScene(mVoiceUIManager, ScenarioDefinitions.SCENE_COMMON);
        VoiceUIManagerUtil.enableScene(mVoiceUIManager, ScenarioDefinitions.SCENE01);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");

        //バックに回ったら発話を中止する.
        VoiceUIManagerUtil.stopSpeech();

        //VoiceUIListenerの解除.
        VoiceUIManagerUtil.unregisterVoiceUIListener(mVoiceUIManager, mMainActivityVoiceUIListener);

        //Scene無効化.
        VoiceUIManagerUtil.disableScene(mVoiceUIManager, ScenarioDefinitions.SCENE_COMMON);
        VoiceUIManagerUtil.disableScene(mVoiceUIManager, ScenarioDefinitions.SCENE01);

        //デフォルトの言語設定に戻す
        Locale locale = Locale.getDefault();
        VoiceUIManagerUtil.setAsr(mVoiceUIManager, locale);
        VoiceUIManagerUtil.setTts(mVoiceUIManager, locale);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");

        //ホームボタンの検知破棄.
        this.unregisterReceiver(mHomeEventReceiver);

        //VoiceUI再起動の検知破棄.
        this.unregisterReceiver(mVoiceUIStartReceiver);

        //WakeLockの破棄
        releaseWakeLock();

        //歌結果用レシーバーの破棄(歌利用時のみ).
        this.unregisterReceiver(mSongResultReceiver);

        //SDLイベントレシーバーの破棄(歌利用時のみ).
        this.unregisterReceiver(mSDLEventReceiver);


        //SDL Service の停止
        if (BuildConfig.TRANSPORT.equals("MULTI") || BuildConfig.TRANSPORT.equals("MULTI_HB")) {
            // TODO: 実機で切断のテスト
            // SdlReceiver.queryForConnectedService(this);
        }else if(BuildConfig.TRANSPORT.equals("TCP")) {
            Log.v(TAG, "stopService");
            Intent proxyIntent = new Intent(this, SdlService.class);
            stopService(proxyIntent);
        }

        //インスタンスのごみ掃除.
        mVoiceUIManager = null;
        mMainActivityVoiceUIListener = null;
        mSongResultReceiver = null;
        mSDLEventReceiver = null;
    }

    /**
     * VoiceUIListenerクラスからのコールバックを実装する.
     */
    @Override
    public void onExecCommand(String command, List<VoiceUIVariable> variables) {
        Log.v(TAG, "onExecCommand() : " + command);
        switch (command) {
            case ScenarioDefinitions.FUNC_END_APP:
                finish();
                break;
            default:
                break;
        }
    }

    /**
     * タイトルバーを設定する.
     */
    private void setupTitleBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);
        ImageButton btn = (ImageButton)findViewById(R.id.actionSettings);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivityForResult(intent, 999);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            //SecondActivityから戻ってきた場合
            case (999):
                if (resultCode == 999) {
                    finish();
                }
                break;
            default:
                break;
        }
    }

    /**
     * WakeLockを取得する.
     */
    private void acquireWakeLock() {
        Log.v(TAG, "acquireWakeLock()");
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        synchronized (mLock) {
            if (mWakelock == null || !mWakelock.isHeld()) {
                mWakelock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE, MainActivity.class.getName());
                mWakelock.acquire();
            }
        }
    }

    /**
     * WakeLockを開放する.
     */
    private void releaseWakeLock() {
        Log.v(TAG, "releaseWakeLock()");
        synchronized (mLock) {
            if (mWakelock != null && mWakelock.isHeld()) {
                mWakelock.release();
                mWakelock = null;
            }
        }
    }

    /**
     * 歌開始用のIntentを設定する.
     */
    private Intent getIntentForSong(int id) {
        Intent intent = new Intent(SongUtil.ACTION_REQUEST_SONG);
        intent.putExtra(SongUtil.EXTRA_REPLYTO_ACTION, ACTION_RESULT_SONG);
        intent.putExtra(SongUtil.EXTRA_REPLYTO_PKG, getPackageName());
        intent.putExtra(SongUtil.EXTRA_TYPE, SongUtil.EXTRA_TYPE_ASSIGN);
        intent.putExtra(SongUtil.EXTRA_REQUEST_ID, id);
        return intent;
    }

    /**
     * ホームボタンの押下イベントを受け取るためのBroadcastレシーバークラス.<br>
     * <p/>
     * アプリは必ずホームボタンで終了する..
     */
    private class HomeEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "Receive Home button pressed");
            // ホームボタン押下でアプリ終了する.
            finish();
        }
    }

    /**
     * 音声UI再起動イベントを受け取るためのBroadcastレシーバークラス.<br>
     * <p/>
     * 稀に音声UIのServiceが再起動することがあり、その場合アプリはVoiceUIの再取得とListenerの再登録をする.
     */
    private class VoiceUIStartReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (VoiceUIManager.ACTION_VOICEUI_SERVICE_STARTED.equals(action)) {
                Log.d(TAG, "VoiceUIStartReceiver#onReceive():VOICEUI_SERVICE_STARTED");
                //VoiceUIManagerのインスタンス取得.
                mVoiceUIManager = VoiceUIManager.getService(getApplicationContext());
                if (mMainActivityVoiceUIListener == null) {
                    mMainActivityVoiceUIListener = new MainActivityVoiceUIListener(getApplicationContext());
                }
                //VoiceUIListenerの登録.
                VoiceUIManagerUtil.registerVoiceUIListener(mVoiceUIManager, mMainActivityVoiceUIListener);
            }
        }
    }

    /**
     * 歌実行結果を受け取るためのBroadcastレシーバー クラス.<br>
     * <p/>
     */
    private class SongResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int result = intent.getIntExtra(SongUtil.EXTRA_RESULT_CODE, SongUtil.RESULT_CANCELED);
            Log.d(TAG, "SongResultReceiver#onReceive() : " + result);
            if (result == SongUtil.RESULT_OK) {
                // 正常に完了した場合.
                int id = intent.getIntExtra(SongUtil.EXTRA_RESULT_ID, -1);
                String name = intent.getStringExtra(SongUtil.EXTRA_RESULT_NAME);
            }
        }
    }

    /**
     * SDLからイベントを受け取るためのBroadcastレシーバー クラス.<br>
     * <p/>
     */
    protected class SDLEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent){
            SDLEvents e = (SDLEvents) intent.getSerializableExtra("message");
            Log.d(TAG, "SDLEventReceiver#onReceive() : " + e.toString());

            switch (e) {
                case KINKON:
                    if (mVoiceUIManager != null) {
                        appendLog("キンコン");
                        VoiceUIVariableListHelper helper = new VoiceUIVariableListHelper().addAccost(ScenarioDefinitions.ACC_KINKON);
                        VoiceUIManagerUtil.updateAppInfo(mVoiceUIManager, helper.getVariableList(), true);
                    }
                    break;
                case FUEL:
                    if (mVoiceUIManager != null) {
                        appendLog("ガソリン");
                        VoiceUIVariableListHelper helper = new VoiceUIVariableListHelper().addAccost(ScenarioDefinitions.ACC_FUEL);
                        VoiceUIManagerUtil.updateAppInfo(mVoiceUIManager, helper.getVariableList(), true);
                    }
                    break;
                case START:
                    if (mVoiceUIManager != null) {
                        appendLog("出発進行");
                        VoiceUIVariableListHelper helper = new VoiceUIVariableListHelper().addAccost(ScenarioDefinitions.ACC_START);
                        VoiceUIManagerUtil.updateAppInfo(mVoiceUIManager, helper.getVariableList(), true);
                    }
                    break;
                case SEATBELT:
                    if (mVoiceUIManager != null) {
                        appendLog("シートベルト");
                        VoiceUIVariableListHelper helper = new VoiceUIVariableListHelper().addAccost(ScenarioDefinitions.ACC_SEATBELT);
                        VoiceUIManagerUtil.updateAppInfo(mVoiceUIManager, helper.getVariableList(), true);
                    }
                    break;
                case REVERSE:
                    if (mVoiceUIManager != null) {
                        appendLog("バック");
                        VoiceUIVariableListHelper helper = new VoiceUIVariableListHelper().addAccost(ScenarioDefinitions.ACC_REVERSE);
                        VoiceUIManagerUtil.updateAppInfo(mVoiceUIManager, helper.getVariableList(), true);
                    }
                    break;
                case KIRI_77777:
                    if (mVoiceUIManager != null) {
                        appendLog("77777");
                        VoiceUIVariableListHelper helper = new VoiceUIVariableListHelper().addAccost(ScenarioDefinitions.ACC_KIRI);
                        VoiceUIManagerUtil.updateAppInfo(mVoiceUIManager, helper.getVariableList(), true);
                    }
                    break;
                case RAIN:
                    appendLog("雨");
                    sendBroadcast(getIntentForSong(9));
                    break;
            }
        }
    }

    private void appendLog(String message) {
        final DateFormat df = new SimpleDateFormat("HH:mm:ss");
        final Date date = new Date(System.currentTimeMillis());

        TextView tv = (TextView)findViewById(R.id.logView);
        tv.append( df.format(date) + " " + message);
        tv.append("\n");

        ScrollView sv = (ScrollView)findViewById(R.id.scrollView);
        sv.fullScroll(View.FOCUS_DOWN);
    }

}
