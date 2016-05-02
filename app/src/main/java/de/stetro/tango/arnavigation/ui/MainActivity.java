package de.stetro.tango.arnavigation.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.atap.tango.ux.TangoUx;
import com.google.atap.tango.ux.TangoUxLayout;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.projecttango.rajawali.DeviceExtrinsics;

import org.rajawali3d.surface.RajawaliSurfaceView;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.stetro.tango.arnavigation.R;
import de.stetro.tango.arnavigation.rendering.SceneRenderer;
import de.stetro.tango.arnavigation.ui.util.ScenePreFrameCallbackAdapter;
import de.stetro.tango.arnavigation.ui.views.MapView;


public class MainActivity extends AppCompatActivity implements Tango.OnTangoUpdateListener {

    // frame pairs for adf based ar pose tracking
    public static final TangoCoordinateFramePair SOS_T_DEVICE_FRAME_PAIR =
            new TangoCoordinateFramePair(
                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                    TangoPoseData.COORDINATE_FRAME_DEVICE);
    public static final TangoCoordinateFramePair DEVICE_T_PREVIOUS_FRAME_PAIR =
            new TangoCoordinateFramePair(
                    TangoPoseData.COORDINATE_FRAME_PREVIOUS_DEVICE_POSE,
                    TangoPoseData.COORDINATE_FRAME_DEVICE);

    // This changes the Camera Texture and Intrinsics
    protected static final int ACTIVE_CAMERA_INTRINSICS = TangoCameraIntrinsics.TANGO_CAMERA_COLOR;
    protected static final int INVALID_TEXTURE_ID = -1;
    protected AtomicBoolean tangoIsConnected = new AtomicBoolean(false);
    protected AtomicBoolean tangoFrameIsAvailable = new AtomicBoolean(false);

    protected Tango tango;
    protected TangoUx tangoUx;
    protected TangoCameraIntrinsics intrinsics;
    protected DeviceExtrinsics extrinsics;

    protected int connectedTextureId;
    protected double rgbFrameTimestamp;
    protected double cameraPoseTimestamp;

    protected SceneRenderer renderer;

    @Bind(R.id.gl_main_surface_view)
    RajawaliSurfaceView mainSurfaceView;
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.tango_ux_layout)
    TangoUxLayout uxLayout;
    @Bind(R.id.map_view)
    MapView mapView;


    /**
     * get the extrinsics transformations for the color and depth camera
     * and also the relative transformation to the device.
     *
     * @param tango API interface
     * @return the device extrinsics of the color camera
     */
    private static DeviceExtrinsics setupExtrinsics(Tango tango) {
        // Create camera to IMU transform.
        TangoCoordinateFramePair framePair = new TangoCoordinateFramePair();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR;
        TangoPoseData imuToRgbPose = tango.getPoseAtTime(0.0, framePair);

        // Create device to IMU transform.
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
        TangoPoseData imuToDevicePose = tango.getPoseAtTime(0.0, framePair);

        // Create depth camera to IMU transform.
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH;
        TangoPoseData imuToDepthPose = tango.getPoseAtTime(0.0, framePair);

        return new DeviceExtrinsics(imuToDevicePose, imuToRgbPose, imuToDepthPose);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tango = new Tango(this);
        tangoUx = new TangoUx(this);
        renderer = new SceneRenderer(this);

        setContentView(R.layout.main_layout);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);
        tangoUx.setLayout(uxLayout);
        renderer.renderVirtualObjects(true);
        mainSurfaceView.setSurfaceRenderer(renderer);
        mainSurfaceView.setZOrderOnTop(false);
        mapView.setFloorPlanData(renderer.getFloorPlanData());
    }

    @Override
    protected void onResume() {
        super.onResume();
        synchronized (this) {
            if (tangoIsConnected.compareAndSet(false, true)) {
                try {
                    connectTango();
                    connectRenderer();
                } catch (TangoOutOfDateException e) {
                    message(R.string.exception_out_of_date);
                }
            }
        }
    }

    private void message(final int message_resource) {
        Toast.makeText(this, message_resource, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFrameAvailable(int cameraId) {
        if (cameraId == ACTIVE_CAMERA_INTRINSICS) {
            tangoFrameIsAvailable.set(true);
            mainSurfaceView.requestRender();
        }
    }

    @Override
    public void onTangoEvent(TangoEvent event) {
        if (tangoUx != null) {
            tangoUx.updateTangoEvent(event);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.set_start_point:
                renderer.setStartPoint(getCurrentPose(), extrinsics);
                break;
            case R.id.set_end_point:
                renderer.setEndPoint(getCurrentPose(), extrinsics
                );
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void setupCameraProperties(Tango tango) {
        extrinsics = setupExtrinsics(tango);
        intrinsics = tango.getCameraIntrinsics(ACTIVE_CAMERA_INTRINSICS);
    }

    protected void connectTango() {
        TangoUx.StartParams params = new TangoUx.StartParams();
        tangoUx.start(params);
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        tango.connect(config);
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<>();
        framePairs.add(SOS_T_DEVICE_FRAME_PAIR);
        framePairs.add(DEVICE_T_PREVIOUS_FRAME_PAIR);
        tango.connectListener(framePairs, this);
        setupCameraProperties(tango);
    }

    public TangoPoseData getCurrentPose() {
        return tango.getPoseAtTime(rgbFrameTimestamp, SOS_T_DEVICE_FRAME_PAIR);
    }


    protected void connectRenderer() {
        renderer.getCurrentScene().registerFrameCallback(new ScenePreFrameCallbackAdapter() {
            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                synchronized (MainActivity.this) {
                    if (!tangoIsConnected.get()) {
                        return;
                    }
                    if (!renderer.isSceneCameraConfigured()) {
                        renderer.setProjectionMatrix(intrinsics);
                    }
                    if (connectedTextureId != renderer.getTextureId()) {
                        tango.connectTextureId(ACTIVE_CAMERA_INTRINSICS, renderer.getTextureId());
                        connectedTextureId = renderer.getTextureId();
                    }
                    if (tangoFrameIsAvailable.compareAndSet(true, false)) {
                        rgbFrameTimestamp = tango.updateTexture(ACTIVE_CAMERA_INTRINSICS);
                    }
                    if (rgbFrameTimestamp > cameraPoseTimestamp) {
                        TangoPoseData currentPose = getCurrentPose();
                        if (currentPose != null && currentPose.statusCode == TangoPoseData.POSE_VALID) {
                            renderer.updateRenderCameraPose(currentPose, extrinsics);
                            cameraPoseTimestamp = currentPose.timestamp;
                        }
                    }
                }
            }
        });
    }


    @Override
    public void onPoseAvailable(TangoPoseData pose) {
        if (tangoUx != null) {
            tangoUx.updatePoseStatus(pose.statusCode);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        synchronized (this) {
            if (tangoIsConnected.compareAndSet(true, false)) {
                renderer.getCurrentScene().clearFrameCallbacks();
                tango.disconnectCamera(ACTIVE_CAMERA_INTRINSICS);
                connectedTextureId = INVALID_TEXTURE_ID;
                tango.disconnect();
                tangoUx.stop();
            }
        }
    }

    @Override
    public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
        if (tangoUx != null) {
            tangoUx.updateXyzCount(xyzIj.xyzCount);
        }
    }

}
