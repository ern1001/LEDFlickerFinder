package com.rayonics.flickerfinder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import static android.os.SystemClock.uptimeMillis;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static String TAG = MainActivity.class.getCanonicalName();
    private Sensor light;
    private ToneGenerator toneGen1;
    private TextView lightflickerView;
    private TextView lightView;
    private int eventNum; //flicker count
    private float maxFlicker; //max flicker %
    private long tEvent; //time of last event
    private TextView minutesView; //minutesView timer
    private int timerMinutes; //for timer display
    private float threshold; //flicker threshold
    private boolean mute; //mute the beep sound
    private boolean startup = true; //so we ignore the startup false flicker
    private static final String MY_PREFS_NAME = "MyPrefsFile";
    private SharedPreferences sharedPrefs;
    private CheckBox muteBox;
    private SensorManager sensorManager;
    Handler timerHandler; // timer handler
    private int timerHandlerDelay; //6 seconds timer update
    private int timerCount; // 6 second count
    Runnable timerRunnable; // timer runnable
    private boolean isForeground;// true if mainactivity is in foreground
    VolumeKeyReceiver keyReceiver; // To detect volume control changes - for unmuting
    MyArray myArray;// Class member to manipulate light sensor array values
    private boolean log=false;// disable logging for production release

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //force screen to stay on
        //Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar); //for top toolbar
        setSupportActionBar(toolbar);

        //Get saved preferences settings
        sharedPrefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
        boolean ap = getAppPreferences(); //retrieve saved preferences
        if (!ap) { //see if we need to initialize prefs
            initPrefs(); //initialize preferences
        }
        //create myArray member for prev light sensor values
        final int arraylen=5; //length of array to be created
        myArray = new MyArray(arraylen);
        //initialize variables
        initStats();

        // Setup text views
        lightView = findViewById(R.id.light);
        lightflickerView = findViewById(R.id.lightflicker);
        minutesView = findViewById(R.id.minutes);

        //Mute checkbox listener
        muteBox = findViewById(R.id.mute);
        muteBox.setChecked(mute);
        muteBox.setOnClickListener(checkboxClickListener);

        //reset button listener
        findViewById(R.id.reset).setOnClickListener(reset_OnClickListener);

        //Light sensor
        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        try { //try/catch in case getDefaultSensor is null
            //sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        } catch (Exception e) {
            System.out.println("Error " + e.getMessage());
            updateLightDisplay (getResources().getString(R.string.lsnotfound)); //show not found message on display
            Toast.makeText(MainActivity.this, "Default Light Sensor Not Found", Toast.LENGTH_LONG).show();
        }
        int lmindelay; //light sensor min delay
        if (light != null) {
            updateLightDisplay (getResources().getString(R.string.lsfound) ); //show light sensor found message on display
            String lname = light.getName(); //light sensor name
            lmindelay = light.getMinDelay();  //get the light sensor minimum timerHandlerDelay in microseconds
            float lpower = light.getPower();
            String lvendor = light.getVendor();
            int lversion = light.getVersion();
            float lresolution = light.getResolution();
            String si = lname + " Version=" + lversion + " Vendor=";
            si += lvendor + " MinDelay=" + lmindelay + " Resolution=";
            si += lresolution + " Power=" + lpower;
            final int lminlimit =10000; //10000 usec = 10 msec
            if (lmindelay < lminlimit) {  //from a realistic perspective
                lmindelay = lminlimit;    //100 updates/sec is the maximum
            }
            // Put info in preferences
            SharedPreferences.Editor editor;
            editor = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();
            editor.putString("lightsensor", si); //save lightsensor info
            editor.putInt("lmindelay", lmindelay);
            editor.putString("text", "OK"); //so it's not null
            editor.apply();//save it into preferences
        }

        //For beep sound
        toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        setVolumeControlStream(AudioManager.STREAM_MUSIC); // so volume control will default to media while app is running

    }// End OnCreate

    //Used to reset app variables & statistics
    public void initStats() {
        eventNum = 0;
        maxFlicker = 0;
        startup = true;
        timerMinutes = 0;
        timerCount = 0;
        timerHandlerDelay = 5991; // 6 seconds minus 9 (correction factor for sw overhead)
        myArray.init(0);
    }

    //Mute checkbox
    View.OnClickListener checkboxClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mute = ((CheckBox) view).isChecked();
            //Toast.makeText(MainActivity.this, "Mute="+mute, Toast.LENGTH_SHORT).show();
            // Editor for writing preferences
            SharedPreferences.Editor editor;
            editor = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();
            editor.putBoolean("mute", mute); //save mute setting
            editor.putString("text", "OK"); //so it's not null
            editor.apply();//save it into preferences
        }
    };

    //On click listener for reset button
    View.OnClickListener reset_OnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            // Toast.makeText(MainActivity.this, "Reset clicked", Toast.LENGTH_SHORT).show();
            //updateLightDisplay(0);
            stopTimerHandler(); //stop previous timer handler
            initStats(); //reset stats and startup flag
            updateTimerDisplay(0, 0);
            startTimerHandler(); //start timer handler
        }
    };

    //Triggered by any light sensor changes
    @Override
    public void onSensorChanged(SensorEvent event) {  //
        //Log.i(TAG, event.sensor.getName()+" value: "+event.values[0]);
        if (event.sensor != light) {
            return; //ignore if not from light sensor
        }
        float currValue;
        float diff;
        float maxPrev;
        long tNow = uptimeMillis(); //get current sys time
        currValue = event.values[0]; //current light sensor reading
        if (startup){
            tEvent=tNow; //update time of this event
            myArray.init(currValue); //init prev values array
        }
        //Use the greater of the past prev values for the difference calculation
        maxPrev=myArray.max();
        diff=maxPrev-currValue;
        // negative flickers produce positive differences. Only recognize negative flicker events
        if (diff < 0) {
            diff=0;
        }
        if (maxPrev == 0) {
            maxPrev = 1; //so can never divide by 0
        }
        //Update array for next time
        myArray.put(currValue);
        //
        float percentFlicker;
        percentFlicker = (100 * diff) / maxPrev;
        if (percentFlicker > 100) {
            percentFlicker = 100;
        }
        if ((percentFlicker > maxFlicker) && !startup) { //keep track of largest flicker percentage seen
            maxFlicker = percentFlicker;
        }
        //compare light change vs threshold , ignore events within approx 200ms of each other
        final int tmin = 190; //ignore events less than 190 ms apart
        if ((percentFlicker >= threshold) && !startup && (tNow-tEvent)>tmin ) {
            eventNum++;  //increment flicker count
            tEvent=tNow; //update for next time
            //init prev values for next time
            myArray.init(currValue);
            // beep if not muted
            if (!mute) {
                if (log) {Log.i(TAG, " flicker event ");}
                toneGen1.startTone(ToneGenerator.TONE_PROP_ACK, 100); //100ms beep tone
            }
        }
        updateLightDisplay(currValue);// call method to update the light display view
    }

    //Update light sensor display text views
    public void updateLightDisplay(float cv) {
        String li = getResources().getString(R.string.light_intensity) + cv;
        li += getResources().getString(R.string.lux);
        lightView.setText(li);
        String lff = getResources().getString(R.string.max_flicker);
        lff += String.format(Locale.US, "%.1f", maxFlicker);
        lff += "%";
        lff += getResources().getString(R.string.flicker_count) + eventNum;
        lightflickerView.setText(lff);
        //
        startup = false; // for next time through
    }
    public void updateLightDisplay(String s) { //String input just updates the text portion of the textview
        lightView.setText(s);
    }

    public void updateTimerDisplay(int m, int c) {
        String mi = getResources().getString(R.string.minutes) + m + "." + c;
        minutesView.setText(mi);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {  //required but unlikely to trigger
        if (log) {Log.i(TAG, "Accuracy Changed for Sensor " + sensor.getName() + " is: " + accuracy);}
    }

    //Get app saved preferences
    public boolean getAppPreferences() {
        String validText = sharedPrefs.getString("text", null);
        if (validText != null) {
            //Toast.makeText(MainActivity.this, "Loading Sensitivity", Toast.LENGTH_SHORT).show();
            threshold = sharedPrefs.getFloat("threshold", 2); //get threshold
            mute = sharedPrefs.getBoolean("mute", false);
            return true; //preferences are initialized
        } else {
            return false; // preferences not initialized yet
        }
    }

    //This is only callled if shared prefs has not been previously initialized
    public void initPrefs() {
        SharedPreferences.Editor editor;
        editor = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();
        editor.putFloat("threshold", 2); //flicker threshold percentage
        editor.putString("lightsensor", getResources().getString(R.string.lsdefault)); //save lightsensor info
        editor.putInt("lmindelay", 200000); //light sensor minimum delay
        editor.putBoolean("mute", false); //save mute setting
        editor.putInt("progress1", 2); //seekbar progress
        editor.putString("text", "OK"); //so it's not null
        editor.apply();//save it into preferences
    }

    //listens for any volume control changes
    public class VolumeKeyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isForeground) {
                mute = false;// unmute automatically if volume control is changed
                muteBox.setChecked(false);
                SharedPreferences.Editor editor;
                editor = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();
                editor.putBoolean("mute", mute); //save mute setting
                editor.putString("text", "OK"); //so it's not null
                editor.apply();//save it into preferences
            }
        }
    }

    // Starts the timer handler
    public void startTimerHandler() {
        if (log) {Log.i(TAG, "starting TimerHandler");}
        timerHandler = new Handler();
        timerHandler.postDelayed(new Runnable() {
            public void run() {
                    try {
                        timerCount++;
                        if (timerCount == 10) {
                            timerMinutes++; //update
                            timerCount = 0;
                        }
                        updateTimerDisplay(timerMinutes, timerCount); //update the display
                    } catch (Exception e) {
                        if (log) {Log.e(TAG, "catch-runnable error");}
                    }
                    timerRunnable = this;
                    timerHandler.postDelayed(timerRunnable, timerHandlerDelay); //start delay again
                }
        }, timerHandlerDelay);
    }

    // stops the timer handler
    public void stopTimerHandler() {
        if (log) {Log.i(TAG, "stopping TimerHandler");}
        timerHandler.removeCallbacks(timerRunnable); //stop timer handler callbacks
        timerHandler.removeMessages(0);// remove pending timer handler messages
    }

    //Class for manipulating Array of most recent sensor values
    public class MyArray {
        private float [] mArray; //array holds prev light sensor values
        //Constructor
        //MyArray(int l, float [] a) {
        MyArray(int l) {
            //MyArray() {
            mArray = new float[l]; //create array of length l
        }
        //Method: initialize array
        private void init(float val) {
            for (int i=0; i<mArray.length; i++){
                mArray[i]=val;
            }
        }
        //Method: find the max value in array
        private float max() {
            float maxvalue=0;
            for (float v : mArray){ //java foreach
                if (maxvalue < v) {
                    maxvalue = v;
                }
            }
            return maxvalue;
        }
        //Method: add to the beginning of the array & shift others right
        private void put(float val) {
            for (int i=mArray.length-1; i>0; i--){
                mArray[i]=mArray[i-1]; //shift array items right
            }
            mArray[0]=val; //put new value at start of array
        }
    }// end MyArray

    //----------------------------- Android Lifecycle ---------------------------------------
    @Override
    protected void onStart(){
        super.onStart();
        if (log) {Log.i(TAG, "onStart");}
        isForeground=true;
        //Register light sensor with sampling period
        sensorManager.registerListener(this,light,10000); //up to 100 updates per second for phones that support it
        initStats();
        getAppPreferences();
        // Timer
        updateTimerDisplay(timerMinutes, timerCount); //initialize the timer display
        startTimerHandler(); //start the timer handler
        //Below is used to determine if volume is changed by user
        keyReceiver = new VolumeKeyReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(keyReceiver, intentFilter);
    }

    @Override
    protected void onStop(){
        super.onStop();
        if (log) {Log.i(TAG, "onStop");}
        isForeground=false;
        sensorManager.unregisterListener(this,light);
        stopTimerHandler(); //stop the timer handler
        unregisterReceiver(keyReceiver);// unregister the volume control receiver
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (log) {Log.i(TAG, "onResume");}
        isForeground=true;
    }

    @Override
    protected void onPause(){
        super.onPause();
        if (log) {Log.i(TAG, "onPause");}
        isForeground=false;
    }

    //---------------- Options Menu Navigation -----------------------------------------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {  //Settings screen
            Intent intent = new Intent(this, SettingsActivity.class);
            this.startActivity(intent);
        }
        if (id == R.id.action_help) {  //Help Screen
            Intent intent = new Intent(this, HelpActivity.class);
            this.startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }


}//End MainActivity


