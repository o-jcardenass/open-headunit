package y7;

import android.app.Activity;
import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import gb.xxy.hr.TransporterService;
import gb.xxy.hr.proto.Insets;
import gb.xxy.hr.proto.UiConfig;
import gb.xxy.hr.proto.UpdateUiConfigRequest;
import gb.xxy.hr.proto.VideoFocusMode;
import gb.xxy.hr.proto.VideoFocusNotification;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import n.j;
import z7.p;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final class i {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    public final b f13221a;

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public final int f13222b;

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    public final int f13223c;

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public final int f13224d;

    /* JADX INFO: renamed from: e, reason: collision with root package name */
    public final int f13225e;

    /* JADX INFO: renamed from: f, reason: collision with root package name */
    public MediaCodec f13226f;

    /* JADX INFO: renamed from: h, reason: collision with root package name */
    public final Surface f13228h;
    public final Activity j;

    /* JADX INFO: renamed from: k, reason: collision with root package name */
    public final boolean f13230k;

    /* JADX INFO: renamed from: l, reason: collision with root package name */
    public final byte f13231l;

    /* JADX INFO: renamed from: g, reason: collision with root package name */
    public final ArrayBlockingQueue f13227g = new ArrayBlockingQueue(1024);

    /* JADX INFO: renamed from: i, reason: collision with root package name */
    public volatile boolean f13229i = false;

    public i(View view, Surface surface, Activity activity, int i5, int i6, boolean z6) {
        MediaFormat mediaFormatCreateVideoFormat;
        this.f13228h = surface;
        this.j = activity;
        this.f13230k = z6;
        SharedPreferences sharedPreferencesB = j.b(view.getContext());
        if (z6) {
            this.f13231l = (byte) 12;
            this.f13221a = new c().b(activity);
            this.f13222b = 0;
            this.f13223c = 0;
            this.f13224d = 0;
            this.f13225e = 0;
        } else {
            this.f13231l = (byte) 3;
            this.f13221a = new c().a(i5, i6);
            this.f13222b = h5.a.U(sharedPreferencesB.getString("margin_top", "0"));
            this.f13223c = h5.a.U(sharedPreferencesB.getString("margin_bottom", "0"));
            this.f13224d = h5.a.U(sharedPreferencesB.getString("margin_left", "0"));
            this.f13225e = h5.a.U(sharedPreferencesB.getString("margin_right", "0"));
        }
        Log.d("HU-MEDIADECODER", "Decoder created");
        try {
            if (sharedPreferencesB.getBoolean("h264_accel", true)) {
                this.f13226f = MediaCodec.createDecoderByType("video/avc");
            } else {
                this.f13226f = MediaCodec.createByCodecName("OMX.google.h264.decoder");
            }
            this.f13226f.setCallback(new h(this, view));
            Log.d("HU-MEDIADECODER", "Using: " + this.f13221a.f13192b + " x " + this.f13221a.f13193c + ", PiP: " + z6);
            if (z6) {
                mediaFormatCreateVideoFormat = MediaFormat.createVideoFormat("video/avc", 800, 480);
            } else {
                b bVar = this.f13221a;
                mediaFormatCreateVideoFormat = MediaFormat.createVideoFormat("video/avc", bVar.f13192b, bVar.f13193c);
            }
            mediaFormatCreateVideoFormat.setInteger("frame-rate", 30);
            mediaFormatCreateVideoFormat.setInteger("max-input-size", 13107200);
            try {
                this.f13226f.configure(mediaFormatCreateVideoFormat, surface, (MediaCrypto) null, 0);
            } catch (Exception e10) {
                e10.printStackTrace();
            }
            this.f13226f.start();
            b(true);
            new Thread(new a4.b(23, this)).start();
        } catch (IOException unused) {
            Activity activity2 = this.j;
            if (activity2 != null) {
                activity2.recreate();
            }
        }
    }

    public final void a() {
        this.f13229i = true;
        this.f13227g.clear();
        b(false);
        try {
            MediaCodec mediaCodec = this.f13226f;
            if (mediaCodec != null) {
                mediaCodec.flush();
                this.f13226f.stop();
                this.f13226f.release();
            }
            Surface surface = this.f13228h;
            if (surface != null && surface.isValid()) {
                this.f13228h.release();
            }
        } catch (Exception e10) {
            Log.e("HU-MEDIADECODER", "Error stopping decoder", e10);
        } finally {
            this.f13226f = null;
        }
    }

    public final void b(boolean z6) {
        p pVar;
        UpdateUiConfigRequest.Builder builderNewBuilder = UpdateUiConfigRequest.newBuilder();
        if (z6) {
            UiConfig uiConfigBuild = UiConfig.newBuilder().setMargins((!this.f13230k ? Insets.newBuilder().setLeft(this.f13221a.f13195e + this.f13224d).setRight(this.f13221a.f13195e + this.f13225e).setTop(this.f13221a.f13196f + this.f13222b).setBottom(this.f13221a.f13196f + this.f13223c) : Insets.newBuilder().setLeft(0).setRight(0).setTop(0).setBottom(0)).build()).build();
            builderNewBuilder.setUiConfig(uiConfigBuild);
            Log.d("HU-MEDIADECODER", "Sending ui update request: " + uiConfigBuild);
            s9.d.b().e(new n7.a(this.f13231l, 32777, builderNewBuilder));
        }
        VideoFocusNotification.Builder builderNewBuilder2 = VideoFocusNotification.newBuilder();
        builderNewBuilder2.setFocus(z6 ? VideoFocusMode.VIDEO_FOCUS_PROJECTED_NO_INPUT_FOCUS : VideoFocusMode.VIDEO_FOCUS_NATIVE);
        builderNewBuilder2.setUnsolicited(true);
        s9.d.b().e(new n7.a(this.f13231l, 32776, builderNewBuilder2));
        Log.d("HU-MEDIADECODER", "Sent video focus: " + builderNewBuilder2.getFocus());
        if (!z6 || this.f13230k) {
            return;
        }
        try {
            TransporterService transporterService = TransporterService.f5038u;
            if (transporterService == null || (pVar = transporterService.f5051l) == null) {
                return;
            }
            if (pVar.f13317d == null && pVar.f13320g == null && pVar.f13319f == null) {
                return;
            }
            Log.d("HUR-WirelessBT", "VideoFocus acquired - dropping dummy HFP (bootstrap complete)");
            pVar.h();
        } catch (Throwable th) {
            Log.d("HU-MEDIADECODER", "Could not notify wireless BT of video focus: " + th);
        }
    }
}
