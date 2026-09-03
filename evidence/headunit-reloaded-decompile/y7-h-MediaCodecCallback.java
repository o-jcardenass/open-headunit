package y7;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.View;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final class h extends MediaCodec.Callback {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    public final /* synthetic */ View f13219a;

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public final /* synthetic */ i f13220b;

    public h(i iVar, View view) {
        this.f13220b = iVar;
        this.f13219a = view;
    }

    @Override // android.media.MediaCodec.Callback
    public final void onError(MediaCodec mediaCodec, MediaCodec.CodecException codecException) {
        Log.d("HU-MEDIADECODER", "Error");
        Activity activity = this.f13220b.j;
        if (activity != null) {
            activity.recreate();
        }
        codecException.printStackTrace();
    }

    @Override // android.media.MediaCodec.Callback
    public final void onInputBufferAvailable(MediaCodec mediaCodec, int i5) {
        this.f13220b.f13227g.offer(Integer.valueOf(i5));
    }

    @Override // android.media.MediaCodec.Callback
    public final void onOutputBufferAvailable(MediaCodec mediaCodec, int i5, MediaCodec.BufferInfo bufferInfo) {
        this.f13219a.post(new g(this, mediaCodec, i5, bufferInfo));
    }

    @Override // android.media.MediaCodec.Callback
    public final void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat mediaFormat) {
        Log.d("HU-MEDIADECODER", "Output format changed!");
    }
}
