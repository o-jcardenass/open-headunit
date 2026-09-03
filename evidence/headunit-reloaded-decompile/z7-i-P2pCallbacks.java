package z7;

import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.support.v4.media.session.t;
import android.util.Log;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final class i implements WifiP2pManager.ActionListener {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    public final /* synthetic */ int f13287a;

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public final /* synthetic */ t f13288b;

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    public final /* synthetic */ j f13289c;

    public /* synthetic */ i(int i5, t tVar, j jVar) {
        this.f13287a = i5;
        this.f13289c = jVar;
        this.f13288b = tVar;
    }

    @Override // android.net.wifi.p2p.WifiP2pManager.ActionListener
    public final void onFailure(int i5) {
        switch (this.f13287a) {
            case 0:
                j.b(this.f13289c, this.f13288b);
                break;
            default:
                Log.e("HUR-Hotspot", "P2P createGroup failed: " + i5);
                j jVar = this.f13289c;
                t tVar = this.f13288b;
                Log.w("HUR-Hotspot", "WiFi-P2P failed - falling back to LocalOnlyHotspot (video will cap to 720p)");
                jVar.f13295f = true;
                if (Build.VERSION.SDK_INT < 26) {
                    tVar.L("No usable hotspot mechanism (P2P failed, LOH needs API 26+)");
                } else {
                    WifiManager wifiManager = (WifiManager) jVar.f13290a.getSystemService("wifi");
                    if (wifiManager == null) {
                        tVar.L("WifiManager unavailable");
                    } else {
                        try {
                            wifiManager.startLocalOnlyHotspot(new h(jVar, tVar), null);
                        } catch (Throwable th) {
                            Log.e("HUR-Hotspot", "startLocalOnlyHotspot threw", th);
                            tVar.L("startLocalOnlyHotspot: " + th.getMessage());
                            return;
                        }
                    }
                }
                break;
        }
    }

    @Override // android.net.wifi.p2p.WifiP2pManager.ActionListener
    public final void onSuccess() {
        switch (this.f13287a) {
            case 0:
                j.b(this.f13289c, this.f13288b);
                break;
            default:
                new Handler(this.f13289c.f13290a.getMainLooper()).postDelayed(new c.n(this, 4, this.f13288b), 1200L);
                break;
        }
    }
}
