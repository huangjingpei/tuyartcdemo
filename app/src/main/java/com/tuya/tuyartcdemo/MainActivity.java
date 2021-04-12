package com.tuya.tuyartcdemo;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.tuya.rtc.TuyaRTCEngine;
import com.tuya.rtc.TuyaRTCEngineFactory;
import com.tuya.rtc.TuyaRTCEngineHandler;
import com.tuya.rtc.TuyaVideoRenderer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;

import org.webrtc.EglBase;
import org.webrtc.RendererCommon;

public class MainActivity extends AppCompatActivity implements View.OnClickListener ,
        CompoundButton.OnCheckedChangeListener{
    private static final String TAG = "MainActivity";

    private static final String[] MANDATORY_PERMISSIONS = {"android.permission.CAMERA",
            "android.permission.RECORD_AUDIO", "android.permission.WRITE_EXTERNAL_STORAGE"};

    private Button sdkInitBtn;
    private Button startPreivewBtn;
    private Button subscribeTopicBtn;
    private Button switchCameraBtn;

    private CheckBox muteLocalAudiocheckbox;
    private CheckBox muteLocalVideocheckbox;


    private LayoutInflater inflater;

    private EglBase eglBase;
    private TuyaRTCEngine rtcEngine;

    private RelativeLayout localSurfaceLayout;

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

        localSurfaceLayout = (RelativeLayout)findViewById(R.id.rtc_local_surfaceview);
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.sdkInit) {
            requestDangerousPermissions(MANDATORY_PERMISSIONS, 1);
            eglBase = EglBase.create(null /* sharedContext */, EglBase.CONFIG_PLAIN);
            rtcEngine = TuyaRTCEngineFactory.getInstance().createEngine(this, eglBase, new TuyaRTCEngineHandler(){
                @Override
                public void onUserJoin(String s) {

                }
            });
            Log.d(TAG, "onClick() called with: v = [" + v + "]");
        } else if (v.getId() == R.id.startPreview) {
            TuyaVideoRenderer viewRenderer = new TuyaVideoRenderer(this);
            viewRenderer.setKeepScreenOn(true);
            viewRenderer.setZOrderMediaOverlay(true);
            viewRenderer.setZOrderOnTop(false);
            viewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
            //viewRenderer.init(eglBase.getEglBaseContext(), null);
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(1280, 720);
            viewRenderer.setLayoutParams(layoutParams);
            localSurfaceLayout.setVisibility(View.VISIBLE);
            localSurfaceLayout.addView(viewRenderer);
            rtcEngine.startPreview("", 1280, 720, 30, viewRenderer, null);

        } else if (v.getId() == R.id.subscribeTopic) {

        } else if (v.getId() == R.id.switchCamera) {

        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

    }


    /**
     * 请求权限
     */
    public void requestDangerousPermissions(String[] permissions, int requestCode) {
        if (checkDangerousPermissions(permissions)){
            handlePermissionResult(requestCode, true);
            return;
        }
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }

    /**
     * 检查是否已被授权危险权限
     * @param permissions
     * @return
     */
    private boolean checkDangerousPermissions(String[] permissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return false;
            }
        }
        return true;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean granted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                granted = false;
            }
        }
        boolean finish = handlePermissionResult(requestCode, granted);
        if (!finish){
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * 处理请求危险权限的结果
     * @return
     */
    private boolean handlePermissionResult(int requestCode, boolean granted) {
        //Notice 这里要自定义处理权限申请。
        return false;
    }

}