package com.functionality.stepanalyzer2;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DailyStepService extends Service implements SensorEventListener {
    /****************Attributes and global variables declared here*****************/

    public static final String CHANNEL_ID = "ForegroundServiceChannel";

    /***SensorManager and Sensor declaration***/
    private SensorManager sensorManager;
    private Sensor sensor;
    private StorageReference mStorageRef;

    /***Variables for step counting here***/
    private int nbStep = 0;
    private String lines = "";
    private long moment = -1;
    private long delta = 0;
    private String ID;
    //private boolean isWorking;

    /***Shared Preferences called to get user ID from MainActivity***/
    private SharedPreferences prefs;
    private String sharedPrefFile = "ema.functionality.stepanalyzer";

    /***File manipulation variables here***/
    private static final String FILE_NAME = "DailyStep";
    private static final String EXTENSION = ".csv";
    private static final String FIRST_LINE = "Timestamp; Delta\n";
    private static final SimpleDateFormat df = new SimpleDateFormat("dd-MM-YYYY_hh-mm-ss");
    private static final SimpleDateFormat df2 = new SimpleDateFormat("hh:mm:ss");
    private static final SimpleDateFormat dfYear = new SimpleDateFormat("YYYY");
    private static final SimpleDateFormat dfMonth = new SimpleDateFormat("MMM");
    private static final SimpleDateFormat dfDay = new SimpleDateFormat("d");
    private static final SimpleDateFormat dfHour = new SimpleDateFormat("hh-mm-ss");
    private static final long TIME_LIMIT_MS = 5000;

    /***Variables used for broadcast purpose, with an Intent and broadcast tag***/
    Intent intentBR;
    public static final String ACTION_CUSTOM_BROADCAST = BuildConfig.APPLICATION_ID + ".ACTION_CUSTOM_BROADCAST";

    /**
     * Create a .csv file and automatically upload it to the Firebase server
     * @param username
     * @param year
     * @param month
     * @param day
     * @param hour
     * @param lines
     */
    public void createTextFile(String username, String year, String month, String day, String hour, String lines) {
        /***Initialize file name here***/
        String name = FILE_NAME + "_" + hour + EXTENSION;

        /***File manipulation here***/
        FileOutputStream fos = null;
        try {
            fos = openFileOutput(name, MODE_PRIVATE);
            fos.write(FIRST_LINE.getBytes());
            fos.write(lines.getBytes());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    /***After completed editing file, close it and upload it to the Firebase server***/
                    fos.close();
                    Uri file = Uri.fromFile(new File(getFilesDir(), name));
                    StorageReference fileRef = mStorageRef.child(username + "/" + year + "/" + month + "/" + day + "/" + name);
                    Toast.makeText(this, "URI : " + file, Toast.LENGTH_LONG).show();

                    /***The putFile() method used for file uploading***/
                    fileRef.putFile(file)
                            .continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                                @Override
                                public Task<Uri> then(Task<UploadTask.TaskSnapshot> task) throws Exception {
                                    if (!task.isSuccessful()) {
                                        throw task.getException();
                                    }
                                    return mStorageRef.getDownloadUrl();
                                }
                            }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                        @Override
                        public void onComplete(Task<Uri> task) {
                            if (task.isSuccessful()) {
                                Toast.makeText(getApplicationContext(), "Upload complete!" , Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getApplicationContext(), "Upload failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                    /***After uploaded the file, delete it***/
                    getApplicationContext().deleteFile(name);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Send number of steps to MainActivity for display purpose
     */
    private void broadcastValue() {
        intentBR.putExtra("Nb_Steps", nbStep);
        sendBroadcast(intentBR);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        Toast.makeText(this, "Service Created", Toast.LENGTH_LONG).show();
        Log.d("service", "Service Created");
        /***Instantiate the broadcast intent***/
        intentBR = new Intent(ACTION_CUSTOM_BROADCAST);

        /***Instantiate the Firebase Cloud Storage***/
        mStorageRef = FirebaseStorage.getInstance().getReference();

        /***Instantiate the Shared Preference and get the user ID from it***/
        prefs = getSharedPreferences(sharedPrefFile, MODE_PRIVATE);
        ID = prefs.getString("ID", ID);
    }

    @Override
    public void onDestroy() {
        Log.d("service", "destroy");
        super.onDestroy();
        Intent broadcastIntent = new Intent(this, AppCloseReceiver.class);
        sendBroadcast(broadcastIntent);
        this.sensorManager.unregisterListener(this);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Toast.makeText(this, "Service Started", Toast.LENGTH_LONG).show();

        /***Register the step sensor listener so it starts to count steps, featuring SENSOR_DELAY_NORMAL as sampling frequency and maximal report latency/delay set to 0***/
        this.sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0);

        String input = intent.getStringExtra("inputExtra");
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText(input)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        /**Return START_STICKY in order to keep the service even when app is killed**/
        return START_STICKY;
    }

    /**
     * Overridden method that listen for step sensor's change so as to update the counter and put lines into text file content
     * @param sensorEvent
     */
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            /***If a change event is detected with step sensor, increase counter by one***/
            nbStep++;
            /***Save the event timestamp (initially in nanoseconds) and convert it to milliseconds, then calculate duration between 2 detected steps***/
            long timestamp = sensorEvent.timestamp/1000000;
            if (moment != -1) {
                delta = (timestamp - moment);
            }
            moment = timestamp;
        }
        /***Send the steps number with broadcastValue()***/
        broadcastValue();
        /***The lines are updated only when the time limit does not exceed a time threshold***/
        if (delta < TIME_LIMIT_MS) {
            lines += df2.format(Calendar.getInstance().getTime()) + "; " + delta + "\n ";
        }
        /***If user stops walking for TIME_LIMIT_MS, all counters are reset and a text file is created and uploaded to the server***/
        else {
            Date now = Calendar.getInstance().getTime();
            createTextFile(ID, dfYear.format(now), dfMonth.format(now), dfDay.format(now), dfHour.format(now) , lines);
            lines = "";
            nbStep = 0;
            moment = -1;
            delta = 0;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
