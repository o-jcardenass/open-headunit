package gb.xxy.hr.activities;

import a1.c;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import androidx.lifecycle.a0;
import c.g0;
import c.o;
import e2.m1;
import e2.w1;
import e5.a;
import gb.xxy.hr.R;
import gb.xxy.hr.TransporterService;
import gb.xxy.hr.activities.Player;
import gb.xxy.hr.proto.KeyCode;
import gb.xxy.hr.proto.RelativeEvent;
import i.b0;
import i.g;
import i8.w;
import k8.r;
import l7.e;
import n.j;
import t0.d0;
import x7.b;
import y7.f;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public class Player extends o {
    public static Player F;
    public final r C;
    public final b0 E;

    /* JADX INFO: renamed from: w, reason: collision with root package name */
    public TransporterService f5059w;

    /* JADX INFO: renamed from: x, reason: collision with root package name */
    public boolean f5060x;

    /* JADX INFO: renamed from: y, reason: collision with root package name */
    public b f5061y;

    /* JADX INFO: renamed from: z, reason: collision with root package name */
    public FrameLayout f5062z;
    public boolean A = false;
    public final a0 B = new a0(Boolean.FALSE);
    public final e D = new e(this);

    public Player() {
        int i5 = 2;
        this.C = new r(i5, this);
        this.E = new b0(i5, this);
    }

    public final void k() {
        try {
            int i5 = 0;
            int i6 = Integer.parseInt(getSharedPreferences(j.c(this), 0).getString("screen_orientation", "0"));
            if (i6 != 1) {
                if (i6 == 2) {
                    i5 = 1;
                } else if (i6 != 3) {
                    i5 = i6 != 4 ? -1 : 9;
                } else {
                    i5 = 8;
                }
            }
            if (getRequestedOrientation() != i5) {
                setRequestedOrientation(i5);
            }
        } catch (Exception e10) {
            e10.printStackTrace();
        }
    }

    public final void l() {
        if (this.A) {
            unbindService(this.D);
            this.A = false;
        }
        a.b("allow", this);
        TransporterService transporterService = this.f5059w;
        if (transporterService != null) {
            transporterService.f5045e = null;
            this.f5059w = null;
        }
        FrameLayout frameLayout = this.f5062z;
        if (frameLayout != null) {
            frameLayout.removeAllViews();
            this.f5062z.setVisibility(8);
        }
        try {
            unregisterReceiver(this.E);
        } catch (Exception unused) {
        }
    }

    public final void m(g gVar) {
        KeyEvent keyEvent = (KeyEvent) gVar.f5435e;
        boolean z6 = (keyEvent.getAction() ^ 1) == 1;
        int i5 = gVar.f5434d;
        long eventTime = keyEvent.getEventTime();
        if (i5 == ((x7.a) this.f5061y.f12533a.get(85)).f12530a) {
            s7.b.b(85, z6, Long.valueOf(eventTime));
        }
        if (i5 == ((x7.a) this.f5061y.f12533a.get(87)).f12530a) {
            s7.b.b(87, z6, Long.valueOf(eventTime));
        }
        if (i5 == ((x7.a) this.f5061y.f12533a.get(88)).f12530a) {
            s7.b.b(88, z6, Long.valueOf(eventTime));
        }
        if (i5 == ((x7.a) this.f5061y.f12533a.get(21)).f12530a) {
            if (this.f5060x) {
                s7.b.b(21, z6, Long.valueOf(eventTime));
            } else if (z6) {
                s7.b.a(null, null, RelativeEvent.newBuilder().addData(RelativeEvent.Rel.newBuilder().setKeycode(KeyCode.KEYCODE_ROTARY_CONTROLLER_VALUE).setDelta(-1)), Long.valueOf(eventTime));
            }
        }
        if (i5 == ((x7.a) this.f5061y.f12533a.get(22)).f12530a) {
            if (this.f5060x) {
                s7.b.b(22, z6, Long.valueOf(eventTime));
            } else if (z6) {
                s7.b.a(null, null, RelativeEvent.newBuilder().addData(RelativeEvent.Rel.newBuilder().setKeycode(KeyCode.KEYCODE_ROTARY_CONTROLLER_VALUE).setDelta(1)), Long.valueOf(eventTime));
            }
        }
        if (i5 == ((x7.a) this.f5061y.f12533a.get(19)).f12530a && !this.f5060x) {
            s7.b.b(19, z6, Long.valueOf(eventTime));
        }
        if (i5 == ((x7.a) this.f5061y.f12533a.get(20)).f12530a && !this.f5060x) {
            s7.b.b(20, z6, Long.valueOf(eventTime));
        }
        if (i5 == ((x7.a) this.f5061y.f12533a.get(66)).f12530a) {
            s7.b.b(66, z6, Long.valueOf(eventTime));
        }
        if (i5 == ((x7.a) this.f5061y.f12533a.get(3)).f12530a) {
            s7.b.b(3, z6, Long.valueOf(eventTime));
        }
        if (i5 == ((x7.a) this.f5061y.f12533a.get(5)).f12530a) {
            s7.b.b(5, z6, Long.valueOf(eventTime));
        }
        if (i5 == ((x7.a) this.f5061y.f12533a.get(Integer.valueOf(KeyCode.KEYCODE_VOICE_ASSIST_VALUE))).f12530a) {
            s7.b.b(KeyCode.KEYCODE_VOICE_ASSIST_VALUE, z6, Long.valueOf(eventTime));
        }
        if (i5 == ((x7.a) this.f5061y.f12533a.get(Integer.valueOf(KeyCode.KEYCODE_MUSIC_VALUE))).f12530a) {
            s7.b.b(KeyCode.KEYCODE_MUSIC_VALUE, z6, Long.valueOf(eventTime));
        }
    }

    @Override // c.o, m3.i, android.app.Activity
    public final void onCreate(Bundle bundle) {
        Log.e("PlayerScreen", "onCreate - Fresh Instance Started");
        super.onCreate(bundle);
        requestWindowFeature(1);
        d0.m(getWindow(), false);
        setContentView(R.layout.player);
        F = this;
        m1 m1Var = new m1(this);
        m1Var.setViewCompositionStrategy(w1.f4074e);
        addContentView(m1Var, new ViewGroup.LayoutParams(-1, -1));
        a0 a0Var = this.B;
        a9.j.e(a0Var, "showNavigationDialog");
        m1Var.setContent(new c(new w(a0Var, this), -569583058, true));
        i().a(this, new g0(3, this));
        k();
    }

    @Override // android.app.Activity
    public final void onDestroy() {
        Log.e("PlayerScreen", "onDestroy - Activity Destroyed");
        l();
        getSharedPreferences(j.c(this), 0).unregisterOnSharedPreferenceChangeListener(this.C);
        F = null;
        super.onDestroy();
    }

    @Override // android.app.Activity, android.view.KeyEvent.Callback
    public final boolean onKeyDown(int i5, KeyEvent keyEvent) {
        m(new g(i5, keyEvent));
        return super.onKeyDown(i5, keyEvent);
    }

    @Override // android.app.Activity, android.view.KeyEvent.Callback
    public final boolean onKeyUp(int i5, KeyEvent keyEvent) {
        m(new g(i5, keyEvent));
        return super.onKeyUp(i5, keyEvent);
    }

    @Override // android.app.Activity
    public final void onPause() {
        Log.e("PlayerScreen", "onPause");
        if (Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode()) {
            super.onPause();
        } else {
            l();
            super.onPause();
        }
    }

    @Override // c.o, android.app.Activity
    public final void onPictureInPictureModeChanged(boolean z6, Configuration configuration) {
        super.onPictureInPictureModeChanged(z6, configuration);
        Log.d("PlayerScreen", "PiP Changed: " + z6 + ". Forcing recreation.");
        recreate();
    }

    @Override // android.app.Activity
    public final void onResume() {
        super.onResume();
        Log.d("PlayerScreen", "onResume");
        SharedPreferences sharedPreferences = getSharedPreferences(j.c(this), 0);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this.C);
        a.i0(this);
        a.b("deny", this);
        int i5 = Build.VERSION.SDK_INT;
        b0 b0Var = this.E;
        if (i5 >= 26) {
            registerReceiver(b0Var, new IntentFilter("hur.exit"), 4);
        } else {
            registerReceiver(b0Var, new IntentFilter("hur.exit"));
        }
        bindService(new Intent(this, (Class<?>) TransporterService.class), this.D, 0);
        this.f5062z = (FrameLayout) findViewById(R.id.holder);
        ((InputMethodManager) getSystemService("input_method")).hideSoftInputFromWindow(this.f5062z.getWindowToken(), 0);
        this.f5060x = sharedPreferences.getBoolean("reverse_left_righ", false);
        this.f5061y = new b(this);
        final boolean z6 = i5 >= 24 && isInPictureInPictureMode();
        final View viewFindViewById = findViewById(android.R.id.content);
        viewFindViewById.post(new Runnable() { // from class: l7.d
            @Override // java.lang.Runnable
            public final void run() {
                Player player = Player.F;
                View view = viewFindViewById;
                int width = view.getWidth();
                int height = view.getHeight();
                if (width <= 0 || height <= 0) {
                    return;
                }
                Player player2 = this.f7309d;
                player2.f5062z.removeAllViews();
                SharedPreferences sharedPreferences2 = player2.getSharedPreferences(j.c(player2), 0);
                int iU = h5.a.U(sharedPreferences2.getString("margin_top", "0"));
                int iU2 = h5.a.U(sharedPreferences2.getString("margin_left", "0"));
                y7.b bVarA = new y7.c().a(width, height);
                player2.f5062z.setOnTouchListener(new f(bVarA.f13199i, bVarA.j, iU2, iU));
                boolean zEqualsIgnoreCase = sharedPreferences2.getString("rendersurf", "0").equalsIgnoreCase("0");
                boolean z9 = z6;
                View eVar = zEqualsIgnoreCase ? new y7.e(player2, z9) : new y7.d(player2, z9);
                player2.f5062z.addView(eVar);
                if (!sharedPreferences2.getBoolean("forcedscale", false)) {
                    eVar.setScaleX(bVarA.f13197g);
                    eVar.setScaleY(bVarA.f13198h);
                    return;
                }
                ViewGroup.LayoutParams layoutParams = eVar.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.height = bVarA.f13193c;
                    layoutParams.width = bVarA.f13192b;
                }
                eVar.setTranslationX((bVarA.f13195e * (-1.0f)) / 2.0f);
                eVar.setTranslationY((bVarA.f13196f * (-1.0f)) / 2.0f);
            }
        });
        this.f5062z.setVisibility(0);
        k();
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public final void onWindowFocusChanged(boolean z6) {
        super.onWindowFocusChanged(z6);
        if (z6) {
            a.i0(this);
        }
    }
}
