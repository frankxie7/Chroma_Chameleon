package chroma;

import chroma.controller.GameplayController;
import chroma.controller.LevelSelector;
import chroma.controller.LoadingMode;
import chroma.controller.MenuMode;
import chroma.model.Level;
import com.badlogic.gdx.*;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import edu.cornell.gdiac.util.*;
import edu.cornell.gdiac.assets.*;
import edu.cornell.gdiac.graphics.*;
import java.awt.Menu;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms.
 * Entry point for the game. It loads assets (with LoadingMode), then transitions
 * to one or more GameplayControllers.
 */
public class ChromaRoot extends Game implements ScreenListener {
    /** AssetManager to load game assets (textures, sounds, etc.) */
    AssetDirectory directory;

    /** The spritebatch to draw the screen (VIEW CLASS) */
    private SpriteBatch batch;

    /** Scene for the asset loading screen (if still used) */
    private LoadingMode loading;

    /** Scene for level selecting screen */
    private MenuMode selecting;

    /** LevelSelector for backend logic */
    private LevelSelector levelSelector;

    /** Current index of the active gameplay screen (if you plan to have more than one) */
    private int current;

    /** Array of gameplay controllers (or could be just one) */
    private GameplayController[] controllers;

//    private AssetManager assets;
    /**
     * Creates a new game from the configuration settings.
     *
     * This method configures the asset manager, but does not load any assets
     * or assign any screen.
     */
    public ChromaRoot() { }

    /**
     * Called when the Application is first created.
     *
     * Immediately loads assets for the loading screen, preparing the loader for
     * other assets. Then sets the loading screen as the active screen.
     */
    @Override
    public void create() {
        batch = new SpriteBatch();

        loading = new LoadingMode("assets.json", batch, 1);
        loading.setScreenListener(this);
        setScreen(loading);
    }

    /**
     * Disposes of all resources on application shutdown.
     */
    public void dispose() {
        // Properly dispose the current screen
        setScreen(null);

        // Dispose the loading screen
        if (loading != null) {
            loading.dispose();
            loading = null;
        }

        // Dispose the selecting screen
        if (selecting != null) {
            selecting.dispose();
            selecting = null;
        }

        // Dispose gameplay controllers
        if (controllers != null) {
            for (int ii = 0; ii < controllers.length; ii++) {
                controllers[ii].dispose();
            }
            controllers = null;
        }

        // Dispose the sprite batch
        batch.dispose();
        batch = null;

        // Unload all resources from the directory
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
     */
    public void resize(int width, int height) {
        // Pass size changes down to the loading screen, if active
        if (loading != null) {
            loading.resize(width, height);
        }

        if (selecting != null) {
            selecting.resize(width, height);
        }

        // Pass size changes to each gameplay controller
        if (controllers != null) {
            for (int ii = 0; ii < controllers.length; ii++) {
                controllers[ii].resize(width, height);
            }
        }
    }

    /**
     * Responds to a request from a child screen via ScreenListener.
     *
     * This typically handles:
     *  - Finishing loading (switch from loading to game)
     *  - Gameplay exit codes (NEXT level, PREV level, QUIT, etc.)
     *
     * @param screen   The screen requesting to exit
     * @param exitCode The state of the screen upon exit
     */
    public void exitScreen(Screen screen, int exitCode) {
        // Transition from loading → menu
        if (screen == loading) {
            directory = loading.getAssets();
            controllers = loading.getControllers();
            loading.dispose();
            loading = null;
            selecting = new MenuMode("assets.json", batch);
            selecting.setScreenListener(this);
            setScreen(selecting);

        } else if (screen == selecting) {
            selecting.dispose();
            selecting = null;

            current = exitCode;
            controllers[current-1].reset();
            setScreen(controllers[current-1]);

            // Handling transitions inside gameplay
        } else if (exitCode == GameplayController.EXIT_NEXT) {
//            levelSelector.nextLevel();
//            controllers[current].reset();
            current = Math.min(current + 1, controllers.length);
            controllers[current - 1].reset();
            setScreen(controllers[current-1]);

        } else if (exitCode == GameplayController.EXIT_PREV) {
//            levelSelector.prevLevel();
//            controllers[current].reset();
            current = Math.max(current - 1, 1);
            controllers[current-1].reset();
            setScreen(controllers[current-1]);

        } else if (exitCode == GameplayController.EXIT_QUIT) {
            // Quit the main application
            controllers[current].reset();
            selecting = new MenuMode("assets.json", batch);
            selecting.setScreenListener(this);
            setScreen(selecting);

        } else if (exitCode == GameplayController.EXIT_MAP) {
            //Transition from gameplay to menu
//            controllers = null;
            selecting = new MenuMode("assets.json", batch);
            selecting.setScreenListener(this);
            setScreen(selecting);
        }
    }
}
