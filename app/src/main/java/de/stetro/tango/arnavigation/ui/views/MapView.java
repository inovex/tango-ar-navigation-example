package de.stetro.tango.arnavigation.ui.views;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.math.vector.Vector3;

import java.util.ArrayList;
import java.util.List;

import de.stetro.tango.arnavigation.data.QuadTree;
import de.stetro.tango.arnavigation.ui.util.MapTransformationGestureDetector;


public class MapView extends View implements View.OnTouchListener, MapTransformationGestureDetector.OnMapTransformationGestureListener, QuadTree.QuadTreeDataListener {
    private static final double RECT_SIZE_CONST = 5.0;
    private static final double MAP_SCALE_CONSTANT = 30.0;

    private final ArrayList<Vector3> points = new ArrayList<>();

    private Paint paint;
    private QuadTree floorPlanData;
    private MapTransformationGestureDetector mapTransformationGestureDetector;

    private Matrix4 activeTransformation = Matrix4.createTranslationMatrix(new Vector3());

    private Vector3 previousTranslation = new Vector3(0, 0, 0);
    private float previousScale = 1.0f;
    private float previousRotation = 0f;


    public MapView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public MapView(Context context) {
        super(context);
        init();
    }

    public MapView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        mapTransformationGestureDetector = new MapTransformationGestureDetector(this);
        setOnTouchListener(this);
        transformPoints();
    }

    private void transformPoints() {
        if (floorPlanData != null) {
            List<Vector2> filledPoints = floorPlanData.getFilledPoints();
            synchronized (points) {
                points.clear();
                for (Vector2 filledPoint : filledPoints) {
                    Vector3 v3 = new Vector3(filledPoint.getX(), filledPoint.getY(), 0);
                    v3.multiply(activeTransformation);
                    points.add(v3);
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.WHITE);
        synchronized (points) {
            for (Vector3 point : points) {
                drawRect(canvas, point.x, point.y);
            }
        }
    }

    private void drawRect(Canvas canvas, double x, double y) {
        canvas.drawRect(
                (int) (x * MAP_SCALE_CONSTANT),
                (int) (y * MAP_SCALE_CONSTANT),
                (int) (x * MAP_SCALE_CONSTANT + RECT_SIZE_CONST),
                (int) (y * MAP_SCALE_CONSTANT + RECT_SIZE_CONST), paint);
    }

    public void setFloorPlanData(QuadTree floorPlanData) {
        this.floorPlanData = floorPlanData;
        this.floorPlanData.setListener(this);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mapTransformationGestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    public void OnTransform(MapTransformationGestureDetector detector) {
        Vector3 translation = detector.getTranslation();
        translation.x = translation.x / MAP_SCALE_CONSTANT;
        translation.y = translation.y / MAP_SCALE_CONSTANT;
        translation = translation.clone().add(this.previousTranslation);
        activeTransformation = Matrix4.createRotationMatrix(Vector3.Axis.Z, detector.getAngle() + this.previousRotation);
        activeTransformation.translate(translation);
        activeTransformation.scale(detector.getScale() + this.previousScale - 1.0f);
        transformPoints();
        postInvalidate();
    }

    @Override
    public void OnTransformEnd(MapTransformationGestureDetector rotationDetector) {
        this.previousTranslation = rotationDetector.getTranslation().add(this.previousTranslation);
        this.previousRotation = rotationDetector.getAngle() + this.previousRotation;
        this.previousScale = rotationDetector.getScale() + this.previousScale - 1.0f;
    }

    @Override
    public void OnQuadTreeUpdate() {
        transformPoints();
        postInvalidate();
    }
}
