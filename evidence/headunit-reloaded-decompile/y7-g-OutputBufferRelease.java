package y7;

import android.app.Activity;
import android.media.MediaCodec;
import gb.xxy.hr.proto.Ack;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final class g implements Runnable {

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public final /* synthetic */ MediaCodec f13215d;

    /* JADX INFO: renamed from: e, reason: collision with root package name */
    public final /* synthetic */ int f13216e;

    /* JADX INFO: renamed from: f, reason: collision with root package name */
    public final /* synthetic */ MediaCodec.BufferInfo f13217f;

    /* JADX INFO: renamed from: g, reason: collision with root package name */
    public final /* synthetic */ h f13218g;

    public g(h hVar, MediaCodec mediaCodec, int i5, MediaCodec.BufferInfo bufferInfo) {
        this.f13218g = hVar;
        this.f13215d = mediaCodec;
        this.f13216e = i5;
        this.f13217f = bufferInfo;
    }

    @Override // java.lang.Runnable
    public final void run() {
        try {
            this.f13215d.releaseOutputBuffer(this.f13216e, this.f13217f.presentationTimeUs);
            Ack.Builder builderNewBuilder = Ack.newBuilder();
            builderNewBuilder.setSessionId(((Integer) o7.g.f8729g.get((byte) 3)).intValue());
            builderNewBuilder.setAck(1);
            s9.d.b().e(new n7.a((byte) 3, 32772, builderNewBuilder));
        } catch (IllegalStateException unused) {
            Activity activity = this.f13218g.f13220b.j;
            if (activity != null) {
                activity.recreate();
            }
        }
    }
}
