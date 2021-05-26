package com.tuya.tuyartcdemo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.tuya.rtc.TuyaRTCEngine;
import com.tuya.rtc.TuyaRTCEngineHandler;
import com.tuya.rtc.TuyaVideoRenderer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.webrtc.EglBase;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener ,
        CompoundButton.OnCheckedChangeListener, TuyaRTCEngineHandler {
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

    private RelativeLayout localSurfaceLayout;
    private LinearLayout remoteFeedContainer;
    private LinearLayout feedContainer;

    private ConcurrentHashMap<String, ViewGroup> feedWindows = new ConcurrentHashMap<>();



    private boolean isSdkInit;
    private boolean isStartPreview;
    private                  boolean         isSubscrbingTopic;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();


    //For test arguments.
    private String clientId = "jct4wjjgtppxth9vpjeq";
    private String secret  = "ns45erx7y9ut8trygwwnfu549eghrmqg";
    private String deviceId = "6ceeb5b251fb016f2aamtp";
    private String authCode = "6d29408bfe0c1b472b48d872b52fbd14";

    private SurfaceViewRenderer localView;

    private TuyaRTCEngine tuyaRTCEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initRes();

    }

    private void initRes() {
        inflater = LayoutInflater.from(this);

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
        remoteFeedContainer = (LinearLayout)findViewById(R.id.rtc_remote_feeds_container);
        feedContainer = remoteFeedContainer;


        SurfaceViewRenderer viewRenderer = new SurfaceViewRenderer(this);
        viewRenderer.setKeepScreenOn(true);
        viewRenderer.setZOrderMediaOverlay(true);
        viewRenderer.setZOrderOnTop(false);
        viewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        //viewRenderer.init(eglBase.getEglBaseContext(), null);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(160, 90);
        viewRenderer.setLayoutParams(layoutParams);
        localView = viewRenderer;

        eglBase = EglBase.create();

    }


    public int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    private void addFeedWindow(String streamName, SurfaceViewRenderer surfaceView) {
        if (!feedWindows.containsKey(streamName)) {
            RelativeLayout feedWindow = (RelativeLayout) inflater.inflate(R.layout.feed_window, feedContainer, false);
            ViewGroup.LayoutParams params = new RelativeLayout.LayoutParams(dip2px(this,320),dip2px(this,180));
            surfaceView.setLayoutParams(params);
            //surfaceView.setBackgroundColor(Color.GRAY);
            feedWindow.addView(surfaceView,0);
            feedContainer.addView(feedWindow);
            feedWindows.put(streamName,feedWindow);
            TextView streamNameTV = feedWindow.findViewById(R.id.streamName_tv);
            streamNameTV.setText(streamName);


            CheckBox muteAudioChkBox = feedWindow.findViewById(R.id.muteAudio_chkbox);
            muteAudioChkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                }
            });

            CheckBox muteVideoChkBox = feedWindow.findViewById(R.id.muteVideo_chkbox);
            muteVideoChkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                }
            });
        } else {
            Log.e(TAG, "addFeedWindow fail repeat streamName = " + streamName);
        }
    }

    private void deleteFeedWindow(String streamName) {
        if (feedWindows.containsKey(streamName)) {
            ViewGroup viewGroup = feedWindows.get(streamName);
            viewGroup.removeAllViewsInLayout();
            feedWindows.remove(streamName);
            feedContainer.removeView(viewGroup);

        } else {
            Log.e(TAG,"deleteFeedWindow fail no uid = "+streamName);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.sdkInit) {
            if (!isSdkInit) {
                requestDangerousPermissions(MANDATORY_PERMISSIONS, 1);
                eglBase = EglBase.create(null /* sharedContext */, EglBase.CONFIG_PLAIN);
                tuyaRTCEngine = new TuyaRTCEngine();
                executor.execute(() ->{
                    tuyaRTCEngine.initRtcEngine(this, eglBase,
                            clientId, secret, authCode, this);
                });

                sdkInitBtn.setText("引擎销毁");
                isSdkInit = true;
            } else {
                if (isStartPreview) {
                    startPreivewBtn.setText("停止预览");
                    isStartPreview = false;
                }
                if (isSubscrbingTopic) {
                    deleteFeedWindow(deviceId);
                    subscribeTopicBtn.setText("订阅内容");
                    isSubscrbingTopic = false;
                }
                sdkInitBtn.setText("引擎初始化");
                isSdkInit = false;
            }
        } else if (v.getId() == R.id.startPreview) {
            if (!isStartPreview) {
                SurfaceViewRenderer viewRenderer = new SurfaceViewRenderer(this);
                viewRenderer.setKeepScreenOn(true);
                viewRenderer.setZOrderMediaOverlay(true);
                viewRenderer.setZOrderOnTop(false);
                viewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
                //viewRenderer.init(eglBase.getEglBaseContext(), null);
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(1280, 720);
                viewRenderer.setLayoutParams(layoutParams);
                localView = viewRenderer;
                localSurfaceLayout.setVisibility(View.VISIBLE);
                localSurfaceLayout.addView(localView);
                startPreivewBtn.setText("停止预览");
                isStartPreview = true;

            } else {
                localSurfaceLayout.removeView(localView);
                localSurfaceLayout.setVisibility(View.GONE);
                startPreivewBtn.setText("开始预览");
                isStartPreview = false;
            }
        } else if (v.getId() == R.id.subscribeTopic) {
            if (tuyaRTCEngine  != null && (!isSubscrbingTopic)) {
                TuyaVideoRenderer viewRenderer = new TuyaVideoRenderer(this);
                viewRenderer.setKeepScreenOn(true);
                viewRenderer.setZOrderMediaOverlay(true);
                viewRenderer.setZOrderOnTop(false);
                viewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
                addFeedWindow(deviceId, viewRenderer);
                tuyaRTCEngine.createTuyaCamera(deviceId);
                localView.init(eglBase.getEglBaseContext(), null);
                viewRenderer.init(eglBase.getEglBaseContext(), null);
                //localSurfaceLayout.setVisibility(View.VISIBLE);
                //localSurfaceLayout.addView(localView);
                executor.execute(()->{
                    tuyaRTCEngine.startPreview(deviceId, localView, viewRenderer);
                });
                subscribeTopicBtn.setText("退订内容");
                isSubscrbingTopic = true;
            } else {
                deleteFeedWindow(deviceId);
                tuyaRTCEngine.stopPreview(deviceId);
                tuyaRTCEngine.destoryTuyaCamera(deviceId);
                subscribeTopicBtn.setText("订阅内容");
                isSubscrbingTopic = false;
            }

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