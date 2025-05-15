/*
 * LoadingMode.java
 *
 * Asset loading is a really tricky problem. If you have a lot of sound or
 * images, it can take a long time to decompress them and load them into memory.
 * If you just have code at the start to load all your assets, your game will
 * look like it is hung at the start.
 *
 * The alternative is asynchronous asset loading. In asynchronous loading, you
 * load a little bit of the assets at a time, but still animate the game while
 * you are loading. This way the player knows the game is not hung, even though
 * he or she cannot do anything until loading is complete. You know those
 * loading screens with the inane tips that want to be helpful? That is
 * asynchronous loading.
 *
 * This player mode provides a basic loading screen. While you could adapt it
 * for between level loading, it is currently designed for loading all assets
 * at the start of the game.
 *
 * @author: Walker M. White
 * @date: 11/21/2024
 */
package chroma.controller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import edu.cornell.gdiac.assets.AssetDirectory;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.util.ScreenListener;

/**
 * Class that provides a loading screen for the state of the game.
 *
 * This is a fairly generic loading screen that shows the GDIAC logo and a
 * progress bar. Once all assets are loaded, the progress bar is replaced
 * by a play button. You are free to adopt this to your needs.
 */
public class LoadingMode implements Screen, InputProcessor {
    /** Default budget for asset loader (do nothing but load 60 fps) */
    private static int DEFAULT_BUDGET = 15;

    // There are TWO asset managers.
    // One to load the loading screen. The other to load the assets
    /** Internal assets for this loading screen */
    private AssetDirectory internal;
    /** The actual assets to be loaded */
    private AssetDirectory assets;

    /** The drawing camera for this scene */
    private OrthographicCamera camera;
    /** Reference to sprite batch created by the root */
    private SpriteBatch batch;
    /** Affine transform for displaying images */
    private Affine2 affine;
    /** Listener that will update the player mode when we are done */
    private ScreenListener listener;

    /** The width of this scene */
    private int width;
    /** The height of this scene */
    private int height;

    /** The constants for arranging images on the screen */
    JsonValue constants;

    /** Scaling factor for when the student changes the resolution. */
    private float scale;
    /** Current progress (0 to 1) of the asset manager */
    private float progress;
    /** The current state of the play button */
    private int   pressState;
    /** The amount of time to devote to loading assets (as opposed to on screen hints, etc.) */
    private int   budget;

    /** Whether or not this player mode is still active */
    private boolean active;

    /**
     * Returns the budget for the asset loader.
     *
     * The budget is the number of milliseconds to spend loading assets each
     * animation frame. This allows you to do something other than load assets.
     * An animation frame is ~16 milliseconds. So if the budget is 10, you have
     * 6 milliseconds to do something else. This is how game companies animate
     * their loading screens.
     *
     * @return the budget in milliseconds
     */
    public int getBudget() {
        return budget;
    }
    // LoadingMode.java  (add near the other fields)
    private boolean filtersSet = false;

    /**
     * Sets the budget for the asset loader.
     *
     * The budget is the number of milliseconds to spend loading assets each
     * animation frame. This allows you to do something other than load assets.
     * An animation frame is ~16 milliseconds. So if the budget is 10, you have
     * 6 milliseconds to do something else. This is how game companies animate
     * their loading screens.
     *
     * @param millis the budget in milliseconds
     */
    public void setBudget(int millis) {
        budget = millis;
    }

    /**
     * Returns true if all assets are loaded and the player is ready to go.
     *
     * @return true if the player is ready to go
     */
    public boolean isReady() {
        return pressState == 2;
    }

    /**
     * Returns the asset directory produced by this loading screen
     *
     * This asset loader is NOT owned by this loading scene, so it persists even
     * after the scene is disposed. It is your responsbility to unload the
     * assets in this directory.
     *
     * @return the asset directory produced by this loading screen
     */
    public AssetDirectory getAssets() {
        return assets;
    }

    /**
     * Creates a LoadingMode with the default budget, size and position.
     *
     * @param file      The asset directory to load in the background
     * @param batch     The sprite batch to draw to
     */
    public LoadingMode(String file, SpriteBatch batch) {
        this(file, batch, DEFAULT_BUDGET);
    }

    /**
     * Creates a LoadingMode with the default size and position.
     *
     * The budget is the number of milliseconds to spend loading assets each animation
     * frame. This allows you to do something other than load assets. An animation
     * frame is ~16 milliseconds. So if the budget is 10, you have 6 milliseconds to
     * do something else. This is how game companies animate their loading screens.
     *
     * @param file      The asset directory to load in the background
     * @param canvas     The game canvas to draw to
     * @param millis The loading budget in milliseconds
     */
    public LoadingMode(String file, SpriteBatch batch, int millis) {
        this.batch  = batch;
        budget = millis;

        // We need these files loaded immediately
        internal = new AssetDirectory( "loading/boot.json" );
        internal.loadAssets();
        internal.finishLoading();

        constants = internal.getEntry( "constants", JsonValue.class );
        resize(Gdx.graphics.getWidth(),Gdx.graphics.getHeight());

        // No progress so far.
        progress = 0;
        pressState = 0;

        affine = new Affine2();
        Gdx.input.setInputProcessor( this );

        // Start loading the REAL assets
        assets = new AssetDirectory( file );
        assets.loadAssets();
        active = true;
    }

    /**
     * Called when this screen should release all resources.
     */
    public void dispose() {
        internal.unloadAssets();
        internal.dispose();
    }

    /**
     * Updates the status of this scene
     *
     * We prefer to separate update and draw from one another as separate
     * methods, instead of using the single render() method that LibGDX does.
     * We will talk about why we prefer this in lecture.
     *
     * @param delta Number of seconds since last animation frame
     */
    private void update(float delta) {
        if (progress < 1.0f) {
            assets.update(budget);
            this.progress = assets.getProgress();
            if (progress >= 1.0f) {
                this.progress = 1.0f;
            }
        }
        if (progress >= 1.0f && !filtersSet) {
            // Make every texture crisp once and for all
            com.badlogic.gdx.utils.Array<Texture> textures = new com.badlogic.gdx.utils.Array<>();
            assets.getAll(Texture.class, textures);
            for (Texture tex : textures) {
                tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            }
            filtersSet = true;   // ensure we only do this once
        }
    }

    /**
     * Draws the status of this player mode.
     *
     * We prefer to separate update and draw from one another as separate
     * methods, instead of using the single render() method that LibGDX does.
     * We will talk about why we prefer this in lecture.
     */
    private void draw() {
        // Cornell colors
        ScreenUtils.clear( 0.051f, 0.173f, 0.212f, 1f  );

        batch.begin(camera);
        batch.setColor( Color.WHITE );

        // Height lock the logo
        Texture texture = internal.getEntry( "splash", Texture.class );
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        batch.draw(texture,(width-height)/2, 0, height, height);

        if (progress < 1.0f) {
            drawProgress();
        } else {
            float cx = width/2;
            float cy = (int)(constants.getFloat( "bar.height" )*height);
            float s = constants.getFloat("button.scale")*scale;
            Color tint = (pressState == 1 ? Color.GRAY : Color.WHITE);
            texture = internal.getEntry("play",Texture.class);
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

            SpriteBatch.computeTransform( affine, texture.getWidth() / 2, texture.getHeight() / 2,
                cx, cy, 0, s, s );

            batch.setColor( tint );
            batch.draw( texture, affine );
        }
        batch.end();
    }

    /**
     * Updates the progress bar according to loading progress
     *
     * The progress bar is composed of parts: two rounded caps on the end, and
     * a rectangle in a middle. We adjust the size of the rectangle in the
     * middle to represent the amount of progress.
     */
    private void drawProgress() {
        float w = (int)(constants.getFloat( "bar.width" )*width);
        float cx = width/2;
        float cy = (int)(constants.getFloat( "bar.height" )*height);
        TextureRegion region1, region2, region3;

        // "3-patch" the background
        batch.setColor( Color.WHITE );
        region1 = internal.getEntry( "progress.backleft", TextureRegion.class );
        batch.draw(region1,cx-w/2, cy, scale*region1.getRegionWidth(), scale*region1.getRegionHeight());

        region2 = internal.getEntry( "progress.backright", TextureRegion.class );
        batch.draw(region2,cx+w/2-scale*region2.getRegionWidth(), cy,
            scale*region2.getRegionWidth(), scale*region2.getRegionHeight());

        region3 = internal.getEntry( "progress.background", TextureRegion.class );
        batch.draw(region3, cx-w/2+scale*region1.getRegionWidth(), cy,
            w-scale*(region2.getRegionWidth()+region1.getRegionWidth()),
            scale*region3.getRegionHeight());

        // "3-patch" the foreground
        region1 = internal.getEntry( "progress.foreleft", TextureRegion.class );
        batch.draw(region1,cx-w/2, cy,scale*region1.getRegionWidth(), scale*region1.getRegionHeight());

        if (progress > 0) {
            region2 = internal.getEntry( "progress.foreright", TextureRegion.class );
            float span = progress*(w-scale*(region1.getRegionWidth()+region2.getRegionWidth()));

            batch.draw( region2,cx-w/2+scale*region1.getRegionWidth()+span, cy,
                scale*region2.getRegionWidth(), scale*region2.getRegionHeight());

            region3 = internal.getEntry( "progress.foreground", TextureRegion.class );
            batch.draw(region3, cx-w/2+scale*region1.getRegionWidth(), cy,
                span, scale*region3.getRegionHeight());
        } else {
            region2 = internal.getEntry( "progress.foreright", TextureRegion.class );
            batch.draw(region2, cx-w/2+scale*region1.getRegionWidth(), cy,
                scale*region2.getRegionWidth(), scale*region2.getRegionHeight());
        }

    }

    // ADDITIONAL SCREEN METHODS
    /**
     * Called when the Screen should render itself.
     *
     * We defer to the other methods update() and draw(). However, it is VERY
     * important that we only quit AFTER a draw.
     *
     * @param delta Number of seconds since last animation frame
     */
    public void render(float delta) {
        if (active) {
            update(delta);
            draw();

            // We are are ready, notify our listener
            if (isReady() && listener != null) {
                listener.exitScreen(this, 0);
            }
        }
    }

    /**
     * Called when the Screen is resized.
     *
     * This can happen at any point during a non-paused state but will never
     * happen before a call to show().
     *
     * @param width  The new width in pixels
     * @param height The new height in pixels
     */
    public void resize(int width, int height) {
        // Compute the drawing scale
        scale = ((float)height)/constants.getFloat( "height" );

        this.width  = width;
        this.height = height;
        if (camera == null) {
            camera = new OrthographicCamera(width,height);
        } else {
            camera.setToOrtho( false, width, height  );
        }
    }

    /**
     * Called when the Screen is paused.
     *
     * This is usually when it's not active or visible on screen. An Application
     * is also paused before it is destroyed.
     */
    public void pause() {
        // TODO Auto-generated method stub

    }

    /**
     * Called when the Screen is resumed from a paused state.
     *
     * This is usually when it regains focus.
     */
    public void resume() {
        // TODO Auto-generated method stub

    }

    /**
     * Called when this screen becomes the current screen for a Game.
     */
    public void show() {
        // Useless if called in outside animation loop
        active = true;
    }

    /**
     * Called when this screen is no longer the current screen for a Game.
     */
    public void hide() {
        // Useless if called in outside animation loop
        active = false;
    }

    /**
     * Sets the ScreenListener for this mode
     *
     * The ScreenListener will respond to requests to quit.
     */
    public void setScreenListener(ScreenListener listener) {
        this.listener = listener;
    }

    // PROCESSING PLAYER INPUT
    /**
     * Called when the screen was touched or a mouse button was pressed.
     *
     * This method checks to see if the play button is available and if the click
     * is in the bounds of the play button. If so, it signals the that the button
     * has been pressed and is currently down. Any mouse button is accepted.
     *
     * @param screenX the x-coordinate of the mouse on the screen
     * @param screenY the y-coordinate of the mouse on the screen
     * @param pointer the button or touch finger number
     * @return whether to hand the event to other listeners.
     */
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (progress < 1.0f || pressState == 2) {
            return true;
        }

        // Flip to match graphics coordinates
        screenY = height-screenY;

        // Play button is a circle.
        float cx = width/2;
        float cy = (int)(constants.getFloat( "bar.height" )*height);
        float s = constants.getFloat( "button.scale" )*scale;
        float radius = s*internal.getEntry("play",Texture.class).getWidth()/2.0f;
        float dist = (screenX-cx)*(screenX-cx)+(screenY-cy)*(screenY-cy);
        if (dist < radius*radius) {
            pressState = 1;
        }
        return false;
    }

    /**
     * Called when a finger was lifted or a mouse button was released.
     *
     * This method checks to see if the play button is currently pressed down.
     * If so, it signals the that the player is ready to go.
     *
     * @param screenX the x-coordinate of the mouse on the screen
     * @param screenY the y-coordinate of the mouse on the screen
     * @param pointer the button or touch finger number
     * @return whether to hand the event to other listeners.
     */
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (pressState == 1) {
            pressState = 2;
            return false;
        }
        return true;
    }

    // UNSUPPORTED METHODS FROM InputProcessor

    /**
     * Called when a key is pressed (UNSUPPORTED)
     *
     * @param keycode the key pressed
     * @return whether to hand the event to other listeners.
     */
    public boolean keyDown(int keycode) {
        return true;
    }

    /**
     * Called when a key is typed (UNSUPPORTED)
     *
     * @param keycode the key typed
     * @return whether to hand the event to other listeners.
     */
    public boolean keyTyped(char character) {
        return true;
    }

    /**
     * Called when a key is released (UNSUPPORTED)
     *
     * @param keycode the key released
     * @return whether to hand the event to other listeners.
     */
    public boolean keyUp(int keycode) {
        return true;
    }

    /**
     * Called when the mouse was moved without any buttons being pressed. (UNSUPPORTED)
     *
     * @param screenX the x-coordinate of the mouse on the screen
     * @param screenY the y-coordinate of the mouse on the screen
     * @return whether to hand the event to other listeners.
     */
    public boolean mouseMoved(int screenX, int screenY) {
        return true;
    }

    /**
     * Called when the mouse wheel was scrolled. (UNSUPPORTED)
     *
     * @param dx the amount of horizontal scroll
     * @param dy the amount of vertical scroll
     *
     * @return whether to hand the event to other listeners.
     */
    public boolean scrolled(float dx, float dy) {
        return true;
    }

    /**
     * Called when the touch gesture is cancelled (UNSUPPORTED)
     *
     * Reason may be from OS interruption to touch becoming a large surface such
     * as the user cheek. Relevant on Android and iOS only. The button parameter
     * will be Input.Buttons.LEFT on iOS.
     *
     * @param screenX the x-coordinate of the mouse on the screen
     * @param screenY the y-coordinate of the mouse on the screen
     * @param pointer the button or touch finger number
     * @param button  the button
     * @return whether to hand the event to other listeners.
     */
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return true;
    }

    /**
     * Called when the mouse or finger was dragged. (UNSUPPORTED)
     *
     * @param screenX the x-coordinate of the mouse on the screen
     * @param screenY the y-coordinate of the mouse on the screen
     * @param pointer the button or touch finger number
     * @return whether to hand the event to other listeners.
     */
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return true;
    }

}
