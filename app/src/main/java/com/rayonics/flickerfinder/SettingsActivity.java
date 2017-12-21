package com.rayonics.flickerfinder;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;


public class SettingsActivity extends AppCompatActivity {
    private static final String MY_PREFS_NAME = "MyPrefsFile";
    private SharedPreferences.Editor editor;
    private SharedPreferences sharedPrefs;
    private int progress1 = 2; //seekbar progress - threshold seekbar
    private float threshold = 2;
    private String lightsensor; //light sensor info
    private int lmindelay = 200000; //min light sensor timerHandlerDelay default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Get saved preferences settings
        sharedPrefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
        getAppPreferences(); //retrieve saved preferences

        setThreshText(threshold); //update threshold textview

        //light sensor information to display
        TextView lsField;
        lsField = findViewById(R.id.lightsensor);
        String ls = getResources().getString(R.string.lightsensor);
        if (lmindelay < 100) {
            lmindelay = 100; // to make division below reasonable
        }
        int lmaxrate = 1000000/lmindelay ; //maximum sensor update rate

        ls+= getResources().getString(R.string.ratemsg1);
        ls+=lmaxrate;
        ls+= getResources().getString(R.string.ratemsg2);
        ls += lightsensor;
        lsField.setText(ls); //show current progress2

        // Editor for writing preferences
        editor = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();

        //Setup seekBar1 - threshold seekbar
        int maxValue = 13;
        SeekBar seekBar1;  // Declare seekbar
        seekBar1 = findViewById(R.id.seekBar1);
        seekBar1.setMax(maxValue);
        seekBar1.setProgress(progress1); //current progress1

        seekBar1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar1) {
                threshold = setThreshold(progress1); //set light flicker threshold
                setThreshText(threshold); //update threshold textview
                //Toast.makeText(MainActivity.this, "Threshold is :" + threshold, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar1) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar1, int progress, boolean fromUser) {
                progress1 = progress;  //progress1 range is 0 to 10
                threshold = setThreshold(progress); //set threshold based upon progress1
                setThreshText(threshold);
                //save progress1 into preferences
                editor.putInt("progress1", progress1); //save progress1
                editor.putFloat("threshold", threshold);//
                editor.putString("text", "OK"); //so it's not null
                editor.apply();//save it
            }
        });

        // add back navigation button to toolbar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });

    }//End onCreate



    //Used to get app saved preferences
    public void getAppPreferences(){
        String validText = sharedPrefs.getString("text", null);
        if (validText != null) {
            //Toast.makeText(MainActivity.this, "Loading Sensitivity", Toast.LENGTH_SHORT).show();
            progress1 = sharedPrefs.getInt("progress1", 2); //seekbar progress
            threshold = sharedPrefs.getFloat("threshold", 2); //get threshold
            lightsensor = sharedPrefs.getString("lightsensor","finding light sensor info...");
            lmindelay = sharedPrefs.getInt("lmindelay", 200000);//light sensor min timerHandlerDelay
        }
    }

    public float setThreshold(int sens) {
        float thresh;
        if (sens >13) { //should never happen
            sens = 13;
        }
        if (sens <0) { //should never happen
            sens = 0;
        }
        //convert progress1 to percent flicker threshold
        float[] mlist = {0.5f, 1, 2, 5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 99};
        thresh=mlist[sens]; //
        return thresh;
    }

    public void setThreshText(float threshold) {
        TextView thField;
        thField = findViewById(R.id.threshold);
        String th = getResources().getString(R.string.threshold);
        th += String.format(Locale.US,"%.1f",threshold);
        th += "%";
        thField.setText(th); //show current progress1
    }



}
