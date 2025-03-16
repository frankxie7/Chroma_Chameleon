package edu.cornell.cis3152.shipdemo;

import com.badlogic.gdx.*;
import edu.cornell.gdiac.util.*;
import edu.cornell.gdiac.assets.*;
import edu.cornell.gdiac.graphics.*;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms.
 *
 * TODO
 * */
public class GDXRoot extends Game implements ScreenListener {
    /** AssetManager to load game assets (textures, sounds, etc.) */
    AssetDirectory directory;
    /** The spritebatch to draw the screen (VIEW CLASS) */
    private SpriteBatch batch;
    /** Scene for the asset loading screen (CONTROLLER CLASS) */
    private LoadingScene loading;
    /** Player mode for the the game proper (CONTROLLER CLASS) */
    private int current;
    /** List of all WorldControllers */
    private PlatformScene[] controllers;

    /**
     * Creates a new game from the configuration settings.
     *
     * This method configures the asset manager, but does not load any assets
     * or assign any screen.
     */
    public GDXRoot() { }

    /**
     * Called when the Application is first created.
     *
     * This is method immediately loads assets for the loading screen, and
     * prepares the asynchronous loader for all other assets.
     */
    public void create() {
        batch  = new SpriteBatch();

        // Create the loading scene
        loading = new LoadingScene("assets.json",batch,1);
        loading.setScreenListener(this);
        setScreen(loading);
    }

    /**
     * Called when the Application is destroyed.
     *
     * This is preceded by a call to pause().
     */
    public void dispose() {
        // Call dispose on our children
        setScreen(null);
        if (loading != null) {
            loading.dispose();
            loading = null;
        }
        if (controllers != null) {
            for(int ii = 0; ii < controllers.length; ii++) {
                controllers[ii].dispose();
            }
            controllers = null;
        }

        batch.dispose();
        batch = null;

        // Unload all of the resources
        if (directory != null) {
            directory.unloadAssets();
            directory.dispose();
            directory = null;
        }
        super.dispose();
    }

    /**
     * Called when the Application is resized.
     *
     * This can happen at any point during a non-paused state but will never
     * happen before a call to create().
     *
     * @param width  The new width in pixels
     * @param height The new height in pixels
     */
    public void resize(int width, int height) {
        if (loading != null) {
            loading.resize(width,height);
        }
        if (controllers != null) {
            for(int ii = 0; ii < controllers.length; ii++) {
                controllers[ii].resize(width,height);
            }
        }
    }

    /**
     * Responds to a request from a child scene.
     *
     * Typically this is used to have a scene exit its player mode. The value
     * exitCode can be also used to implement menu options.
     *
     * @param screen   The screen requesting to exit
     * @param exitCode The state of the screen upon exit
     */
    public void exitScreen(Screen screen, int exitCode) {
        if (screen == loading) {
            directory = loading.getAssets();
            loading.dispose();
            loading = null;

            // Initialize the three game worlds
            controllers = new PlatformScene[1];
            controllers[0] = new PlatformScene(directory);

            for(int ii = 0; ii < controllers.length; ii++) {
                controllers[ii].setScreenListener(this);
                controllers[ii].setSpriteBatch(batch);
            }

            current = 0;
            controllers[current].reset();
            setScreen(controllers[current]);
        } else if (exitCode == PhysicsScene.EXIT_NEXT) {
            current = (current+1) % controllers.length;
            controllers[current].reset();
            setScreen(controllers[current]);
        } else if (exitCode == PhysicsScene.EXIT_PREV) {
            current = (current+controllers.length-1) % controllers.length;
            controllers[current].reset();
            setScreen(controllers[current]);
        } else if (exitCode == PhysicsScene.EXIT_QUIT) {
            // We quit the main application
            Gdx.app.exit();
        }
    }

}
