package de.stetro.tango.arnavigation.ui.util;


import org.rajawali3d.scene.ASceneFrameCallback;

public abstract class ScenePreFrameCallbackAdapter extends ASceneFrameCallback {


    @Override
    public void onPreDraw(long sceneTime, double deltaTime) {

    }

    @Override
    public void onPostFrame(long sceneTime, double deltaTime) {

    }

    @Override
    public boolean callPreFrame() {
        return true;
    }

}
