package chroma.controller;

/**
 * GameplayController
 * ------------------
 * This class acts as the high-level coordinator for the game. It is responsible for:
 * - Managing game state and flow (reset, win, lose, and level transitions).
 * - Handling player input (via InputController) and applying it to game objects.
 * - Delegating physics simulation to the PhysicsController and level construction to the Level class.
 * - Rendering all game objects and UI messages.
 */
import chroma.model.Enemy;
import chroma.model.Level;
import chroma.model.Terrain;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Rectangle;
import edu.cornell.gdiac.assets.AssetDirectory;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.graphics.TextAlign;
import edu.cornell.gdiac.graphics.TextLayout;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.util.ScreenListener;
import edu.cornell.gdiac.math.Poly2;
import edu.cornell.gdiac.math.PolyTriangulator;

import java.util.ArrayList;
import java.util.List;

public class GameplayController implements Screen {

    public static final int EXIT_QUIT = 0;
    public static final int EXIT_NEXT = 1;
    public static final int EXIT_PREV = 2;
    public static final int EXIT_COUNT = 180;

    private boolean debug;
    private boolean active;
    private boolean complete;
    private boolean failed;
    private int countdown;

    private ScreenListener listener;
    private SpriteBatch batch;
    private AssetDirectory directory;
    private JsonValue constants;

    private float width, height;
    private float worldWidth, worldHeight;

    private OrthographicCamera camera;

    // New modules
    private PhysicsController physics;
    private Level level;

    private List<AIController> aiControllers;

    // For UI messages
    private BitmapFont displayFont;
    private TextLayout goodMessage;
    private TextLayout badMessage;

    // For input conversion (for syncing mouse and gamepad input)
    protected Rectangle bounds;
    protected Vector2 scale;

    public GameplayController(AssetDirectory directory) {
        this.directory = directory;
        this.constants = directory.getEntry("platform-constants", JsonValue.class);

        // Read world configuration from JSON
        JsonValue worldConf = constants.get("world");
        this.worldWidth = worldConf.get("bounds").getFloat(0);
        this.worldHeight = worldConf.get("bounds").getFloat(1);
        float gravityY = worldConf.getFloat("gravity", -10f);

        // For converting input coordinates
        scale = new Vector2();
        bounds = new Rectangle(0, 0, worldConf.get("bounds").getFloat(0),
            worldConf.get("bounds").getFloat(1));
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera = new OrthographicCamera();

        // Initialize the PhysicsController with gravity
        physics = new PhysicsController(gravityY, directory);

        // Setup font and messages
        displayFont = directory.getEntry("shared-retro", BitmapFont.class);
        goodMessage = new TextLayout();
        goodMessage.setText("VICTORY!");
        goodMessage.setColor(com.badlogic.gdx.graphics.Color.YELLOW);
        goodMessage.setAlignment(TextAlign.middleCenter);

        badMessage = new TextLayout();
        badMessage.setText("FAILURE!");
        badMessage.setColor(com.badlogic.gdx.graphics.Color.RED);
        badMessage.setAlignment(TextAlign.middleCenter);

        reset();
    }

    public void reset() {
        // Dispose previous physics world if necessary
        if (physics != null) {
            physics.dispose();
        }
        float gravityY = constants.get("world").getFloat("gravity", -10f);
        physics = new PhysicsController(gravityY, directory);
        complete = false;
        failed = false;
        countdown = -1;

        // Determine units (scale factor for physics objects)
        float units = (height == 0) ? 1 : (height / worldHeight);

        // Build the level (environment) including walls and platforms
        level = new Level(directory, units, constants);

        // Add key objects to the physics world
        physics.addObject(level.getGoalDoor());
        physics.addObject(level.getAvatar());

        for (Enemy enemy : level.getEnemies()) {
            physics.addObject(enemy);
            if (aiControllers == null) {
                aiControllers = new ArrayList<>();
            }
            aiControllers.add(new AIController(enemy, this, physics, level));
        }
            // Add all walls and platforms
        for (Terrain wall : level.getWalls()) {
            physics.addObject(wall);
        }
        for (Terrain platform : level.getPlatforms()) {
            physics.addObject(platform);
        }
    }


    private boolean preUpdate(float dt) {
        InputController input = InputController.getInstance();
        input.sync(bounds, scale);  // Update input state

        if (input.didDebug()) {
            debug = !debug;
        }
        if (input.didReset()) {
            reset();
        }
        if (input.didExit()) {
            return false;
        }
        if (countdown > 0) {
            countdown--;
            if (countdown == 0) {
                if (failed) {
                    reset();
                } else if (complete && listener != null) {
                    listener.exitScreen(this, EXIT_NEXT);
                    return false;
                }
            }
        }
        return true;
    }

    private void update(float dt) {
        InputController input = InputController.getInstance();
        // Update player (chameleon) movement based on input
        float hmove = input.getHorizontal();
        float vmove = input.getVertical();
        level.getAvatar().setMovement(hmove * level.getAvatar().getForce());
        level.getAvatar().setVerticalMovement(vmove * level.getAvatar().getForce());
        level.getAvatar().setShooting(input.didSecondary());
        level.getAvatar().applyForce();

        // Ensure the chameleon's orientation is updated (this call is now redundant
        // if Chameleon.update() calls updateOrientation(), but is safe to include)
        level.getAvatar().updateOrientation();

        // Update all AI enemies
        for (AIController ai : aiControllers) {
            ai.update(dt);
        }

        // Check if player fell off the world
        if (!failed && level.getAvatar().getObstacle().getY() < -1) {
            setFailure(true);
        }
        if(input.didSecondary()){
            level.getAvatar().setShooting(true);
            System.out.println(level.getAvatar().isShooting());
        }
        if(level.getAvatar().isShooting()){
            level.getAvatar().shootRays();
            physics.addPaint(level.getAvatar());
        }
        updateCamera();
    }


    private void postUpdate(float dt) {
        physics.update(dt);
    }

    private void draw(float dt) {
        ScreenUtils.clear(Color.DARK_GRAY);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

//       Since the background is not a game model its texture is not given by Level.
        // Handled manually

        // Calculate the conversion factor from world units to screen pixels.
        float units = (height == 0) ? 1 : (height / worldHeight);
        // Compute the full map dimensions in screen pixels.
        float mapWidth = worldWidth * units;
        float mapHeight = worldHeight * units;

        // Get background configuration.
        JsonValue bgConfig = constants.get("background");
        float scaleFactor = bgConfig.getFloat("scaleFactor", 1.0f); // e.g., 1.2 makes tiles 20% larger

        Texture floorTile = directory.getEntry("floor-tiles", Texture.class);
        floorTile.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        int nativeTileWidth = floorTile.getWidth();
        int nativeTileHeight = floorTile.getHeight();
        int effectiveTileWidth = (int) (nativeTileWidth * scaleFactor);
        int effectiveTileHeight = (int) (nativeTileHeight * scaleFactor);

        // Compute how many tiles we need to cover the entire map area.
        int tilesX = (int) Math.ceil(mapWidth / (float) effectiveTileWidth);
        int tilesY = (int) Math.ceil(mapHeight / (float) effectiveTileHeight);

        // Draw the background tiles so they cover the entire map.
        for (int i = 0; i < tilesX; i++) {
            for (int j = 0; j < tilesY; j++) {
                float x = i * effectiveTileWidth;
                float y = j * effectiveTileHeight;
                batch.draw(floorTile, x, y, effectiveTileWidth, effectiveTileHeight);
            }
        }
        // Draw all objects managed by the physics controller
        for (ObstacleSprite sprite : physics.objects) {
            sprite.draw(batch);
        }

        if (debug) {
            for (ObstacleSprite sprite : physics.objects) {
                sprite.drawDebug(batch);
            }
        }

        if (complete && !failed) {
            batch.drawText(goodMessage, width / 2, height / 2);
        } else if (failed) {
            batch.drawText(badMessage, width / 2, height / 2);
        }
        batch.end();
    }
    private void updateCamera() {
        // Get the player's position from the physics body (in world/physics units)
        Vector2 playerPos = level.getAvatar().getObstacle().getPosition();

        // Compute the conversion factor (this is the same as used when initializing objects)
        float units = (height == 0) ? 1 : (height / worldHeight);

        // Set the camera's position so that the player is centered in the view.
        // We multiply the player's position by the conversion factor to get the position in screen coordinates.
        camera.position.set(playerPos.x * units, playerPos.y * units, 0);
        camera.update();
    }


    @Override
    public void render(float delta) {
        if (!active)
            return;
        if (!preUpdate(delta))
            return;
        update(delta);
        postUpdate(delta);

        draw(delta);
    }

    @Override
    public void show() {
        active = true;
    }

    @Override
    public void hide() {
        active = false;
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        if (camera == null) {
            camera = new OrthographicCamera();
        }
        camera.setToOrtho(false, width, height);
        scale.x = width / bounds.width;
        scale.y = height / bounds.height;
        reset();
    }

    @Override
    public void dispose() {
        physics.dispose();
    }

    public void setScreenListener(ScreenListener listener) {
        this.listener = listener;
    }

    public void setSpriteBatch(SpriteBatch batch) {
        this.batch = batch;
    }

    private void setFailure(boolean value) {
        if (value) {
            countdown = EXIT_COUNT;
        }
        failed = value;
    }

    public float getWorldWidth() { return worldWidth; }

    public float getWorldHeight() { return worldHeight; }
}
