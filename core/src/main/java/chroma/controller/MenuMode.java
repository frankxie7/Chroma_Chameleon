package chroma.controller;

import chroma.controller.LevelSelector;
import chroma.model.Level;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import edu.cornell.gdiac.assets.AssetDirectory;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.util.ScreenListener;
import com.badlogic.gdx.Preferences;


public class MenuMode implements Screen, InputProcessor {
    private AssetDirectory directory;
    private SpriteBatch batch;

    /** Asset directory for assets used in this menu screen*/
    private AssetDirectory internal;
    private OrthographicCamera camera;
    private Affine2 affine;
    private JsonValue constants;

    private int width, height;
    private float scale;

    /** Number of buttons */
    private int buttonNum;

    /** The rectangle bounds for every button */
    private Bound[] bounds;

    /** The bounds for left and right arrows */
    private Bound[] arrBounds;

    /** The textures for every button */
    private Texture[] buttonTexs1;
    /** The textures for every button */
    private Texture[] buttonTexs2;

    /** The textures for every pressed button on first page */
    private Texture[] buttonPressTexs1;
    /** The textures for every pressed button on second page */
    private Texture[] buttonPressTexs2;

    private ScreenListener listener;

    /** Whether or not this player mode is still active */
    private boolean active;

    /** Current level number */
    private int currLevel;

    /** The current state of the button */
    private int pressState;

    /** Whether left arrow is pressed*/
    private boolean leftPressed;
    /** Whether right arrow is pressed*/
    private boolean rightPressed;
    /** Current page number*/
    private int currPage;
    private Preferences prefs;
    /** Draw the outline for determining */
    private ShapeRenderer shapeRenderer;

    private Sound menuSong;
    private Sound levelSelectedSound;

    private float animTime;

    /** Internal class for creating a rectangle bound */
    private class Bound {
        public float x, y, width, height;

        public Bound(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean contains(float px, float py) {
            float dx = px - x;
            float dy = py - y;
            return px >= x && px <= x + width && py >= y && py <= y + height;
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
        prefs = Gdx.app.getPreferences("GameProgress");
        levelSelectedSound = internal.getEntry("level-select", Sound.class);

        menuSong = internal.getEntry("intro", Sound.class);
        long soundId = menuSong.play();
        menuSong.setLooping(soundId, true);
        menuSong.setVolume(soundId, 0.2f);

        constants = internal.getEntry( "constants", JsonValue.class );
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        buttonNum = constants.getInt("numButtons");
        bounds = new Bound[buttonNum];
        arrBounds = new Bound[2];
        buttonTexs1 = new Texture[buttonNum];
        buttonPressTexs1 = new Texture[buttonNum];
        for (int i = 1; i < buttonNum + 1; i++) {
            buttonTexs1[i-1] = internal.getEntry("button" + i, Texture.class);
            buttonTexs1[i-1].setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            buttonPressTexs1[i-1] = internal.getEntry("buttonPress" + i, Texture.class);
            buttonPressTexs1[i-1].setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        buttonTexs2 = new Texture[buttonNum];
        buttonPressTexs2 = new Texture[buttonNum];
        for (int i = 0; i < buttonNum; i++) {
            buttonTexs2[i] = internal.getEntry("button1" + i, Texture.class);
            buttonTexs2[i].setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            buttonPressTexs2[i] = internal.getEntry("buttonPress1" + i, Texture.class);
            buttonPressTexs2[i].setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }

        animTime = 0;

        affine = new Affine2();
        Gdx.input.setInputProcessor(this);

        this.directory = new AssetDirectory(assetFile);
        directory.loadAssets();
        directory.finishLoading();

        active = true;
        currPage = 0;
        shapeRenderer = new ShapeRenderer();
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
    private void draw(float delta) {
        // Background colors
//        ScreenUtils.clear( 0.051f, 0.173f, 0.212f, 1f );

        batch.begin(camera);
        batch.setColor( Color.WHITE );

        Texture backgroundSheet = internal.getEntry("background", Texture.class);
        backgroundSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        Animation<TextureRegion> backgroundAnim = Level.createAnimation(backgroundSheet, 7, 0.075f);
        animTime += delta;
        TextureRegion backgroundFrame = backgroundAnim.getKeyFrame(animTime, false);
        batch.draw(backgroundFrame, 0, 0, width,  height) ;

        // Draw buttons
        int numCols = constants.getInt("numCols");
        int numRows = constants.getInt("numRows");

        float buttonScale = constants.getFloat("button.scale") ;
        float boundScale = constants.getFloat("bound.scale") ;

        Texture button = buttonTexs1[0];
        float buttonHeight = button.getHeight() * scale * buttonScale;
        float buttonWidth = button.getWidth() * scale * buttonScale;
        float boundHeight = buttonHeight * boundScale;
        float boundWidth = buttonWidth * boundScale;

        // First, calculate the total width occupied by all buttons and gaps
        float hSpacing = (width * 0.2f - numCols * buttonWidth) / (numCols + 1);  // or compute it
        float totalWidth = buttonWidth * numCols + hSpacing * (numCols - 1);

        // Center of screen
        float centerX = width / 2f;
        // Starting X of the leftmost button (so column 1 ends up centered)
        float startX = centerX - (totalWidth / 2f);

        // Similarly, calculate vertical spacing to fit inside a vertical region (e.g., the central panel)
        float panelTop = height * 0.5f; // adjust depending on where your panel starts
        float vSpacing = (panelTop - numRows * buttonHeight) / (numRows + 1);


        for (int i = 0; i < buttonNum; i++) {
            int row = i / numCols;
            int col = i % numCols;

            float x = startX + col * (buttonWidth + hSpacing);
            float y = panelTop - ((row + 1) * vSpacing + row * buttonHeight);

            bounds[i] = new Bound(x, y, boundWidth*2, boundHeight*2);

//            if (currPage == 0) {
//                Texture texture = i == currLevel - 1 ? buttonPressTexs1[currLevel-1]: buttonTexs1[i];
//                texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
//                batch.draw(texture, x, y, buttonWidth, buttonHeight);
//            } else {
//                Texture texture = i == currLevel - 10 ? buttonPressTexs2[currLevel-10]: buttonTexs2[i];
//                texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
//                batch.draw(texture, x, y, buttonWidth, buttonHeight);
//            }
            int perPage   = numCols * numRows;
            int levelNum  = i + 1 + currPage * perPage;


            boolean done = prefs.getBoolean("level" + levelNum + "Completed", false);

            Texture texture;
            if (done) {
                boolean pressed = (currPage == 0 && i == currLevel - 1)
                    || (currPage != 0 && i == currLevel - 1 - currPage * perPage);
                String key = pressed
                    ? "buttonPress" + levelNum + "Red"
                    : "button"      + levelNum + "Red";
                texture = internal.getEntry(key, Texture.class);
            } else if (currPage == 0) {
                texture = (i == currLevel - 1)
                    ? buttonPressTexs1[i]
                    : buttonTexs1[i];
            } else {
                int localIndex = i;
                texture = (localIndex == currLevel - 1 - currPage * perPage)
                    ? buttonPressTexs2[localIndex]
                    : buttonTexs2[localIndex];
            }

            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            batch.draw(texture, x, y, buttonWidth, buttonHeight);
        }

        // Draw two arrows
        Texture leftArrow = internal.getEntry("leftArrow", Texture.class);
        leftArrow.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        Texture rightArrow = internal.getEntry("rightArrow", Texture.class);
        rightArrow.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        float arrScale = constants.getFloat("arrow.scale");
        float arrWidth = leftArrow.getWidth() * scale * arrScale;
        float arrHeight = leftArrow.getHeight() * scale * arrScale;

        float arrY = bounds[4].y + boundHeight- arrHeight / 2f;
        float arrL = bounds[3].x + boundWidth - arrWidth - buttonWidth / 2f - hSpacing;
        float arrR = bounds[5].x + boundWidth + buttonWidth / 2f + hSpacing;

        batch.draw(leftArrow, arrL, arrY, arrWidth, arrHeight);
        batch.draw(rightArrow, arrR, arrY, arrWidth, arrHeight);
        arrBounds[0] = new Bound(arrL, arrY, arrWidth, arrHeight);
        arrBounds[1] = new Bound(arrR, arrY, arrWidth, arrHeight);
        batch.end();

        // Begin shape rendering for debug
//        shapeRenderer.setProjectionMatrix(camera.combined);
//        shapeRenderer.begin(ShapeRenderer.ShapeType.Line); // Line for outline
//        shapeRenderer.setColor(Color.YELLOW); // any debug color
//
//        for (int i = 0; i < bounds.length; i++) {
//            Bound cb = bounds[i];
//            shapeRenderer.rect(cb.x, cb.y, cb.width, cb.height);
//        }
//
//        for (int i = 0; i < arrBounds.length; i++) {
//            Bound cb = arrBounds[i];
//            shapeRenderer.rect(cb.x, cb.y, cb.width, cb.height);
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
            draw(delta);
        }

        // We are ready, notify our listener
        if (isReady() && listener != null) {
            menuSong.stop();
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

    /**
     * Update the current page number based on the left, right arrows
     */
    private void updateCurrPage() {
        if (leftPressed) {
            currPage = Math.max(currPage-1, 0);

            leftPressed = false;
        } else if (rightPressed) {
            currPage = Math.min(currPage+1, 1);
            rightPressed = false;
        }
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
        if (bounds == null) return false;
        if (arrBounds == null) return false;
        if (pressState == 2) {
            return true;
        }

        Vector3 touch = new Vector3(screenX, screenY, 0);
        camera.unproject(touch);

        for (int i = 0; i < bounds.length; i++) {
            if (bounds[i] != null && bounds[i].contains(touch.x, touch.y)) {
                currLevel = i + 1 + currPage * 9;
                pressState = 1;
                levelSelectedSound.play();
                menuSong.stop();
                return false;
            }
        }

        if (arrBounds[0] != null && arrBounds[0].contains(touch.x, touch.y)) {
            leftPressed = true;
        } else if (arrBounds[1] != null && arrBounds[1].contains(touch.x, touch.y)) {
            rightPressed = true;
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
        if (leftPressed || rightPressed) {
            updateCurrPage();
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
