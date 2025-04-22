package chroma.controller;

import chroma.controller.LevelSelector;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import edu.cornell.gdiac.assets.AssetDirectory;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.util.ScreenListener;


public class MenuMode implements Screen, InputProcessor {
    private AssetDirectory directory;
    private SpriteBatch batch;

    private AssetDirectory internal;
    private OrthographicCamera camera;
    private Affine2 affine;
    private JsonValue constants;

    private int width, height;
    private float scale;
    private CircleBound[] bounds;

    private ScreenListener listener;

    /** Whether or not this player mode is still active */
    private boolean active;

    /** Current level number */
    private int currLevel;

    /** The current state of the button */
    private int pressState;

    /** Draw the outline for determining */
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;


    /** Internal class for creating a circular bound */
    private class CircleBound {
        public float x, y, radius;

        public CircleBound(float x, float y, float radius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
        }

        public boolean contains(float px, float py) {
            float dx = px - x;
            float dy = py - y;
            return dx * dx + dy * dy <= radius * radius;
        }
    }

    /**
     * Returns true if all assets are loaded and the player is ready to go.
     *
     * @return true if the player is ready to go
     */
    public boolean isReady() {
        return pressState == 2;
    }

    public MenuMode(String assetFile, SpriteBatch batch) {
        this.batch = batch;

        internal = new AssetDirectory( "menu/menu.json" );
        internal.loadAssets();
        internal.finishLoading();

        constants = internal.getEntry( "constants", JsonValue.class );
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        bounds = new CircleBound[constants.getInt("numButtons")];

        affine = new Affine2();
        Gdx.input.setInputProcessor(this);

        this.directory = new AssetDirectory(assetFile);
        directory.loadAssets();
        directory.finishLoading();

        active = true;

        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont(); // You can also load custom fonts if needed
        font.setColor(Color.BLACK); // Set to your desired color
        font.getData().setScale(4f); // Scale to fit the button nicely

    }

    /**
     * Called when this screen should release all resources.
     */
    public void dispose() {
        internal.unloadAssets();
        internal.dispose();
    }

    /**
     * Draws the status of this player mode.
     *
     * We prefer to separate update and draw from one another as separate
     * methods, instead of using the single render() method that LibGDX does.
     * We will talk about why we prefer this in lecture.
     */
    private void draw() {
        // Background colors
        ScreenUtils.clear( 0.051f, 0.173f, 0.212f, 1f );

        batch.begin(camera);
        batch.setColor( Color.WHITE );

        Texture initTexture = internal.getEntry("play",Texture.class);
        Texture splatter = internal.getEntry("buttons", Texture.class);
        int numCols = constants.getInt("numCols");
        int numRows = constants.getInt("numRows");
        int numButtons = constants.getInt("numButtons");
        float buttonScale = constants.getFloat("button.scale") ;
        float boundScale = constants.getFloat("bound.scale") ;

        float buttonSize = height * buttonScale;
        float radius = buttonSize * boundScale;
        float hSpacing = (width - numCols * buttonSize) / (numCols + 1); // horizontal spacing
        float vSpacing = (height - numRows * buttonSize) / (numRows + 1); // vertical spacing

        for (int i = 0; i < numButtons; i++) {
            int row = i / numCols;
            int col = i % numCols;

            float x = hSpacing + col * (buttonSize + hSpacing);
            float y = height - (vSpacing + (row + 1) * buttonSize + row * vSpacing);

            bounds[i] = new CircleBound(x + buttonSize / 2 , y + buttonSize / 2, radius);

//            Color tint = (i == currLevel-1 ? Color.GRAY : Color.WHITE);
//            batch.setColor( tint );

            Texture texture = i == currLevel - 1 ? splatter: initTexture;
            batch.draw(texture, x, y, buttonSize, buttonSize);

            // Draw the level number
            String levelText = String.valueOf(i + 1);
            GlyphLayout layout = new GlyphLayout(font, levelText);
            float textWidth = layout.width;
            float textHeight = layout.height;

            font.draw(batch, levelText,
                x + buttonSize / 2 - textWidth / 2,
                y + buttonSize / 2 + textHeight / 2); // Centered
        }

        batch.end();

        // Begin shape rendering for debug
//        shapeRenderer.setProjectionMatrix(camera.combined);
//        shapeRenderer.begin(ShapeRenderer.ShapeType.Line); // Line for outline
//        shapeRenderer.setColor(Color.YELLOW); // any debug color
//
//        for (int i = 0; i < bounds.length; i++) {
//            CircleBound cb = bounds[i];
//            shapeRenderer.circle(cb.x, cb.y, cb.radius);
//        }
//
//        shapeRenderer.end();
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
            draw();
        }

        // We are are ready, notify our listener
        if (isReady() && listener != null) {
            listener.exitScreen(this, currLevel);
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
        this.width = width;
        this.height = height;
        this.scale = ((float) height) / constants.getFloat("height");

        if (camera == null) {
            camera = new OrthographicCamera(width, height);
        } else {
            camera.setToOrtho(false, width, height);
        }
    }

    /**
     * Called when this screen becomes the current screen for a Game.
     */
    public void show() { active = true;}

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
     * This method checks to see if the click is in the bounds of the play button.
     * If so, it signals the that the button has been pressed and is currently down.
     * Any mouse button is accepted.
     *
     * @param screenX the x-coordinate of the mouse on the screen
     * @param screenY the y-coordinate of the mouse on the screen
     * @param pointer the button or touch finger number
     * @return whether to hand the event to other listeners.
     */
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (pressState == 2) {
            return true;
        }

        Vector3 touch = new Vector3(screenX, screenY, 0);
        camera.unproject(touch);

        for (int i = 0; i < bounds.length; i++) {
            if (bounds[i].contains(touch.x, touch.y)) {
                currLevel = i + 1;
                pressState = 1;
                return false;
            }
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

    public boolean keyDown(int keycode) { return true; }
    public boolean keyUp(int keycode) { return true; }
    public boolean keyTyped(char character) { return true; }

    public boolean touchDragged(int screenX, int screenY, int pointer) { return true; }
    public boolean mouseMoved(int screenX, int screenY) { return true; }
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return true;
    }
    public boolean scrolled(float amountX, float amountY) { return true; }
    public void pause() {}
    public void resume() {}


}
