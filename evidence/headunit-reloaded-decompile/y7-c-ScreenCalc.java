package y7;

import android.content.Context;
import android.graphics.Point;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import gb.xxy.hr.proto.VideoCodecResolutionType;
import z7.p;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final class c {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    public boolean f13201a;

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public b f13202b;

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    public int f13203c;

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public int f13204d;

    /* JADX WARN: Code duplicated, block: B:44:0x016c  */
    public final b a(int i5, int i6) {
        float f2;
        Log.d("ScreenCalc", "screenWidth: " + i5 + " screenHeight: " + i6);
        this.f13203c = i5;
        this.f13204d = i6;
        b bVar = new b();
        float f6 = 1.0f;
        bVar.f13194d = 1.0f;
        this.f13202b = bVar;
        boolean z6 = p.f13312n && p.f13313o < 5000;
        if (z6) {
            Log.d("ScreenCalc", "Wireless non-5GHz session: capping projected resolution to 720p");
        }
        if (i5 <= 800 && i6 <= 480) {
            b bVar2 = this.f13202b;
            bVar2.f13191a = VideoCodecResolutionType.VIDEO_800x480;
            bVar2.f13192b = 800;
            bVar2.f13193c = 480;
        } else if ((i5 > 1280 || i6 > 720) && !z6) {
            b bVar3 = this.f13202b;
            bVar3.f13191a = VideoCodecResolutionType.VIDEO_1920x1080;
            bVar3.f13192b = 1920;
            bVar3.f13193c = 1080;
        } else {
            b bVar4 = this.f13202b;
            bVar4.f13191a = VideoCodecResolutionType.VIDEO_1280x720;
            bVar4.f13192b = 1280;
            bVar4.f13193c = 720;
        }
        b bVar5 = this.f13202b;
        int i10 = bVar5.f13192b;
        if (i5 > i10 || i6 > bVar5.f13193c) {
            float f8 = i5;
            float f10 = i6;
            float f11 = f8 / f10;
            int i11 = bVar5.f13193c;
            if (f11 < i10 / i11) {
                this.f13201a = true;
                bVar5.f13194d = (f10 * 1.0f) / i11;
            } else {
                this.f13201a = false;
                bVar5.f13194d = (f8 * 1.0f) / i10;
            }
        }
        bVar5.f13196f = c() / 2;
        this.f13202b.f13195e = d() / 2;
        b bVar6 = this.f13202b;
        float fC = (bVar6.f13193c - c()) / this.f13204d;
        Log.d("CarScreen", "Vertical correction: " + fC + ", hegith " + this.f13202b.f13193c + ", marg: " + c() + ", height: " + this.f13204d);
        bVar6.j = fC;
        b bVar7 = this.f13202b;
        Log.d("CarScreen", "Horizontal correction: 0, width " + this.f13202b.f13192b + ", marg: " + d() + ", width: " + this.f13203c);
        float fD = (float) (this.f13202b.f13192b - d());
        int i12 = this.f13203c;
        float f12 = (float) i12;
        bVar7.f13199i = fD / f12;
        b bVar8 = this.f13202b;
        int i13 = this.f13204d;
        if (i13 > i12) {
            float fRound = Math.round(bVar8.f13192b * bVar8.f13194d);
            float f13 = this.f13203c * 1.0f;
            if (f13 == 0.0f) {
                f2 = 1.0f;
            } else {
                f2 = fRound / f13;
            }
        } else {
            int i14 = bVar8.f13192b;
            if (i14 > i12) {
                float f14 = i14;
                float f15 = f12 * 1.0f;
                if (f15 == 0.0f) {
                    f2 = 1.0f;
                } else {
                    f2 = f14 / f15;
                }
            } else if (this.f13201a) {
                f2 = (i14 / bVar8.f13193c) / (i12 / i13);
            } else {
                f2 = 1.0f;
            }
        }
        bVar8.f13197g = f2;
        Log.d("CrScreen", "ScaleX is: " + this.f13202b.f13197g);
        b bVar9 = this.f13202b;
        int i15 = bVar9.f13193c;
        int i16 = this.f13204d;
        if (i15 > i16) {
            float f16 = i15;
            float f17 = i16 * 1.0f;
            if (f17 != 0.0f) {
                f6 = f16 / f17;
            }
        } else if (!this.f13201a) {
            f6 = (this.f13203c / i16) / (bVar9.f13192b / i15);
        }
        bVar9.f13198h = f6;
        bVar9.f13200k = Math.round(((bVar9.f13192b - bVar9.f13195e) / 800.0f) * 150.0f);
        return this.f13202b;
    }

    public final b b(Context context) {
        Display defaultDisplay = ((WindowManager) context.getApplicationContext().getSystemService("window")).getDefaultDisplay();
        Point point = new Point();
        defaultDisplay.getRealSize(point);
        return a(point.x, point.y);
    }

    public final int c() {
        StringBuilder sb = new StringBuilder("Zoom is: ");
        sb.append(this.f13202b.f13194d);
        sb.append(", adjusted height: ");
        b bVar = this.f13202b;
        sb.append(Integer.valueOf(Math.round(bVar.f13193c * bVar.f13194d)));
        Log.d("CarScreen", sb.toString());
        b bVar2 = this.f13202b;
        int iIntValue = Float.valueOf((Math.round(bVar2.f13193c * bVar2.f13194d) - this.f13204d) / this.f13202b.f13194d).intValue();
        if (iIntValue < 0) {
            return 0;
        }
        return iIntValue;
    }

    public final int d() {
        StringBuilder sb = new StringBuilder("Zoom is: ");
        sb.append(this.f13202b.f13194d);
        sb.append(", adjusted width: ");
        b bVar = this.f13202b;
        sb.append(Integer.valueOf(Math.round(bVar.f13192b * bVar.f13194d)));
        Log.d("CarScreen", sb.toString());
        b bVar2 = this.f13202b;
        int iIntValue = Float.valueOf((Math.round(bVar2.f13192b * bVar2.f13194d) - this.f13203c) / this.f13202b.f13194d).intValue();
        if (iIntValue < 0) {
            return 0;
        }
        return iIntValue;
    }
}
