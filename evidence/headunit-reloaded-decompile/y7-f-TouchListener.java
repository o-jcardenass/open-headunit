package y7;

import android.view.MotionEvent;
import android.view.View;
import gb.xxy.hr.proto.PointerAction;
import gb.xxy.hr.proto.TouchEvent;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final class f implements View.OnTouchListener {

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public final int f13211d;

    /* JADX INFO: renamed from: e, reason: collision with root package name */
    public final int f13212e;

    /* JADX INFO: renamed from: f, reason: collision with root package name */
    public final float f13213f;

    /* JADX INFO: renamed from: g, reason: collision with root package name */
    public final float f13214g;

    public f(float f2, float f6, int i5, int i6) {
        this.f13214g = f2;
        this.f13213f = f6;
        this.f13211d = i5;
        this.f13212e = i6;
    }

    @Override // android.view.View.OnTouchListener
    public final boolean onTouch(View view, MotionEvent motionEvent) {
        PointerAction pointerAction;
        motionEvent.getActionIndex();
        int actionMasked = motionEvent.getActionMasked();
        motionEvent.getPointerCount();
        TouchEvent.Builder builderNewBuilder = TouchEvent.newBuilder();
        for (int i5 = 0; i5 < motionEvent.getPointerCount(); i5++) {
            motionEvent.getX();
            motionEvent.getY();
            motionEvent.getX(i5);
            motionEvent.getY(i5);
            builderNewBuilder.addPointerData(TouchEvent.Pointer.newBuilder().setX((int) ((motionEvent.getX(i5) - this.f13211d) * this.f13214g)).setY((int) ((motionEvent.getY(i5) - this.f13212e) * this.f13213f)).setPointerId(motionEvent.getPointerId(i5)).build());
        }
        if (actionMasked == 0) {
            pointerAction = PointerAction.ACTION_DOWN;
        } else if (actionMasked == 1) {
            pointerAction = PointerAction.ACTION_UP;
        } else if (actionMasked == 2) {
            pointerAction = PointerAction.ACTION_MOVED;
        } else if (actionMasked == 3) {
            pointerAction = PointerAction.ACTION_UP;
        } else if (actionMasked == 5) {
            pointerAction = PointerAction.ACTION_POINTER_DOWN;
        } else {
            if (actionMasked != 6) {
                return false;
            }
            pointerAction = PointerAction.ACTION_POINTER_UP;
        }
        builderNewBuilder.setAction(pointerAction);
        builderNewBuilder.setActionIndex(motionEvent.getActionIndex());
        s7.b.a(builderNewBuilder, null, null, null);
        builderNewBuilder.toString();
        return true;
    }
}
