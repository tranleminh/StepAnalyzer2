package com.functionality.stepanalyzer2;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.TextView;

import java.sql.Time;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity{

    /****************Attributes and global variables declared here*****************/

    /***Application's main UI features***/
    private EditText ID;
    private TextView Steps;
    private TextView Status;
    private TextView Delta;
    private Button BtnDaily;
    //private Button Btn6min;
    private Chronometer CLK;


    /***WorkManager variables that ensure automatic background task
     private WorkManager mWorkManager;
     private OneTimeWorkRequest workRequest;*/

    /***Button manipulation variables***/
    private boolean dailyTracker = false;
    private String id = "NOT_INITIALIZED";
    private String btnText = "Start Daily Tracker";
    private int steps = 0;

    /***A Shared Preferences to store information on user ID and button status to be restore on every launch***/
    private SharedPreferences mPreferences;
    private String sharedPrefFile = "ema.functionality.stepanalyzer";

    /***Broadcast Receiver which receive step numbers counted by DailyStepTracker.java***/
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            steps = intent.getIntExtra("Nb_Steps", steps);
            Steps.setText("Number of steps :" + steps);
        }
    };

    /**************/
    private Intent intent;


//    private boolean isMyServiceRunning(Class<?> serviceClass) {
//        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
//        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
//            if (serviceClass.getName().equals(service.service.getClassName())) {
//                Log.i ("isMyServiceRunning?", true+"");
//                return true;
//            }
//        }
//        Log.i ("isMyServiceRunning?", false+"");
//        return false;
//    }


    /**
     * Main method revoked on application launch
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /******/
        intent = new Intent(this, DailyStepService.class);

        /***The Broadcast Receiver is launched here***/
        registerReceiver(broadcastReceiver, new IntentFilter(DailyStepService.ACTION_CUSTOM_BROADCAST));

        /***Shared Preferences instantiated, user id and button status are updated here***/
        mPreferences = getSharedPreferences(sharedPrefFile, MODE_PRIVATE);
        id = mPreferences.getString("ID", id);
        dailyTracker = mPreferences.getBoolean("DailyTracker", dailyTracker);
        btnText = mPreferences.getString("BtnText", btnText);

        /***UI instantiation***/
        ID = findViewById(R.id.id);
        Steps = findViewById(R.id.nbstep);
        Status = findViewById(R.id.status);
        Delta = findViewById(R.id.delta);
        BtnDaily = findViewById(R.id.btn_daily);
        //Btn6min = findViewById(R.id.btn_6min);
        Steps.setText("Number of steps :" + steps);

        if (!id.equals("NOT_INITIALIZED"))
            ID.setText(id);

        /***A clock used for 6 minutes test, which is currently not yet implemented***/
        CLK = findViewById(R.id.clk);
        CLK.setVisibility(View.INVISIBLE);


        /***A text changed listener with afterTextChanged() method implemented in order to automatically save ID right after the ID field is modified***/
        ID.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                id = ID.getText().toString();
                SharedPreferences.Editor preferencesEditor = mPreferences.edit();
                preferencesEditor.putString("ID",id);
                preferencesEditor.apply();
            }
        });

        /***Daily Tracker Button implemented here***/
        BtnDaily.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //if (!isMyServiceRunning(DailyStepService.class)) {
                    //if (!dailyTracker) {
                    //mWorkManager.enqueue(workRequest);
                    //startService(intent);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }
                    dailyTracker = true;
                    btnText = "Daily Tracker Started";
                    SharedPreferences.Editor preferencesEditor = mPreferences.edit();
                    preferencesEditor.putString("BtnText", btnText);
                    preferencesEditor.putBoolean("DailyTracker", dailyTracker);
                    preferencesEditor.apply();
                    BtnDaily.setText(btnText);
                    //finish();
                //}
                //}
                /*else {
                    btnText = "Daily Tracker Already Started !!!";
                    SharedPreferences.Editor preferencesEditor = mPreferences.edit();
                    preferencesEditor.putString("BtnText",btnText);
                    preferencesEditor.apply();
                    BtnDaily.setText(btnText);
                }*/
            }
        });
    }

    @Override
    protected void onDestroy() {
        stopService(intent);
        super.onDestroy();
    }
}