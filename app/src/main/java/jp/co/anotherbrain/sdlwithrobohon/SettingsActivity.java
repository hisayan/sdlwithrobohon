package jp.co.anotherbrain.sdlwithrobohon;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new PrefsFragment()).commit();
    }


    public static class PrefsFragment extends PreferenceFragment {

        private Context context;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            context = getActivity().getApplicationContext();

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            Preference button = findPreference(getString(R.string.pref_key_restart_button));
            button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference pref) {
                    //アプリ再起動
                    restart(context);
                    return true;
                }
            });

        }

        void restart(Context cnt){
            // intent 設定で自分自身のクラスを設定
            Intent mainActivity = new Intent(cnt, MainActivity.class);

            // PendingIntent , ID=0
            PendingIntent pendingIntent = PendingIntent.getActivity(cnt,
                    0, mainActivity, PendingIntent.FLAG_CANCEL_CURRENT);

            // AlarmManager のインスタンス生成
            AlarmManager alarmManager = (AlarmManager)cnt.getSystemService(
                    Context.ALARM_SERVICE);

            // １回のアラームを現在の時間からperiod（0.5秒）後に実行させる
            if(alarmManager != null){
                long trigger = System.currentTimeMillis() + 500;
                alarmManager.setExact(AlarmManager.RTC, trigger, pendingIntent);
            }

            getActivity().setResult(999);
            getActivity().finish();
        }

    }


}