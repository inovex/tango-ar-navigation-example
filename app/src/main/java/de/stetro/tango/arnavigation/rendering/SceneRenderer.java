package de.stetro.tango.arnavigation.rendering;


import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.MotionEvent;

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;

import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.renderer.RajawaliRenderer;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

import de.stetro.tango.arnavigation.data.PathFinder;
import de.stetro.tango.arnavigation.data.QuadTree;


public class SceneRenderer extends RajawaliRenderer {
    public static final int QUAD_TREE_START = -80;
    public static final int QUAD_TREE_RANGE = 160;
    private static final String TAG = SceneRenderer.class.getSimpleName();
    private final QuadTree data;
    // Rajawali texture used to render the Tango color camera
    private ATexture mTangoCameraTexture;
    // Keeps track of whether the scene camera has been configured
    private boolean mSceneCameraConfigured;
    private List<Cube> cubes = new ArrayList<>();
    private Material red;
    private Pose newCubePose;

    private FloorPlan floorPlan;
    private Pose startPoint;
    private Pose endPoint;
    private List<Cube> pathCubes = new ArrayList<>();
    private boolean fillPath = false;
    private Material blue;
    private boolean renderVirtualObjects;

    public SceneRenderer(Context context) {
        super(context);
        data = new QuadTree(new Vector2(QUAD_TREE_START, QUAD_TREE_START), QUAD_TREE_RANGE, 8);
    }

    @Override
    protected void initScene() {
        // Create a quad covering the whole background and assign a texture to it where the
        // Tango color camera contents will be rendered.
        ScreenQuad backgroundQuad = new ScreenQuad();
        Material tangoCameraMaterial = new Material();
        tangoCameraMaterial.setColorInfluence(0);
        // We need to use Rajawali's {@code StreamingTexture} since it sets up the texture
        // for GL_TEXTURE_EXTERNAL_OES rendering
        mTangoCameraTexture =
                new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);
        try {
            tangoCameraMaterial.addTexture(mTangoCameraTexture);
            backgroundQuad.setMaterial(tangoCameraMaterial);
        } catch (ATexture.TextureException e) {
            Log.e(TAG, "Exception creating texture for RGB camera contents", e);
        }
        getCurrentScene().addChildAt(backgroundQuad, 0);

        // Add a directional light in an arbitrary direction.
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);


        red = new Material();
        red.setColor(Color.RED);
        blue = new Material();
        blue.setColor(Color.BLUE);

        floorPlan = new FloorPlan(data);
        getCurrentScene().addChild(floorPlan);
        floorPlan.setVisible(renderVirtualObjects);

    }

    /**
     * Update the scene camera based on the provided pose in Tango start of service frame.
     * The device pose should match the pose of the device at the time the last rendered RGB
     * frame, which can be retrieved with this.getTimestamp();
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData devicePose, DeviceExtrinsics extrinsics) {
        Pose cameraPose = ScenePoseCalculator.toOpenGlCameraPose(devicePose, extrinsics);
        getCurrentCamera().setRotation(cameraPose.getOrientation());
        getCurrentCamera().setPosition(cameraPose.getPosition());
        floorPlan.setTrajectoryPosition(cameraPose.getPosition());
    }

    /**
     * It returns the ID currently assigned to the texture where the Tango color camera contents
     * should be rendered.
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public int getTextureId() {
        return mTangoCameraTexture == null ? -1 : mTangoCameraTexture.getTextureId();
    }

    /**
     * We need to override this method to mark the camera for re-configuration (set proper
     * projection matrix) since it will be reset by Rajawali on surface changes.
     */
    @Override
    public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height) {
        super.onRenderSurfaceSizeChanged(gl, width, height);
        mSceneCameraConfigured = false;
    }

    public boolean isSceneCameraConfigured() {
        return mSceneCameraConfigured;
    }

    /**
     * Sets the projection matrix for the scene camera to match the parameters of the color camera,
     * provided by the {@code TangoCameraIntrinsics}.
     */
    public void setProjectionMatrix(TangoCameraIntrinsics intrinsics) {
        Matrix4 projectionMatrix = ScenePoseCalculator.calculateProjectionMatrix(
                intrinsics.width, intrinsics.height,
                intrinsics.fx, intrinsics.fy, intrinsics.cx, intrinsics.cy);
        getCurrentCamera().setProjectionMatrix(projectionMatrix);
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xOffsetStep, float yOffsetStep,
                                 int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }

    @Override
    protected void onRender(long ellapsedRealtime, double deltaTime) {
        super.onRender(ellapsedRealtime, deltaTime);
        // add a new annotation cube to scene graph if available
        if (newCubePose != null) {
            Cube cube = new Cube(0.2f);
            cube.setMaterial(red);
            cube.setPosition(newCubePose.getPosition());
            cube.setOrientation(newCubePose.getOrientation());
            cubes.add(cube);
            getCurrentScene().addChild(cube);
            newCubePose = null;
        }

        // add routing cubes to scene graph if available
        if (fillPath) {
            for (Cube pathCube : pathCubes) {
                getCurrentScene().removeChild(pathCube);
            }
            pathCubes.clear();
            PathFinder finder = new PathFinder(floorPlan.getData());
            try {
                List<Vector2> path = finder.findPathBetween(startPoint.getPosition(), endPoint.getPosition());
                for (Vector2 vector2 : path) {
                    Cube cube = new Cube(0.1f);
                    cube.setMaterial(blue);
                    cube.setPosition(new Vector3(vector2.getX(), -1.2, vector2.getY()));
                    getCurrentScene().addChild(cube);
                    pathCubes.add(cube);
                }
            } catch (Exception e) {
                Log.e(TAG, "onRender: " + e.getMessage(), e);
            } finally {
                fillPath = false;
            }
        }
    }

    public void setStartPoint(TangoPoseData currentPose, DeviceExtrinsics extrinsics) {
        startPoint = ScenePoseCalculator.toOpenGlCameraPose(currentPose, extrinsics);
        floorPlan.addPoint(startPoint.getPosition());
        if (startPoint != null && endPoint != null) {
            fillPath = true;
        }
    }

    public void setEndPoint(TangoPoseData currentPose, DeviceExtrinsics extrinsics) {
        endPoint = ScenePoseCalculator.toOpenGlCameraPose(currentPose, extrinsics);
        floorPlan.addPoint(endPoint.getPosition());
        if (startPoint != null && endPoint != null) {
            fillPath = true;
        }
    }

    public QuadTree getFloorPlanData() {
        return data;
    }

    public void renderVirtualObjects(boolean renderObjects) {
        renderVirtualObjects = renderObjects;
        if (this.floorPlan != null)
            this.floorPlan.setVisible(renderObjects);
    }
}
