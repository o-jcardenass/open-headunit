package y7;

import android.app.Activity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final class d extends SurfaceView implements SurfaceHolder.Callback {

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public final boolean f13205d;

    /* JADX INFO: renamed from: e, reason: collision with root package name */
    public i f13206e;

    /* JADX INFO: renamed from: f, reason: collision with root package name */
    public final Activity f13207f;

    public d(Activity activity, boolean z6) {
        super(activity);
        this.f13207f = activity;
        this.f13205d = z6;
        getHolder().addCallback(this);
    }

    @Override // android.view.SurfaceHolder.Callback
    public final void surfaceChanged(SurfaceHolder surfaceHolder, int i5, int i6, int i10) {
        Log.d("SurfaceViewPlayer", "Surface changed: " + this.f13205d + ", decoder: " + this.f13206e);
        if (this.f13206e == null) {
            this.f13206e = new i(this, surfaceHolder.getSurface(), this.f13207f, i6, i10, this.f13205d);
        }
    }

    @Override // android.view.SurfaceHolder.Callback
    public final void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        i iVar = this.f13206e;
        if (iVar != null) {
            iVar.a();
        }
    }

    @Override // android.view.SurfaceHolder.Callback
    public final void surfaceCreated(SurfaceHolder surfaceHolder) {
    }
}
