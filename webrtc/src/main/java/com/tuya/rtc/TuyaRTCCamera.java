package com.tuya.rtc;


import android.content.Context;
import android.provider.MediaStore;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TuyaRTCCamera implements TuyaRTCClient.PeerConnectionEvents {
    private static final String TAG                  = "TuyaRTCCamera";
    private static final int    STAT_CALLBACK_PERIOD = 1000;

    private Context        appContext;
    private String         did;
    private TuyaRTCClient  tuyaRtcClient;
    private TuyaMQTTClient tuyaMQTTClient;
    private long           callStartedTimeMs;
    private boolean        connected;
    private String         sessionId;
    private String         webrtcConfig;
    private VideoCapturer  videoCapturer;


    public TuyaRTCCamera(Context context,
                         PeerConnectionFactory factory,
                         EglBase eglbase,
                         String did,
                         TuyaRTCEngine.TuyaRTCEngineParameters parameters) {
        this.appContext = context;
        this.tuyaRtcClient = new TuyaRTCClient(context, factory, eglbase, parameters, this);
        this.tuyaMQTTClient = TuyaMQTTClient.getInstance();
        this.sessionId = getRandomString(32);
        this.did = did;
    }

    public String getRandomString(int length) {
        String str = "zxcvbnmlkjhgfdsaqwertyuiopQWERTYUIOPASDFGHJKLZXCVBNM1234567890";
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; ++i) {
            int number = random.nextInt(62);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }

    public int startPreview(VideoCapturer capturer, VideoSink localSink, VideoSink sink) {
        callStartedTimeMs = System.currentTimeMillis();
        if (TuyaMQTTClient.getInstance().getWebRTCConfig(did) != true) {
            Log.e(TAG, "Get webrtc configuration failed. startPreview failed.");
            return -1;
        }
        webrtcConfig = TuyaMQTTClient.getInstance().getCurentWebRTCConfig();
        JSONObject ices = null;
        JSONObject webrtcConfiguration;
        List<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>();
        try {
            webrtcConfiguration = new JSONObject(webrtcConfig);
            ices = webrtcConfiguration.getJSONObject("p2p_config");

            JSONArray icesArray = ices.getJSONArray("ices");
            int len = icesArray.length();
            for (int i = 0; i < icesArray.length(); i++) {
                JSONObject ice = (JSONObject) icesArray.get(i);
                String url = ice.getString("urls");
                if (url.contains("stun")) {
                    PeerConnection.IceServer iceServer = new PeerConnection.IceServer(url);
                    iceServers.add(iceServer);
                } else if (url.contains("turn")) {
                    String username = ice.getString("username");
                    String credential = ice.getString("credential");
                    PeerConnection.IceServer iceServer = new PeerConnection.IceServer(url, username, credential);
                    iceServers.add(iceServer);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }


        TuyaRTCClient.SignalingParameters signalParameters = new TuyaRTCClient.SignalingParameters(iceServers,
                true,
                null,
                null,
                null,
                null,
                null);

        tuyaRtcClient.createPeerConnection(localSink, sink, capturer, signalParameters);
        tuyaRtcClient.createOffer();
        return 0;
    }

    public int stopPreview() {
        tuyaRtcClient.close();
        tuyaRtcClient = null;
        return 0;
    }

    public int muteAudio(boolean mute) {
        if (tuyaRtcClient != null) {
            tuyaRtcClient.setAudioEnabled(!mute);
            return 0;
        }
        return -1;
    }

    public int muteVideo(boolean mute) {
        if (tuyaRtcClient != null) {
            tuyaRtcClient.setVideoEnabled(!mute);
            return 0;
        }
        return -1;
    }


    public boolean getRemoteAudioMute() {
        if (tuyaRtcClient != null) {
            return !tuyaRtcClient.getAudioEnable();
        }
        return false;

    }

    public boolean getRemoteVideoMute() {
        if (tuyaRtcClient != null) {
            return !tuyaRtcClient.getVideoEnable();
        }
        return false;
    }

    public void dispose() {

    }

    @Override
    public void onLocalDescription(final SessionDescription desc) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        if ((tuyaMQTTClient != null) && (tuyaMQTTClient.isReady())) {
            Log.e(TAG, "Sending " + desc.type + ", delay=" + delta + "ms");
            if (desc.type == SessionDescription.Type.OFFER) {
                tuyaMQTTClient.sendOfferSdp(sessionId, did, desc, webrtcConfig);
            } else {
                Log.e(TAG, "Send answer sdp is not implemented. ");
                //tuyaMQTTClient.sendAnswerSdp(desc);
            }
        } else {
            Log.e(TAG, "Mqtt is not initialized, send sdp failed.");
        }
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {

        if ((tuyaMQTTClient != null) && (tuyaMQTTClient.isReady())) {
            tuyaMQTTClient.sendLocalIceCandidate(sessionId, did, webrtcConfig, candidate);
        }

    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates) {

        if ((tuyaMQTTClient != null) && (tuyaMQTTClient.isReady())) {
            Log.e(TAG, "onIceCandidatesRemoved is not implemented");
        }

    }

    @Override
    public void onIceConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Log.e(TAG, "ICE connected, delay=" + delta + "ms");
    }

    @Override
    public void onIceDisconnected() {
        Log.e(TAG, "ICE disconnected");
    }

    @Override
    public void onConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;

        Log.e(TAG, "DTLS connected, delay=" + delta + "ms");
        connected = true;
        tuyaRtcClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD);

    }

    @Override
    public void onDisconnected() {

        Log.e(TAG, "DTLS disconnected");
        connected = false;
        tuyaRtcClient.close();
        tuyaRtcClient = null;
    }

    @Override
    public void onPeerConnectionClosed() {

    }

    @Override
    public void onPeerConnectionStatsReady(final StatsReport[] reports) {
    }

    @Override
    public void onPeerConnectionError(final String description) {
        Log.e(TAG, "PeerConnection error: " + description);
    }


    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    public void setRemoteDescription(final SessionDescription sdp) {
        if (tuyaRtcClient != null) {
            tuyaRtcClient.setRemoteDescription(sdp);
        }
    }

    public void addRemoteIceCandidate(final IceCandidate candidate) {
        if (tuyaRtcClient != null) {
            tuyaRtcClient.addRemoteIceCandidate(candidate);
        }
    }
    public String getSessionId() {
        return  sessionId;
    }

}
