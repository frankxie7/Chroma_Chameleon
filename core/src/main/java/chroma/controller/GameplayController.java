package chroma.controller;

import chroma.model.Bomb;
import chroma.model.Enemy;
import chroma.model.Level;
import chroma.model.Terrain;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import edu.cornell.gdiac.assets.AssetDirectory;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.graphics.TextAlign;
import edu.cornell.gdiac.graphics.TextLayout;
import edu.cornell.gdiac.physics2.Obstacle;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.util.ScreenListener;

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
    private int failureDelay;

    private ScreenListener listener;
    private SpriteBatch batch;
    private AssetDirectory directory;
    private JsonValue constants;

    // Dimensions in pixels
    private float width, height;
    // Dimensions of the “world” in Box2D units
    private float worldWidth, worldHeight;



    private OrthographicCamera camera;

    private PhysicsController physics;
    private Level level;

    private List<AIController> aiControllers;

    // For UI messages
    private BitmapFont displayFont;
    private TextLayout goodMessage;
    private TextLayout badMessage;

    //Single scale factor for world→screen
    private float units;
    // For input conversion (screen→world).
    // scale.x = (screenWidth / worldWidth), scale.y = (screenHeight / worldHeight).
    protected Vector2 scale;

    protected Rectangle bounds;

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
        bounds = new Rectangle(0, 0, worldWidth, worldHeight);

        // Initialize the camera
        camera = new OrthographicCamera();

        // Set default values (real values assigned in resize)
        this.width  = Gdx.graphics.getWidth();
        this.height = Gdx.graphics.getHeight();
        this.units  = 1.0f;

        // Initialize the PhysicsController
        physics = new PhysicsController(gravityY, directory);

        // Setup font and messages
        displayFont = directory.getEntry("shared-retro", BitmapFont.class);
        goodMessage = new TextLayout();
        goodMessage.setText("VICTORY!");
        goodMessage.setColor(Color.YELLOW);
        goodMessage.setAlignment(TextAlign.middleCenter);

        badMessage = new TextLayout();
        badMessage.setText("FAILURE!");
        badMessage.setColor(Color.RED);
        badMessage.setAlignment(TextAlign.middleCenter);
        badMessage.setFont(displayFont);

        // Now that everything is ready, build the level, etc.
        // (But we will do the final init after calling resize)
        resize((int)this.width, (int)this.height);
    }

    /**
     * Rebuilds the world state (physics, level objects, etc.).
     */
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

        // Build the level with the current `units`
        level = new Level(directory, units, constants);

        // Add key objects to the physics world
        physics.addObject(level.getGoalDoor());
        physics.addObject(level.getAvatar());

        // Initialize AI
        aiControllers = new ArrayList<>();
        for (Enemy enemy : level.getEnemies()) {
            physics.addObject(enemy);
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

    /**
     * Process input before update.
     *
     * @return false if we should end this Screen, true otherwise
     */
    private boolean preUpdate(float dt) {
        InputController input = InputController.getInstance();
        // Convert screen→world for mouse, etc.
        input.sync(bounds, scale);

        if (input.didDebug()) {
            debug = !debug;
        }
        if (input.didReset()) {
            reset();
        }
        if (input.didExit()) {
            return false;
        }

        // Handle the countdown for victory/failure
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

    /**
     * Game logic update
     */
    private void update(float dt) {
        InputController input = InputController.getInstance();

        // Update the chameleon movement
        float hmove = input.getHorizontal();
        float vmove = input.getVertical();
        level.getAvatar().setMovement(hmove * level.getAvatar().getForce());
        level.getAvatar().setVerticalMovement(vmove * level.getAvatar().getForce());
        level.getAvatar().setShooting(input.didSecondary());
        level.getAvatar().applyForce();
        level.getAvatar().updateOrientation();

        // Update AI enemies
        for (AIController ai : aiControllers) {
            ai.update(dt);
        }

        // Throw a bomb on left‐click
        if (input.didTertiary()) {
            createBomb();
        }
        // Remove bombs that have exploded
        for (Bomb b: level.getBombs()) {
            if (b.isExpired()) {
                removeBomb(b);
            }
        }

        // Check collisions
        if (!failed && physics.didPlayerCollideWithEnemy()) {
            setFailure(true);
            physics.resetCollisionFlags();
        }

        // Fire paint spray
        if (level.getAvatar().isShooting()) {
            // Get mouse position in screen space.
            Vector3 screenMouse = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            // Unproject to obtain world coordinates (in pixel space).
            camera.unproject(screenMouse);
            // Convert pixel coordinates to Box2D world units.
            Vector2 mouseWorld = new Vector2(screenMouse.x / units, screenMouse.y / units);
            // Get avatar position.
            Vector2 avatarPos = level.getAvatar().getObstacle().getPosition();
            // Compute angle (in radians) from avatar to mouse.
            float sprayAngle = (float) Math.atan2(mouseWorld.y - avatarPos.y, mouseWorld.x - avatarPos.x);
            physics.shootRays(level.getAvatar(), sprayAngle);
            physics.addPaint(level.getAvatar(), units, constants);
        }

        updateCamera();
    }

    private void createBomb() {
        // Get the mouse position in screen coordinates.
        Vector3 screenPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        // Convert to world coordinates (in pixel space) taking into account camera translation.
        camera.unproject(screenPos);
        // Convert pixel coordinates to Box2D world units.
        Vector2 pos = new Vector2(screenPos.x / units, screenPos.y / units);

        Texture bombTex = directory.getEntry("platform-bullet", Texture.class);
        JsonValue bombData = constants.get("bomb");

        Bomb bomb = new Bomb(units, bombData, pos);
        bomb.setTexture(bombTex);
        bomb.getObstacle().setName("bomb");

        level.getBombs().add(bomb);
        physics.queueObject(bomb);
    }


    /**
     * Removes a bomb from the physics world.
     */
    public void removeBomb(ObstacleSprite bomb) {
        bomb.getObstacle().markRemoved(true);
    }

    /**
     * Step physics after update.
     */
    private void postUpdate(float dt) {
        physics.update(dt);
    }

    /**
     * Main draw method.
     */
    private void draw(float dt) {
        ScreenUtils.clear(Color.DARK_GRAY);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Draw tiled background
        float mapWidth  = worldWidth  * units;
        float mapHeight = worldHeight * units;

        JsonValue bgConfig = constants.get("background");
        // Has nothing to do with unit/scale, this is for the scaling of a single tile
        float scaleFactor = bgConfig.getFloat("scaleFactor", 1.0f);

        Texture floorTile = directory.getEntry("floor-tiles", Texture.class);
        floorTile.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        int nativeTileWidth  = floorTile.getWidth();
        int nativeTileHeight = floorTile.getHeight();
        int effectiveTileWidth  = (int) (nativeTileWidth  * scaleFactor);
        int effectiveTileHeight = (int) (nativeTileHeight * scaleFactor);

        int tilesX = (int) Math.ceil(mapWidth  / (float) effectiveTileWidth);
        int tilesY = (int) Math.ceil(mapHeight / (float) effectiveTileHeight);

        for (int i = 0; i < tilesX; i++) {
            for (int j = 0; j < tilesY; j++) {
                float x = i * effectiveTileWidth;
                float y = j * effectiveTileHeight;
                batch.draw(floorTile, x, y, effectiveTileWidth, effectiveTileHeight);
            }
        }

        // Draw all physics objects
        for (ObstacleSprite sprite : physics.objects) {
            sprite.draw(batch);
        }

        // Debug overlays
        if (debug) {
            for (ObstacleSprite sprite : physics.objects) {
                sprite.drawDebug(batch);
            }
        }

        // UI messages
        if (complete && !failed) {
            batch.drawText(goodMessage, width / 2, height / 2);
        } else if (failed) {
            batch.drawText(badMessage, width / 2, height / 2);
        }

        batch.end();
    }

    /**
     * Center the camera on the player and clamp to map bounds.
     */
    private void updateCamera() {
        float mapWidth  = worldWidth  * units;
        float mapHeight = worldHeight * units;

        Vector2 playerPos = level.getAvatar().getObstacle().getPosition();
        float desiredCamX = playerPos.x * units;
        float desiredCamY = playerPos.y * units;

        float halfViewWidth  = camera.viewportWidth  / 2f;
        float halfViewHeight = camera.viewportHeight / 2f;

        // Clamp horizontally
        if (desiredCamX < halfViewWidth) {
            desiredCamX = halfViewWidth;
        } else if (desiredCamX > mapWidth - halfViewWidth) {
            desiredCamX = mapWidth - halfViewWidth;
        }
        // Clamp vertically
        if (desiredCamY < halfViewHeight) {
            desiredCamY = halfViewHeight;
        } else if (desiredCamY > mapHeight - halfViewHeight) {
            desiredCamY = mapHeight - halfViewHeight;
        }

        camera.position.set(desiredCamX, desiredCamY, 0);
        camera.update();
    }

    /**
     * The main render loop.
     */
    @Override
    public void render(float delta) {
        if (!active) return;
        if (!preUpdate(delta)) return;
        update(delta);
        postUpdate(delta);
        draw(delta);
    }

    // Screen interface methods
    @Override
    public void show() { active = true; }
    @Override
    public void hide() { active = false; }
    @Override
    public void pause() { }
    @Override
    public void resume() { }

    /**
     * Handle window resizing.
     */
    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;

        // Make sure we have a camera
        if (camera == null) {
            camera = new OrthographicCamera();
        }
        camera.setToOrtho(false, width, height);

        // 1) Compute the uniform scale factor from world→screen
        //    so that worldHeight always fits the new window height
        units = (this.height == 0) ? 1.0f : (this.height / worldHeight);

        // 2) The InputController scale for screen→world
        //    scale.x = (float) screenWidth / worldWidth
        //    scale.y = (float) screenHeight / worldHeight
        scale.x = (this.width  == 0) ? 1.0f : ( (float)this.width  / worldWidth  );
        scale.y = (this.height == 0) ? 1.0f : ( (float)this.height / worldHeight );

        // Rebuild the world for the new scale
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

    private void setVictory() {
        complete = true;
        countdown = EXIT_COUNT;
    }

    private void setFailure(boolean value) {
        if (value) {
            countdown = EXIT_COUNT;
        }
        failed = value;
    }

    public float getWorldWidth()  { return worldWidth;  }
    public float getWorldHeight() { return worldHeight; }
}
