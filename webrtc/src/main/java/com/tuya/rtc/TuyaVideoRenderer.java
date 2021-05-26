package com.tuya.rtc;

import android.content.Context;

import org.webrtc.EglBase;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoFrame;

public class TuyaVideoRenderer extends SurfaceViewRenderer {
    public TuyaVideoRenderer(Context context) {
        super(context);
    }

    @Override
    public void init(EglBase.Context sharedContext, RendererCommon.RendererEvents rendererEvents, int[] configAttributes, RendererCommon.GlDrawer drawer) {
        super.init(sharedContext, rendererEvents, configAttributes, drawer);
    }

    @Override
    public void init(EglBase.Context sharedContext, RendererCommon.RendererEvents rendererEvents) {
        super.init(sharedContext, rendererEvents);
    }

    @Override
    public void onFrame(VideoFrame frame) {
        super.onFrame(frame);
    }
}
