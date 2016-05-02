package de.stetro.tango.arnavigation.ui.util;

import android.view.MotionEvent;

import org.rajawali3d.math.vector.Vector3;

public class MapTransformationGestureDetector {
    private static final int INVALID_POINTER_ID = -1;
    private float fX, fY, sX, sY;
    private int ptrID1, ptrID2;
    private float angle;
    private float scale;
    private Vector3 translation;

    private OnMapTransformationGestureListener mListener;

    public MapTransformationGestureDetector(OnMapTransformationGestureListener listener) {
        mListener = listener;
        ptrID1 = INVALID_POINTER_ID;
        ptrID2 = INVALID_POINTER_ID;
    }

    public float getAngle() {
        return angle;
    }

    public float getScale() {
        return scale;
    }

    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ptrID1 = event.getPointerId(event.getActionIndex());
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                ptrID2 = event.getPointerId(event.getActionIndex());
                sX = event.getX(event.findPointerIndex(ptrID1));
                sY = event.getY(event.findPointerIndex(ptrID1));
                fX = event.getX(event.findPointerIndex(ptrID2));
                fY = event.getY(event.findPointerIndex(ptrID2));
                break;
            case MotionEvent.ACTION_MOVE:
                if (ptrID1 != INVALID_POINTER_ID && ptrID2 != INVALID_POINTER_ID) {
                    float nfX, nfY, nsX, nsY;
                    nsX = event.getX(event.findPointerIndex(ptrID1));
                    nsY = event.getY(event.findPointerIndex(ptrID1));
                    nfX = event.getX(event.findPointerIndex(ptrID2));
                    nfY = event.getY(event.findPointerIndex(ptrID2));

                    angle = angleBetweenLines(fX, fY, sX, sY, nfX, nfY, nsX, nsY);
                    float newScale = scaleBetweenLines(fX, fY, sX, sY, nfX, nfY, nsX, nsY);
                    if (newScale > 0) {
                        scale = newScale;
                    }
                    translation = new Vector3((nfX - fX + nsX - sX) / 2, (nfY - fY + nsY - sY) / 2, 0);

                    if (mListener != null) {
                        mListener.OnTransform(this);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                ptrID1 = INVALID_POINTER_ID;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                ptrID2 = INVALID_POINTER_ID;
                mListener.OnTransformEnd(this);
                break;
            case MotionEvent.ACTION_CANCEL:
                ptrID1 = INVALID_POINTER_ID;
                ptrID2 = INVALID_POINTER_ID;
                break;
        }
        return true;
    }

    private float scaleBetweenLines(float fX, float fY, float sX, float sY, float nfX, float nfY, float nsX, float nsY) {
        float pointerDistance1 = (float) Math.sqrt((fX - sX) * (fX - sX) + (fY - sY) * (fY - sY));
        float pointerDistance2 = (float) Math.sqrt((nfX - nsX) * (nfX - nsX) + (nfY - nsY) * (nfY - nsY));
        return pointerDistance2 / pointerDistance1;
    }

    private float angleBetweenLines(float fX, float fY, float sX, float sY, float nfX, float nfY, float nsX, float nsY) {
        float angle1 = (float) Math.atan2((fY - sY), (fX - sX));
        float angle2 = (float) Math.atan2((nfY - nsY), (nfX - nsX));
        float angle = ((float) Math.toDegrees(angle1 - angle2)) % 360;
        if (angle < -180.f) angle += 360.0f;
        if (angle > 180.f) angle -= 360.0f;
        return angle;
    }

    public Vector3 getTranslation() {
        return translation;
    }

    public interface OnMapTransformationGestureListener {
        void OnTransform(MapTransformationGestureDetector detector);

        void OnTransformEnd(MapTransformationGestureDetector detector);
    }
}