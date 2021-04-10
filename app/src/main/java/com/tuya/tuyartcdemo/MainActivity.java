package com.tuya.tuyartcdemo;

import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.tuya.rtc.TuyaRTCEngine;
import com.tuya.rtc.TuyaRTCEngineFactory;
import com.tuya.rtc.TuyaRTCEngineHandler;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.LayoutInflater;
import android.view.View;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import org.webrtc.EglBase;

public class MainActivity extends AppCompatActivity implements View.OnClickListener ,
        CompoundButton.OnCheckedChangeListener{
    private Button sdkInitBtn;
    private Button startPreivewBtn;
    private Button subscribeTopicBtn;
    private Button switchCameraBtn;

    private CheckBox muteLocalAudiocheckbox;
    private CheckBox muteLocalVideocheckbox;


    private LayoutInflater inflater;

    private EglBase eglBase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //System.loadLibrary("c++_shared");
        initRes();

    }

    private void initRes() {
        sdkInitBtn = (Button) findViewById(R.id.sdkInit);
        sdkInitBtn.setOnClickListener(this);
        startPreivewBtn = (Button) findViewById(R.id.startPreview);
        startPreivewBtn.setOnClickListener(this);
        subscribeTopicBtn = (Button) findViewById(R.id.subscribeTopic);
        subscribeTopicBtn.setOnClickListener(this);
        switchCameraBtn = (Button) findViewById(R.id.switchCamera);
        switchCameraBtn.setOnClickListener(this);

        muteLocalAudiocheckbox = (CheckBox) findViewById(R.id.muteLocalAudio);
        muteLocalVideocheckbox = (CheckBox) findViewById(R.id.muteLocalVideo);

        muteLocalAudiocheckbox.setOnCheckedChangeListener(this);
        muteLocalVideocheckbox.setOnCheckedChangeListener(this);
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.sdkInit) {
            eglBase = EglBase.create(null /* sharedContext */, EglBase.CONFIG_PLAIN);
            TuyaRTCEngineFactory.getInstance().createEngine(this, eglBase, new TuyaRTCEngineHandler(){
                @Override
                public void onUserJoin(String s) {

                }
            });
        } else if (v.getId() == R.id.startPreview) {

        } else if (v.getId() == R.id.subscribeTopic) {

        } else if (v.getId() == R.id.switchCamera) {

        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

    }
}