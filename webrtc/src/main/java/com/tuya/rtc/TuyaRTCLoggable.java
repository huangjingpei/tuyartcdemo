package com.tuya.rtc;

import android.os.FileUtils;
import android.util.Log;

import org.webrtc.Loggable;
import org.webrtc.Logging.Severity;

public class TuyaRTCLoggable implements Loggable {
    private static final String TAG = "TuyaRTCLoggable";
    private String toSeverity(Severity severity) {
        switch (severity) {
            case LS_VERBOSE:
                return "VERBOSE";
            case LS_ERROR:
                return "ERROR";
            case LS_WARNING:
                return "WARNING";
            case LS_INFO:
                return "INFO";
            default:
                return "UNKNOWN";
        }
    }
    @Override
    public void onLogMessage(String message, Severity severity, String tag) {
//        Log.e(TAG, tag + " " + "[" + toSeverity(severity) + "]" + " " + message);
    }


}
