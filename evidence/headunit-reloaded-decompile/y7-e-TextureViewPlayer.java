package y7;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final class e extends TextureView implements TextureView.SurfaceTextureListener {

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public i f13208d;

    /* JADX INFO: renamed from: e, reason: collision with root package name */
    public final Activity f13209e;

    /* JADX INFO: renamed from: f, reason: collision with root package name */
    public final boolean f13210f;

    public e(Activity activity, boolean z6) {
        super(activity);
        this.f13209e = activity;
        this.f13210f = z6;
        setSurfaceTextureListener(this);
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public final void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i5, int i6) {
        this.f13208d = new i(this, new Surface(surfaceTexture), this.f13209e, i5, i6, this.f13210f);
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public final boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        i iVar = this.f13208d;
        if (iVar != null) {
            iVar.a();
        }
        this.f13208d = null;
        return false;
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public final void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i5, int i6) {
        Log.d("HUR-Texture", "Surface Size Changed: " + i5 + "x" + i6);
        i iVar = this.f13208d;
        if (iVar != null) {
            iVar.a();
        }
        this.f13208d = new i(this, new Surface(surfaceTexture), this.f13209e, i5, i6, this.f13210f);
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public final void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }
}
