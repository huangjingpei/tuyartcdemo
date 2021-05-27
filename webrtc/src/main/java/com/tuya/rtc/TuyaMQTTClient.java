package com.tuya.rtc;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TuyaMQTTClient implements MqttCallbackExtended , IMqttActionListener {
    private static final String TAG = "TuyaMQTTClient";

    private          String             clientId;
    private          String             secretId;
    private          String             mqttUrl;
    private          String             mqttClientId;
    private          String             authCodeId;
    private          String             mqttUserName;
    private          String             mqttPassword;
    private          String             uid;
    private          String             accessToken;
    private          String             refreshToken;
    private          String             webrtcConfig;
    private          MqttConnectOptions mqttConnectOptions;
    private          MqttAsyncClient    mqttAsyncClient;
    private          SignalingEvents    signalingEvents;
    private volatile boolean            isReady;

    private boolean mqttConnected;
    private final Lock      waitLock    = new ReentrantLock();
    private final Condition waitFinished = waitLock.newCondition();

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        //waitLock.lock();
        mqttConnected = true;
        subscribeToTopic();
        waitFinished.signal();
        //waitLock.unlock();
    }

    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        waitLock.lock();
        mqttConnected = false;
        waitFinished.signal();
        waitLock.unlock();
    }


    interface SignalingEvents {
        /**
         * Callback fired once the room's signaling parameters
         * SignalingParameters are extracted.
         */
        void onConnectedToRoom(final TuyaRTCClient.SignalingParameters params);

        /**
         * Callback fired once remote SDP is received.
         */
        void onRemoteDescription(String did, String sessionId, final SessionDescription sdp);

        /**
         * Callback fired once remote Ice candidate is received.
         */
        void onRemoteIceCandidate(String did, String sessionId, final IceCandidate candidate);

        /**
         * Callback fired once remote Ice candidate removals are received.
         */
        void onRemoteIceCandidatesRemoved(String did, String sessionId, final IceCandidate[] candidates);

        /**
         * Callback fired once channel is closed.
         */
        void onChannelClose(String did, String sessionId);

        /**
         * Callback fired once channel error happened.
         */
        void onChannelError(String did, String sessionId, final String description);
    }

    public static TuyaMQTTClient getInstance() {
        return TuyaMQTTClient.InstanceHolder.instance;
    }

    // Lazy initialization holder class idiom for static fields.
    private static class InstanceHolder {
        // We are storing application context so it is okay.
        static final TuyaMQTTClient instance = new TuyaMQTTClient();
    }

    public TuyaMQTTClient() {

    }

    public boolean prepare(String cid, String secret, String authCode, SignalingEvents signalingEvents) {
        this.clientId = cid;
        this.secretId = secret;
        this.authCodeId = authCode;
        this.signalingEvents = signalingEvents;
        if (getToken() != true) {
            Log.e(TAG, "get token failed.");
            return false;
        }

        if (getMqttConfig() != true) {
            Log.e(TAG, "get mqtt config failed.");
            return false;
        }
        if (connectToHost(mqttUrl, mqttClientId) != true) {
            Log.e(TAG, "connect mqtt host failed.");
            return false;
        }
        isReady = true;
        return true;
    }

    public boolean isReady() {
        return isReady;
    }

    public boolean connectToHost(String url, String clientId) {
        boolean result = false;
        if ((mqttUserName == null) || (mqttPassword == null)) {
            Log.e(TAG, "password and username is not corrected.");
            return result;
        }
        try {
            String uid = mqttUserName.substring(6) ;
            String topic = "/av/u/" + uid ;
            mqttAsyncClient = new MqttAsyncClient(url, clientId, new MemoryPersistence());
            mqttConnectOptions = new MqttConnectOptions();
            mqttConnectOptions.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1);
            mqttConnectOptions.setCleanSession(false);
            mqttConnectOptions.setKeepAliveInterval(20);
            mqttConnectOptions.setAutomaticReconnect(true);
            mqttConnectOptions.setUserName(mqttUserName);
            mqttConnectOptions.setPassword(mqttPassword.toCharArray());
            mqttAsyncClient.setCallback(this);
            Log.e("MQTT"," connect to MQTT clientId:" + clientId + " url :" + url + " userName:" + mqttUserName + " password:" + mqttPassword +" topic:" + topic);

            mqttAsyncClient.connect(mqttConnectOptions, null, this);

        } catch (MqttException e) {
            e.printStackTrace();
        }
        waitLock.lock();
        try {
            waitFinished.await(3000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        waitLock.unlock();
        Log.e(TAG,"MQTT connect return.");
        return (result = mqttConnected);
    }

    public void disconnectFromHost() {
        try {
            mqttAsyncClient.disconnect(1000L);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    public void sendLocalIceCandidate(String sessionId,
                                      String did,
                                      String webrtcConfig,
                                      IceCandidate candidate) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject webrtcConfiguration = new JSONObject(webrtcConfig);
                    JSONObject message = new JSONObject();
                    jsonPut(message, "protocol", 302);
                    jsonPut(message, "t", (System.currentTimeMillis())/1000);

                    // configuration
                    JSONObject data = new JSONObject();

                    // header
                    JSONObject header = new JSONObject();
                    String uid = mqttUserName.substring(6);
                    jsonPut(header, "from", uid);
                    jsonPut(header, "to", did);
                    jsonPut(header, "type", "candidate");
//                    jsonPut(header,"moto_id","moto_pre_cn002");
                    jsonPut(header, "moto_id", webrtcConfiguration.getString("moto_id"));
                    jsonPut(header, "sessionid", sessionId);
                    jsonPut(data, "header", header);

                    // msg
                    JSONObject msg = new JSONObject();
                    jsonPut(msg, "mode", "webrtc");
                    String strCandidate = "a=" + candidate.sdp;
                    jsonPut(msg, "candidate", strCandidate);
                    jsonPut(data, "msg", msg);

                    jsonPut(message, "data", data);

                    String jsonString = message.toString();
                    MqttMessage mqttMsg = new MqttMessage();
                    mqttMsg.setQos(1);
                    mqttMsg.setRetained(false);
                    mqttMsg.setPayload(jsonString.getBytes());

//                    String moto_topic = "/av/moto/moto_pre_cn002/u/" + _connectParameters.deviceid;
                    String moto_topic = "/av/moto/" + webrtcConfiguration.getString("moto_id") + "/u/" + did;
                    Log.d("MQTT", "====== send ice:" + jsonString);

                    mqttAsyncClient.publish(moto_topic, mqttMsg, null, new IMqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            Log.d("MQTT", "Publish succeeded...topic :" + moto_topic);
                        }

                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            Log.d("MQTT", "Publish failed...topic :" + moto_topic);
                        }
                    });
                } catch (Exception e) {

                }

            }
        }).start();
    }


    public void sendOfferSdp(String sessionId, String did, SessionDescription sdp, String webrtcConfig) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject webrtcConfiguration = new JSONObject(webrtcConfig);
                    String timeString = String.valueOf(System.currentTimeMillis());
                    JSONObject message = new JSONObject();
                    jsonPut(message, "protocol", 302);
                    jsonPut(message, "pv", "2.2");
                    jsonPut(message, "t", System.currentTimeMillis()/1000);


                    // configuration
                    JSONObject data = new JSONObject();

                    // header
                    JSONObject header = new JSONObject();
                    String uid = mqttUserName.substring(6);
                    jsonPut(header, "from", uid);
                    jsonPut(header, "to", did);
                    jsonPut(header, "type", "offer");
//                    jsonPut(header,"moto_id","moto_pre_cn002");
                    jsonPut(header, "moto_id", webrtcConfiguration.getString("moto_id"));
                    jsonPut(header, "sessionid", sessionId);

                    jsonPut(data, "header", header);

                    // msg
                    JSONObject msg = new JSONObject();
                    jsonPut(msg, "mode", "webrtc");
                    JSONArray ices = webrtcConfiguration.getJSONObject("p2p_config").getJSONArray("ices");
                    jsonPut(msg, "token", ices);
                    jsonPut(msg, "sdp", sdp.description);
                    String auth = webrtcConfiguration.getString("auth");
                    Log.d("MQTT", "===== getAuthCode the auth:" + auth);
                    jsonPut(msg, "auth", auth);

                    jsonPut(data, "msg", msg);
                    jsonPut(message, "data", data);

                    String jsonString = message.toString();
                    MqttMessage mqttMsg = new MqttMessage();
                    mqttMsg.setQos(1);
                    mqttMsg.setRetained(false);
                    mqttMsg.setPayload(jsonString.getBytes());

//                    String moto_topic = "/av/moto/moto_pre_cn002/u/" + _connectParameters.deviceid;
                    String moto_topic = "/av/moto/" + webrtcConfiguration.getString("moto_id") + "/u/" + did;
                    Log.d("MQTT", "====== send offer:" + jsonString);
                    mqttAsyncClient.publish(moto_topic, mqttMsg, null, new IMqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            Log.d("MQTT", "Publish succeeded...topic :" + moto_topic);
                        }

                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            Log.d("MQTT", "Publish failed...topic :" + moto_topic);
                        }
                    });
                } catch (Exception e) {

                }

            }
        }).start();
    }


    private void subscribeToTopic(){

        try {
            String uid = mqttUserName.substring(6) ;
            String topic = "/av/u/" + uid ;
            mqttAsyncClient.subscribe(topic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    try {
                        String []topics = asyncActionToken.getTopics();
                        for(int i = 0; i < topics.length;i++) {
                            Log.d("MQTT", "topics " + topics[i]);
                        }
                        waitLock.lock();
                        waitFinished.signal();
                        waitLock.unlock();
                        Log.d("MQTT","Subscribe success. topic:" + topic);
                    }catch (Exception e)
                    {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d("MQTT","Subscribe failed. topic:" + topic);
                }
            });
        } catch (Exception ex){
            ex.printStackTrace();
        }
    }



    boolean getMqttConfig() {
        boolean ret = false;
        try {
            //String strUrl = "https://openapi.tuyacn.com/v1.0/access/11/config" ;
            String strUrl = "https://openapi.tuyacn.com/v1.0/open-hub/access/config";
            URL url = new URL(strUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("accept", "*/*");
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            connection.setRequestProperty("Content-Type", "application/json");
            // for header.
            String ts = String.valueOf(System.currentTimeMillis());
            String sign = getSignForOthers(clientId, secretId, ts, accessToken);

            // sign
            connection.setRequestProperty("sign", sign);
            // client id
            connection.setRequestProperty("client_id", clientId);
            // ts
            connection.setRequestProperty("t", ts);
            // access token
            connection.setRequestProperty("access_token", accessToken);
            JSONObject body = new JSONObject();
            body.put("uid", "ay15543724944733IyNx");
            body.put("link_id", "10002");
            body.put("link_type", "mqtt");
            body.put("topics", "WebRTC");
            connection.setFixedLengthStreamingMode(body.toString().length());

            // Send POST request.

            OutputStream outStream = connection.getOutputStream();
            outStream.write(body.toString().getBytes("UTF-8"));
            outStream.close();

            int res = connection.getResponseCode();
            if (res == HttpURLConnection.HTTP_OK) {
                //得到响应流
                InputStream inputStream = connection.getInputStream();
                String response = drainStream(inputStream);
                JSONObject json = new JSONObject(response);
                if ((json.has("result") == true) &&
                        (json.has("success") == true) &&
                        (json.getJSONObject("result").has("client_id") == true) &&
                        (json.getJSONObject("result").has("url") == true) &&
                        (json.getJSONObject("result").has("username") == true) &&
                        (json.getJSONObject("result").has("password") == true)
                ) {
                    mqttUrl = json.getJSONObject("result").getString("url");
                    mqttClientId = json.getJSONObject("result").getString("client_id");
                    mqttUserName = json.getJSONObject("result").getString("username");
                    mqttPassword = json.getJSONObject("result").getString("password");
                    ret = true;
                } else {
                    Log.d(TAG, "===== getMqttConfig response is invalid.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }


    public boolean getWebRTCConfig(String did) {
        boolean ret = false;
        try {
            //String strUrl = "https://openapi.tuyacn.com/v1.0/devices/" + deviceid + "/camera-config?type=rtc" ;
            String strUrl = "https://openapi.tuyacn.com/v1.0/users/" + uid + "/devices/" + did + "/webrtc-configs";

            URL url = new URL(strUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(30000);
            connection.setRequestMethod("GET");
            // for header.
            String ts = String.valueOf(System.currentTimeMillis());
            String sign = getSignForOthers(clientId, secretId, ts, accessToken);

            // sign
            connection.setRequestProperty("sign", sign);
            // client id
            connection.setRequestProperty("client_id", clientId);
            // ts
            connection.setRequestProperty("t", ts);

            // access token
            connection.setRequestProperty("access_token", accessToken);
            connection.connect();
            int res = connection.getResponseCode();
            if (res == HttpURLConnection.HTTP_OK) {
                //得到响应流
                InputStream inputStream = connection.getInputStream();
                String response = drainStream(inputStream);
                JSONObject json = new JSONObject(response);
                String success = json.getString("success");

                if ((json.has("result") == true) &&
                        (json.has("success") == true) &&
                        (json.getJSONObject("result").has("auth") == true) &&
                        (json.getJSONObject("result").has("moto_id") == true) &&
                        (json.getJSONObject("result").has("p2p_config") == true) &&
                        (json.getJSONObject("result").getJSONObject("p2p_config").has("ices") == true)
                ) {
                    webrtcConfig = json.getString("result");
                    ret = true;
                } else {
                    Log.d(TAG, "===== getMqttConfig ");

                }
            }
        } catch (Exception e) {

        }
        return ret;
    }

    // Return the contents of an InputStream as a String.
    private static String drainStream(InputStream in) {
        Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public String getCurentWebRTCConfig() {
        return webrtcConfig;
    }


    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect) {
            subscribeToTopic();
        }
    }

    @Override
    public void connectionLost(Throwable cause) {

    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String jsonString = message.toString();
        JSONObject json = new JSONObject(jsonString);
        Log.d("MQTT", "messageArrived() called with: topic = [" + topic + "], message = [" + message + "]");
        if (json != null) {
            if ((json.has("data")) &&
                    (json.getJSONObject("data").has("header")) &&
                    (json.getJSONObject("data").getJSONObject("header").has("type")) &&
                    (json.getJSONObject("data").getJSONObject("header").has("from")) &&
                    (json.getJSONObject("data").getJSONObject("header").has("sessionid"))) {
                String type = json.getJSONObject("data").getJSONObject("header").getString("type");
                String sessionId = json.getJSONObject("data").getJSONObject("header").getString("sessionid");
                String deviceId = json.getJSONObject("data").getJSONObject("header").getString("from");
                if (type.equals("answer")) {
                    String sdp = json.getJSONObject("data").getJSONObject("msg").getString("sdp");
                    sdp = sdp.replaceAll("profile-level-id=42001f", "profile-level-id=42e01f");
                    SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                    Log.d("MATT", "======  on receive answer message sdp :" + sdp);
                    signalingEvents.onRemoteDescription(deviceId, sessionId, sessionDescription);
                } else if (type.equals("candidate")) {

                    String candidate = json.getJSONObject("data").getJSONObject("msg").getString("candidate");
                    candidate = candidate.substring(2);
                    IceCandidate iceCandidate = new IceCandidate("audio", 0, candidate);
                    Log.d("MATT", "======  on receive candidate message sessionDescription :" + candidate);
                    signalingEvents.onRemoteIceCandidate(deviceId, sessionId, iceCandidate);
                } else {
                    Log.d("MATT", "Receive other message..");
                }

            }
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {

    }


    boolean getToken() {
//        boolean ret = false;
//        try {
//            Log.d("MQTT", " get Token clientId:" + clientId + " secret:" + secretId + " Code:" + authCodeId);
//            String strUrl = "https://openapi.tuyacn.com/v1.0/token?code=" + authCodeId + "&grant_type=2";
//            URL url = new URL(strUrl);
//            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//            connection.setConnectTimeout(3000);
//            connection.setRequestMethod("GET");
//            // for header.
//            String ts = String.valueOf(System.currentTimeMillis());
//            String sign = getSignForGetToken(clientId, secretId, ts);
//            // sign
//            connection.setRequestProperty("sign", sign);
//            // client id
//            connection.setRequestProperty("client_id", clientId);
//            // ts
//            connection.setRequestProperty("t", ts);
//
//            Log.d("MQTT", " get Token sign:" + sign + " client_id :" + clientId + " ts:" + ts);
//            connection.connect();
//            int res = connection.getResponseCode();
//            if (res == HttpURLConnection.HTTP_OK) {
//                //得到响应流
//                InputStream inputStream = connection.getInputStream();
//                //将响应流转换成字符串
//                String response = drainStream(inputStream);
//                Log.d("MQTT", " get token result:" + response);
//                JSONObject json = new JSONObject(response);
//                if ((json.has("result") == true) &&
//                        (json.has("success") == true) &&
//                        (json.getJSONObject("result").has("uid") == true) &&
//                        (json.getJSONObject("result").has("access_token") == true) &&
//                        (json.getJSONObject("result").has("refresh_token") == true)
//                ) {
//                    uid = json.getJSONObject("result").getString("uid");
//                    accessToken = json.getJSONObject("result").getString("access_token");
//                    refreshToken = json.getJSONObject("result").getString("access_token");
//                    ret = true;
//                } else {
//                    Log.d(TAG, "getToken failed. ");
//
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return ret;

        uid = "ay15543724944733IyNx";
        accessToken = "c04ef4d783ef001a1f09aa0116e92a6a";
        refreshToken = "c2f658821fa48a682f560dd89cb49888";

        return true;
    }

    private String getSignForGetToken(String clientId, String secret, String ts) {
        String astring = clientId + secret + ts;
        Log.d("MQTT", "getSignForGetToken astring:" + astring);
        String md5String = md5(astring);
        return md5String.toUpperCase();
    }

    private String getSignForOthers(String clientId, String secret, String ts, String accessToken) {
        String astring = clientId + accessToken + secret + ts;
        String md5String = md5(astring);
        return md5String.toUpperCase();
    }


    public String md5(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    temp = "0" + temp;
                }
                result.append(temp);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

}
