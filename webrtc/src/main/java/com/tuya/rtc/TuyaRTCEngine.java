package com.tuya.rtc;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.webrtc.CapturerObserver;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Loggable;
import org.webrtc.Logging;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TuyaRTCEngine implements TuyaMQTTClient.SignalingEvents {
    private static final String TAG                                   = "TuyaRTCEngine";
    private static final String VIDEO_CODEC_H264_HIGH                 = "H264 High";
    private static final String VIDEO_FLEXFEC_FIELDTRIAL              =
            "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/";
    private static final String VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL = "WebRTC-IntelVP8/Enabled/";
    private static final String DISABLE_WEBRTC_AGC_FIELDTRIAL         =
            "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/";

    private boolean                 isError;
    private String                  cid;
    private String                  secretId;
    private String                  authCode;
    private Context                 appContext;
    private TuyaRTCEngineHandler    engineHandler;
    private TuyaRTCEngineParameters tuyaRTCEngineParameters;
    private EglBase                 eglBase;
    private VideoCapturer           nullVideoCapturer;
    private RecordSink            recordSink;
    private PeerConnectionFactory factory;
    private volatile boolean isRecording;
    private static final ExecutorService       executor = Executors.newSingleThreadExecutor();

    private ConcurrentHashMap<String, TuyaRTCCamera> cameraHashMap = new ConcurrentHashMap<>();

    public boolean initRtcEngine(Context appContext,
                                 EglBase eglBase,
                                 String cid,
                                 String secretId,
                                 String authCode,
                                 TuyaRTCEngineHandler engineHandler) {

        this.appContext = appContext;
        this.eglBase = eglBase;
        this.cid = cid;
        this.secretId = secretId;
        this.authCode = authCode;
        this.engineHandler = engineHandler;
        tuyaRTCEngineParameters = new TuyaRTCEngineParameters(
                true,
                false,
                false,
                1280,
                720,
                30,
                1024 * 1024,
                null,
                true,
                true,
                0,
                null,
                true,
                false,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true);
        nullVideoCapturer = new NullVideoCapture();
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        createPeerConnectionFactory(options);
        boolean result = TuyaMQTTClient.getInstance().prepare(cid, secretId, authCode, this);
        return result;
    }


    public void destoryRtcEngine() {
        Log.d(TAG, "Closing peer connection factory.");
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        eglBase.release();
        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
    }

    public void createTuyaCamera(String did) {
        TuyaRTCCamera camera = new TuyaRTCCamera(appContext, factory, eglBase, did, tuyaRTCEngineParameters);
        addTuyaCamera(did, camera);
        return;
    }

    public void destoryTuyaCamera(String did) {
        removeTuyaCamera(did);
        return;
    }

    public int startPreview(String did, SurfaceViewRenderer localSink, SurfaceViewRenderer sink) {
        TuyaRTCCamera camera = getTuyaCameraByDid(did);
        if (camera != null) {
            camera.startPreview(nullVideoCapturer, localSink, sink);
            return 0;
        }
        Log.e(TAG, "startPreview failed.");
        return -1;
    }

    public int stopPreview(String did) {
        TuyaRTCCamera camera = getTuyaCameraByDid(did);
        if (camera != null) {
            camera.stopPreview();
            return 0;
        }
        Log.e(TAG, "stopPreview failed.");
        return -1;
    }

    public int setRemoteRenderer(String did, VideoSink sink) {
        TuyaRTCCamera camera = getTuyaCameraByDid(did);
        if (camera != null) {
            return 0;

        }
        return -1;
    }


    public int muteAudio(String did, boolean mute) {
        TuyaRTCCamera camera = getTuyaCameraByDid(did);
        if (camera != null) {
            return camera.muteAudio(mute);

        }
        return -1;
    }


    public int muteVideo(String did, boolean mute) {
        TuyaRTCCamera camera = getTuyaCameraByDid(did);
        if (camera != null) {
            return camera.muteVideo(mute);

        }
        return -1;
    }

    public boolean getRemoteAudioMute(String did) {
        TuyaRTCCamera camera = getTuyaCameraByDid(did);
        if (camera != null) {
            return camera.getRemoteAudioMute();

        }
        return false;
    }

    public boolean getRemoteVideoMute(String did) {
        TuyaRTCCamera camera = getTuyaCameraByDid(did);
        if (camera != null) {
            return camera.getRemoteVideoMute();
        }
        return false;
    }

    private static class RecordSink implements VideoSink {
        @Override
        public void onFrame(VideoFrame frame) {
            Logging.d(TAG, "RecordSink onFrame");
        }
    };


    public boolean startRecord(String did) {
        isRecording = true;
        recordSink = new RecordSink();
        TuyaRTCCamera camera = getTuyaCameraByDid(did);
        if (camera != null) {
            return camera.startRecord(recordSink);
        }
        return false;
    }

    public boolean stopRecord(String did) {
        isRecording = false;
        TuyaRTCCamera camera = getTuyaCameraByDid(did);
        if (camera != null) {
            return camera.stopRecord(recordSink);
        }
        recordSink = null;
        return false;
    }


    private void createPeerConnectionFactory(PeerConnectionFactory.Options options) {
        if (factory != null) {
            throw new IllegalStateException("PeerConnectionFactory has already been constructed");
        }
        createPeerConnectionFactoryInternal(options);
    }


    private void createPeerConnectionFactoryInternal(PeerConnectionFactory.Options options) {
        isError = false;
        final String fieldTrials = getFieldTrials(tuyaRTCEngineParameters);

        Log.d(TAG, "Initialize WebRTC. Field trials: " + fieldTrials);
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .setFieldTrials(fieldTrials)
                        .setEnableInternalTracer(true)
                        .setInjectableLogger(new TuyaRTCLoggable(), Logging.Severity.LS_INFO)
                        .createInitializationOptions());


        if (tuyaRTCEngineParameters.isTracing()) {
            PeerConnectionFactory.startInternalTracingCapture(
                    Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
                            + "webrtc-trace.txt");
        }

        final AudioDeviceModule adm = createJavaAudioDevice();

        // Create peer connection factory.
        if (options != null) {
            Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
        }
        final boolean enableH264HighProfile =
                VIDEO_CODEC_H264_HIGH.equals(tuyaRTCEngineParameters.getVideoCodec());
        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        if (tuyaRTCEngineParameters.isVideoCodecHwAcceleration()) {
            encoderFactory = new DefaultVideoEncoderFactory(
                    eglBase.getEglBaseContext(), true /* enableIntelVp8Encoder */, enableH264HighProfile);
            decoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());
        } else {
            encoderFactory = new SoftwareVideoEncoderFactory();
            decoderFactory = new SoftwareVideoDecoderFactory();
        }

        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
        Log.d(TAG, "Peer connection factory created.");
        adm.release();
    }

    AudioDeviceModule createJavaAudioDevice() {
        // Enable/disable OpenSL ES playback.
        if (!tuyaRTCEngineParameters.isUseOpenSLES()) {
            Log.w(TAG, "External OpenSLES ADM not implemented yet.");
            // TODO(magjed): Add support for external OpenSLES ADM.
        }

        // Set audio record error callbacks.
        JavaAudioDeviceModule.AudioRecordErrorCallback audioRecordErrorCallback = new JavaAudioDeviceModule.AudioRecordErrorCallback() {
            @Override
            public void onWebRtcAudioRecordInitError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordInitError: " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioRecordStartError(
                    JavaAudioDeviceModule.AudioRecordStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordStartError: " + errorCode + ". " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioRecordError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordError: " + errorMessage);
                reportError(errorMessage);
            }
        };

        JavaAudioDeviceModule.AudioTrackErrorCallback audioTrackErrorCallback = new JavaAudioDeviceModule.AudioTrackErrorCallback() {
            @Override
            public void onWebRtcAudioTrackInitError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackInitError: " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackStartError(
                    JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackStartError: " + errorCode + ". " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackError: " + errorMessage);
                reportError(errorMessage);
            }
        };

        // Set audio record state callbacks.
        JavaAudioDeviceModule.AudioRecordStateCallback audioRecordStateCallback = new JavaAudioDeviceModule.AudioRecordStateCallback() {
            @Override
            public void onWebRtcAudioRecordStart() {
                Log.i(TAG, "Audio recording starts");
            }

            @Override
            public void onWebRtcAudioRecordStop() {
                Log.i(TAG, "Audio recording stops");
            }
        };

        // Set audio track state callbacks.
        JavaAudioDeviceModule.AudioTrackStateCallback audioTrackStateCallback = new JavaAudioDeviceModule.AudioTrackStateCallback() {
            @Override
            public void onWebRtcAudioTrackStart() {
                Log.i(TAG, "Audio playout starts");
            }

            @Override
            public void onWebRtcAudioTrackStop() {
                Log.i(TAG, "Audio playout stops");
            }
        };

        JavaAudioDeviceModule.AudioTrackSamplesReadyCallback audioTrackSamplesReadyCallback = new JavaAudioDeviceModule.AudioTrackSamplesReadyCallback() {
            @Override
            public void onWebRtcAudioTrackSamplesReady(JavaAudioDeviceModule.AudioSamples audioSamples) {
                if (isRecording) {
                    Logging.d(TAG, "recrod audio frame.");
                }
            }
        };


        return JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(!tuyaRTCEngineParameters.isDisableBuiltInAEC())
                .setUseHardwareNoiseSuppressor(!tuyaRTCEngineParameters.isDisableBuiltInNS())
                .setAudioRecordErrorCallback(audioRecordErrorCallback)
                .setAudioTrackErrorCallback(audioTrackErrorCallback)
                .setAudioRecordStateCallback(audioRecordStateCallback)
                .setAudioTrackStateCallback(audioTrackStateCallback)
                .setAudioTrackSamplesReadyCallback(audioTrackSamplesReadyCallback)
                .createAudioDeviceModule();
    }

    private void reportError(final String errorMessage) {
        executor.execute(() -> {
            if (!isError) {
                Log.e(TAG, "TuyaRTCEnegine error: " + errorMessage);
                isError = true;
            }
        });
    }


    private TuyaRTCCamera getTuyaCameraByDid(String did) {
        if ((did != null) && cameraHashMap.containsKey(did)) {
            return cameraHashMap.get(did);
        }
        return null;
    }

    private TuyaRTCCamera getTuyaCameraBySessionId(String sessionId) {
        if ((sessionId != null)) {
            for(Map.Entry<String, TuyaRTCCamera> entry: cameraHashMap.entrySet()) {
                if (entry.getValue().getSessionId().compareTo(sessionId) == 0) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    public void addTuyaCamera(String did, TuyaRTCCamera camera) {
        if ((did != null) && (camera != null)) {
            if (cameraHashMap.contains(did)) {
                removeTuyaCamera(did);
            }
            cameraHashMap.put(did, camera);
        }
    }


    public void removeTuyaCamera(String did) {
        if ((did != null) && cameraHashMap.containsKey(did)) {
            cameraHashMap.remove(did);
        }
    }


    private String getFieldTrials(TuyaRTCEngineParameters peerConnectionParameters) {
        String fieldTrials = "";
        if (peerConnectionParameters.isVideoFlexfecEnabled()) {
            fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL;
            Log.d(TAG, "Enable FlexFEC field trial.");
        }
        fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;
        if (peerConnectionParameters.isDisableWebRtcAGCAndHPF()) {
            fieldTrials += DISABLE_WEBRTC_AGC_FIELDTRIAL;
            Log.d(TAG, "Disable WebRTC AGC field trial.");
        }
        return fieldTrials;

    }


    @Override
    public void onConnectedToRoom(TuyaRTCClient.SignalingParameters params) {

    }

    @Override
    public void onRemoteDescription(String did, String sessionId, SessionDescription sdp) {
        TuyaRTCCamera camera = getTuyaCameraByDid(did);
        if (camera != null) {
            camera.setRemoteDescription(sdp);
        }
    }

    @Override
    public void onRemoteIceCandidate(String did, String sessionId, IceCandidate candidate) {
        TuyaRTCCamera camera = getTuyaCameraByDid(did);
        if (camera != null) {
            camera.addRemoteIceCandidate(candidate);
        }
    }

    @Override
    public void onRemoteIceCandidatesRemoved(String did, String sessionId, IceCandidate[] candidates) {

    }

    @Override
    public void onChannelClose(String did, String sessionId) {

    }

    @Override
    public void onChannelError(String did, String sessionId, String description) {

    }

    /**
     * Peer connection parameters.
     */
    class TuyaRTCEngineParameters {
        private boolean videoCallEnabled;
        private boolean loopback;
        private boolean tracing;
        private int     videoWidth;
        private int     videoHeight;
        private int     videoFps;
        private int     videoMaxBitrate;
        private String  videoCodec;
        private boolean videoCodecHwAcceleration;
        private boolean videoFlexfecEnabled;
        private int     audioStartBitrate;
        private String  audioCodec;
        private boolean noAudioProcessing;
        private boolean aecDump;
        private boolean saveInputAudioToFile;
        private boolean useOpenSLES;
        private boolean disableBuiltInAEC;
        private boolean disableBuiltInAGC;
        private boolean disableBuiltInNS;
        private boolean disableWebRtcAGCAndHPF;
        private boolean enableRtcEventLog;
        private boolean disableDataChannel;

        public TuyaRTCEngineParameters(boolean videoCallEnabled, boolean loopback, boolean tracing,
                                       int videoWidth, int videoHeight, int videoFps, int videoMaxBitrate, String videoCodec,
                                       boolean videoCodecHwAcceleration, boolean videoFlexfecEnabled, int audioStartBitrate,
                                       String audioCodec, boolean noAudioProcessing, boolean aecDump, boolean saveInputAudioToFile,
                                       boolean useOpenSLES, boolean disableBuiltInAEC, boolean disableBuiltInAGC,
                                       boolean disableBuiltInNS, boolean disableWebRtcAGCAndHPF, boolean enableRtcEventLog,
                                       boolean disableDataChannel) {
            this.videoCallEnabled = videoCallEnabled;
            this.loopback = loopback;
            this.tracing = tracing;
            this.videoWidth = videoWidth;
            this.videoHeight = videoHeight;
            this.videoFps = videoFps;
            this.videoMaxBitrate = videoMaxBitrate;
            this.videoCodec = videoCodec;
            this.videoFlexfecEnabled = videoFlexfecEnabled;
            this.videoCodecHwAcceleration = videoCodecHwAcceleration;
            this.audioStartBitrate = audioStartBitrate;
            this.audioCodec = audioCodec;
            this.noAudioProcessing = noAudioProcessing;
            this.aecDump = aecDump;
            this.saveInputAudioToFile = saveInputAudioToFile;
            this.useOpenSLES = useOpenSLES;
            this.disableBuiltInAEC = disableBuiltInAEC;
            this.disableBuiltInAGC = disableBuiltInAGC;
            this.disableBuiltInNS = disableBuiltInNS;
            this.disableWebRtcAGCAndHPF = disableWebRtcAGCAndHPF;
            this.enableRtcEventLog = enableRtcEventLog;
            this.disableDataChannel = disableDataChannel;
        }

        public boolean isVideoCallEnabled() {
            return videoCallEnabled;
        }

        public void setVideoCallEnabled(boolean videoCallEnabled) {
            this.videoCallEnabled = videoCallEnabled;
        }

        public boolean isLoopback() {
            return loopback;
        }

        public void setLoopback(boolean loopback) {
            this.loopback = loopback;
        }

        public int getVideoWidth() {
            return videoWidth;
        }

        public void setVideoWidth(int videoWidth) {
            this.videoWidth = videoWidth;
        }

        public int getVideoHeight() {
            return videoHeight;
        }

        public void setVideoHeight(int videoHeight) {
            this.videoHeight = videoHeight;
        }

        public int getVideoFps() {
            return videoFps;
        }

        public void setVideoFps(int videoFps) {
            this.videoFps = videoFps;
        }

        public int getVideoMaxBitrate() {
            return videoMaxBitrate;
        }

        public void setVideoMaxBitrate(int videoMaxBitrate) {
            this.videoMaxBitrate = videoMaxBitrate;
        }

        public String getAudioCodec() {
            return audioCodec;
        }

        public void setAudioCodec(String audioCodec) {
            this.audioCodec = audioCodec;
        }

        public boolean isNoAudioProcessing() {
            return noAudioProcessing;
        }

        public void setNoAudioProcessing(boolean noAudioProcessing) {
            this.noAudioProcessing = noAudioProcessing;
        }

        public boolean isAecDump() {
            return aecDump;
        }

        public void setAecDump(boolean aecDump) {
            this.aecDump = aecDump;
        }

        public boolean isDisableBuiltInAGC() {
            return disableBuiltInAGC;
        }

        public void setDisableBuiltInAGC(boolean disableBuiltInAGC) {
            this.disableBuiltInAGC = disableBuiltInAGC;
        }

        public boolean isEnableRtcEventLog() {
            return enableRtcEventLog;
        }

        public void setEnableRtcEventLog(boolean enableRtcEventLog) {
            this.enableRtcEventLog = enableRtcEventLog;
        }

        public boolean isDisableDataChannel() {
            return disableDataChannel;
        }

        public void setDisableDataChannel(boolean disableDataChannel) {
            this.disableDataChannel = disableDataChannel;
        }

        public int getAudioStartBitrate() {
            return audioStartBitrate;
        }

        public void setAudioStartBitrate(int audioStartBitrate) {
            this.audioStartBitrate = audioStartBitrate;
        }

        public boolean isUseOpenSLES() {
            return useOpenSLES;
        }

        public void setUseOpenSLES(boolean useOpenSLES) {
            this.useOpenSLES = useOpenSLES;
        }

        public boolean isDisableBuiltInAEC() {
            return disableBuiltInAEC;
        }

        public void setDisableBuiltInAEC(boolean disableBuiltInAEC) {
            this.disableBuiltInAEC = disableBuiltInAEC;
        }

        public boolean isDisableBuiltInNS() {
            return disableBuiltInNS;
        }

        public void setDisableBuiltInNS(boolean disableBuiltInNS) {
            this.disableBuiltInNS = disableBuiltInNS;
        }

        public boolean isVideoFlexfecEnabled() {
            return videoFlexfecEnabled;
        }

        public void setVideoFlexfecEnabled(boolean videoFlexfecEnabled) {
            this.videoFlexfecEnabled = videoFlexfecEnabled;
        }

        public boolean isDisableWebRtcAGCAndHPF() {
            return disableWebRtcAGCAndHPF;
        }

        public void setDisableWebRtcAGCAndHPF(boolean disableWebRtcAGCAndHPF) {
            this.disableWebRtcAGCAndHPF = disableWebRtcAGCAndHPF;
        }

        public boolean isTracing() {
            return tracing;
        }

        public void setTracing(boolean tracing) {
            this.tracing = tracing;
        }

        public boolean isSaveInputAudioToFile() {
            return saveInputAudioToFile;
        }

        public void setSaveInputAudioToFile(boolean saveInputAudioToFile) {
            this.saveInputAudioToFile = saveInputAudioToFile;
        }

        public String getVideoCodec() {
            return videoCodec;
        }

        public void setVideoCodec(String videoCodec) {
            this.videoCodec = videoCodec;
        }

        public boolean isVideoCodecHwAcceleration() {
            return videoCodecHwAcceleration;
        }

        public void setVideoCodecHwAcceleration(boolean videoCodecHwAcceleration) {
            this.videoCodecHwAcceleration = videoCodecHwAcceleration;
        }
    }

    static class NullVideoCapture implements VideoCapturer {

        @Override
        public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver capturerObserver) {
            return ;
        }

        @Override
        public void startCapture(int width, int height, int framerate) {
            return ;
        }

        @Override
        public void stopCapture() throws InterruptedException {
            return ;
        }

        @Override
        public void changeCaptureFormat(int width, int height, int framerate) {
            return ;
        }

        @Override
        public void dispose() {
            return ;
        }

        @Override
        public boolean isScreencast() {
            return false;
        }
    }

}
