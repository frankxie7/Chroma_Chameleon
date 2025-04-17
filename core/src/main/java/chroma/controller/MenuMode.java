package chroma.controller;

import chroma.controller.LevelSelector;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
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
    private Rectangle[] bounds;

    private ScreenListener listener;

    public MenuMode(String assetFile, SpriteBatch batch) {
        this.batch = batch;

        internal = new AssetDirectory( "menu/menu.json" );
        internal.loadAssets();
        internal.finishLoading();

        constants = internal.getEntry( "constants", JsonValue.class );
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        bounds = new Rectangle[10];

        affine = new Affine2();
        Gdx.input.setInputProcessor(this);

        this.directory = new AssetDirectory(assetFile);
        directory.loadAssets();
        directory.finishLoading();
    }

    @Override public void dispose() {
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
        // Cornell colors
        ScreenUtils.clear( 0.702f, 0.1255f, 0.145f,1.0f );

        batch.begin(camera);
        batch.setColor( Color.WHITE );

        Texture texture = internal.getEntry("play",Texture.class);
        int numCols = constants.getInt("numCols");
        int numRows = constants.getInt("numRows");
        int numButtons = constants.getInt("numButtons");
        float buttonScale = constants.getFloat("button.scale");

        float buttonSize = height * buttonScale; // you can tweak this
        float hSpacing = (width - numCols * buttonSize) / (numCols + 1); // horizontal spacing
        float vSpacing = (height - numRows * buttonSize) / (numRows + 1); // vertical spacing

        for (int i = 0; i < numButtons; i++) {
            int row = i / numCols;
            int col = i % numCols;

            float x = hSpacing + col * (buttonSize + hSpacing);
            float y = height - (vSpacing + (row + 1) * buttonSize + row * vSpacing);

            bounds[i] = new Rectangle(x, y, buttonSize, buttonSize);
            batch.draw(texture, x, y, buttonSize, buttonSize);
        }

        batch.end();
    }


    public void setScreenListener(ScreenListener listener) {
        this.listener = listener;
    }

    @Override
    public void show() {}

    @Override
    public void render(float delta) {
        draw();
    }

    @Override
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

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        Vector3 touch = new Vector3(screenX, screenY, 0);
        camera.unproject(touch);

        for (int i = 0; i < bounds.length; i++) {
            if (bounds[i].contains(touch.x, touch.y)) {
                if (listener != null) {
                    int currLevel = i + 1;
                    listener.exitScreen(this, currLevel);
                }
                return true;
            }
        }
        return false;
    }

    @Override public boolean keyDown(int keycode) { return false; }
    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return true;
    }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

}
