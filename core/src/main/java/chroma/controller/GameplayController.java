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
import chroma.model.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import edu.cornell.gdiac.assets.AssetDirectory;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.graphics.SpriteMesh;
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
    private int numGoals = 1000;

    private ScreenListener listener;
    private SpriteBatch batch;
    private AssetDirectory directory;
    private JsonValue constants;

    // Dimensions in pixels
    private float width, height;
    // Dimensions of the “world” in Box2D units
    private float worldWidth, worldHeight;

    // Paint Bar constants, unused
    private Texture paintBarFrame;
    private Texture paintBarFill;
    private float paintBarMaxWidth = 200;
    private float paintBarHeight = 20;
    private float paintBarX = 50;
    private float paintBarY = 50;

    private Chameleon player;
    private float splatterCost = 3f;
    private float bombCost = 2f;



    private OrthographicCamera camera;
    private OrthographicCamera uiCamera;

    private PhysicsController physics;
    private Level level;
    private LevelSelector levelSelector;

    private List<AIController> aiControllers;
    private boolean globalChase = false;

    // For UI messages
    private BitmapFont displayFont;
    private TextLayout goodMessage;
    private TextLayout badMessage;
    private TextLayout goalMessage;

    //Single scale factor for world→screen
    private float units;
    // For input conversion (screen→world).
    // scale.x = (screenWidth / worldWidth), scale.y = (screenHeight / worldHeight).
    protected Vector2 scale;

    protected Rectangle bounds;

    public GameplayController(AssetDirectory directory) {
        this.directory = directory;
        this.constants = directory.getEntry("platform-constants", JsonValue.class);

        levelSelector = new LevelSelector(directory);
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
        uiCamera = new OrthographicCamera();
        uiCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        // Set default values (real values assigned in resize)
        this.width  = Gdx.graphics.getWidth();
        this.height = Gdx.graphics.getHeight();
        this.units  = 1.0f;

        // Initialize the PhysicsController
        physics = new PhysicsController(gravityY,numGoals, directory);

        // Setup font and messages
        displayFont = directory.getEntry("shared-retro", BitmapFont.class);
        float targetWidth = Gdx.graphics.getWidth() * 0.8f;
        float targetHeight = Gdx.graphics.getHeight() * 0.1f; // optional for height limit

// Set a default font scale
        float baseScale = 1.0f;
        displayFont.getData().setScale(baseScale);

// Measure both messages
        GlyphLayout layout = new GlyphLayout();

        layout.setText(displayFont, "VICTORY!");
        float victoryWidth = layout.width;
        float victoryHeight = layout.height;

        layout.setText(displayFont, "FAILURE!");
        float failureWidth = layout.width;
        float failureHeight = layout.height;

// Choose the larger width or height to normalize
        float maxWidth = Math.max(victoryWidth, failureWidth);
        float maxHeight = Math.max(victoryHeight, failureHeight);

// Scale factor to make both messages fit the same bounding box
        float scale = Math.min(targetWidth / maxWidth, targetHeight / maxHeight);

// Apply the scale to the font
        displayFont.getData().setScale(scale);

// Now make layouts
        goodMessage = new TextLayout();
        goodMessage.setText("VICTORY!");
        goodMessage.setColor(Color.YELLOW);
        goodMessage.setAlignment(TextAlign.middleCenter);
        goodMessage.setFont(displayFont);

        badMessage = new TextLayout();
        badMessage.setText("FAILURE!");
        badMessage.setColor(Color.RED);
        badMessage.setAlignment(TextAlign.middleCenter);
        badMessage.setFont(displayFont);


        goalMessage = new TextLayout();


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
        physics = new PhysicsController(gravityY,numGoals, directory);

        complete = false;
        failed = false;
        countdown = -1;

        // Build the level with the current `units`
        level = new Level(directory, units, levelSelector);



        // Add all walls
        for (Terrain wall : level.getWalls()) {
            physics.addObject(wall);
        }

        // Add key objects to the physics world
        player = level.getAvatar();
        player.setPaint(player.getMaxPaint());
        physics.addObject(level.getGoalDoor());
        physics.addObject(player);
        physics.createGoal(new Vector2(3f,28.5f),10,units, constants);
        physics.createGoal(new Vector2(23.5f,6.5f),30,units, constants);

        // Initialize AI
        aiControllers = new ArrayList<>();
        for (Enemy enemy : level.getEnemies()) {
            if (enemy.getType() != Enemy.Type.CAMERA) { // Only add physical enemies
                physics.addObject(enemy);
            }
            aiControllers.add(new AIController(enemy, this, physics, level));
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
//        float hmove = input.getHorizontal();
//        float vmove = input.getVertical();
//        level.getAvatar().setMovement(hmove * level.getAvatar().getForce());
//        level.getAvatar().setVerticalMovement(vmove * level.getAvatar().getForce());
        player.setShooting(input.didSecondary());
//        level.getAvatar().applyForce();
        player.updateOrientation();

        // Update AI enemies
        boolean anyChasing = false;
        for (AIController ai : aiControllers) {
            ai.update(dt);
            if (ai.getPlayerDetected()) {
                anyChasing = true;
            }
        }
        if (anyChasing) {
            for (AIController ai : aiControllers) {
                ai.setState(AIController.State.CHASE);
            }
        }
        globalChase = anyChasing;

        // Update the state of aiming
        player.setAiming(input.didAim() && player.hasEnoughPaint(bombCost));

        // Throw a bomb on left‐click
        if (input.didTertiary() && player.hasEnoughPaint(bombCost) && input.didAim()) {
            createBomb();
            player.setPaint(player.getPaint() - bombCost);
        }
        // Remove bombs that have exploded
        for (Bomb b: level.getBombs()) {
            if (b.isExpired()) {
                removeBomb(b);
            }
        }
        for (Bomb b : level.getBombs()) {
            b.update(dt);
            // If you want to check collisions or do "landing" logic, do it here:
            if (b.isExpired()) {
                removeBomb(b);
            }
            // Or if b hits a certain target or distance, b.setFlying(false);
        }
        // Check collisions
        if (!failed && physics.didPlayerCollideWithEnemy()) {
            setFailure(true);
            physics.resetCollisionFlags();
        }

        // Check winning
        if (!failed && physics.didWin()) {
            setVictory();
            physics.resetCollisionFlags();
        }

        // Fire paint spray
        if (player.isShooting() && player.hasEnoughPaint(splatterCost)) {
            // Get mouse position in screen space.
            Vector3 screenMouse = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            // Unproject to obtain world coordinates (in pixel space).
            camera.unproject(screenMouse);
            // Convert pixel coordinates to Box2D world units.
            Vector2 mouseWorld = new Vector2(screenMouse.x / units, screenMouse.y / units);
            // Get avatar position.
            Vector2 avatarPos = player.getObstacle().getPosition();
            // Compute angle (in radians) from avatar to mouse.
            float sprayAngle = (float) Math.atan2(mouseWorld.y - avatarPos.y, mouseWorld.x - avatarPos.x);
            physics.shootRays(player, sprayAngle);
            physics.addPaint(player, units, constants);
            player.setPaint(player.getPaint() - splatterCost);
        }

        List<ObstacleSprite> toRemove = new ArrayList<>();
        for (ObstacleSprite obj : physics.objects) {
            if (obj instanceof Spray) {
                Spray spray = (Spray) obj;
                spray.update(dt);
                if (spray.isExpired()) {
                    toRemove.add(spray);
                }
            }
        }

        for (ObstacleSprite obj : toRemove) {
            physics.removeObject(obj);
        }
        physics.objects.removeAll(toRemove);
        updateCamera();
    }

    /**
     * Helper for clamping the bomb position in aiming range
     * */
    private Vector2 clampBombPos(Vector3 screenPos) {
        float startX = player.getPosition().x * units;
        float startY = player.getPosition().y * units;

        float aimRange = constants.get("bomb").getFloat("aimRange") * units;

        // Calculate the unclamped bomb position
        float bombX = screenPos.x;
        float bombY = screenPos.y;

        // Calculate the distance from the player to the bomb position
        float dx = bombX - startX;
        float dy = bombY - startY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        // If the distance exceeds the allowed range, clamp it
        if (distance > aimRange / 2) {
            float scale = (aimRange / 2) / distance;  // Ratio to scale the vector down
            bombX = startX + dx * scale;
            bombY = startY + dy * scale;
        }
        return new Vector2(bombX, bombY);
    }

    /**
     * Helper for creating the bomb
     * */
    private void createBomb() {
        Vector3 screenPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(screenPos);

        Vector2 clampedPos = clampBombPos(screenPos);
        Vector2 targetPos = new Vector2(clampedPos.x / units, clampedPos.y / units);
        Vector2 playerPos = player.getObstacle().getPosition();
        float speed = 6f;
        Vector2 velocity = new Vector2(targetPos).sub(playerPos).nor().scl(speed);
        Texture bombTex   = directory.getEntry("platform-bullet", Texture.class);
        JsonValue bombData= constants.get("bomb");
        Bomb bomb = new Bomb(units, bombData, playerPos, velocity, targetPos);
        bomb.setTexture(bombTex);
        bomb.getObstacle().setName("bomb");

        level.getBombs().add(bomb);
        physics.addObject(bomb);
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
     * Draw the paint container UI with a solid color fill.
     */
    private void drawPaintContainer(Texture bar, Texture barOverlay) {
        if (player == null) return;

        batch.setProjectionMatrix(uiCamera.combined);

        float heightRatio = constants.get("paintBar").getFloat("height");
        float widthRatio = constants.get("paintBar").getFloat("width");
        float posXRatio = constants.get("paintBar").getFloat("posX");
        float posYRatio = constants.get("paintBar").getFloat("posY");
        float textRatio = constants.get("paintBar").getFloat("textRatio");
        float textOffset = constants.get("paintBar").getFloat("textOffset");
        float paintPercent = player.getPaint() / player.getMaxPaint();
        float currentBarHeight = paintPercent * heightRatio;

        batch.draw(barOverlay, width*posXRatio, height*posYRatio, width*widthRatio, height*heightRatio);

        if (paintPercent > 0.5f) {
            batch.setColor(Color.WHITE);
        } else if (paintPercent > 0.2f) {
            batch.setColor(Color.YELLOW);
        } else {
            batch.setColor(Color.RED);
        }

        batch.draw(bar, width*posXRatio, height*posYRatio, width*widthRatio, height*currentBarHeight);

        displayFont.getData().setScale(width*widthRatio / textRatio);
        String paintText = String.format("%.0f%%", paintPercent * 100);
        batch.drawText(paintText, displayFont, width*posXRatio, height*(posYRatio-textOffset));

        batch.setColor(Color.WHITE);

    }

    /**
     * Helper for drawing the aiming range
     * */
    private void drawAimRange(Texture aimTex) {
        if (player == null) return;

        // Get the mouse position in screen coordinates.
        Vector3 screenPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        // Convert to world coordinates (in pixel space) taking into account camera translation.
        camera.unproject(screenPos);

        float startX = player.getPosition().x;
        float startY = player.getPosition().y;

        float s = constants.get("bomb").getFloat( "size" );
        float radius = s * units;
        float aimRange = constants.get("bomb").getFloat("aimRange") * units;

        Vector2 aimPos = clampBombPos(screenPos);

        // Draw the aiming circle
        batch.draw(aimTex, aimPos.x- radius/2, aimPos.y - radius/2, radius, radius);

        // Draw the aiming range
        batch.draw(aimTex, startX*units - aimRange/2, startY*units - aimRange/2, aimRange, aimRange);
    }

    /**
     * Debug helper to see all tiles and coordinates labelled in debug view. Uncomment call in 'draw' method to view.
     */
    private void drawMapCoords(SpriteBatch batch) {
        BitmapFont font = new BitmapFont(); // Default font
        font.setColor(Color.WHITE); // Set color to white for visibility
        font.getData().setScale(0.5f); // Scale down text if needed

        for (int x = 0; x < worldWidth; x++) {
            for (int y = 0; y < worldHeight; y++) {
                float tileX = x * units;
                float tileY = y * units;
                String coordText = "(" + x + "," + y + ")";

                font.draw(batch, coordText, tileX, tileY);
            }
        }
    }

    /**
     * Main draw method.
     */
    private void draw(float dt) {
        ScreenUtils.clear(Color.DARK_GRAY);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        // Draw tiled background


        // Draw all bombs
        for (ObstacleSprite sprite : physics.objects) {
            if (sprite.getName() != null && sprite.getName().equals("bomb")) {
                Bomb bomb = (Bomb) sprite.getObstacle().getUserData();
                if (bomb != null && !bomb.isFlying()) {
                    bomb.draw(batch);
                }
            }
        }

        for (ObstacleSprite sprite : physics.objects) {
            if (sprite.getName() != null && sprite.getName().equals("spray")) {
                sprite.draw(batch);
            }
        }

        // Draw all physics objects other than bombs
        for (ObstacleSprite sprite : physics.objects) {
            if (sprite.getName() == null || (sprite.getName() != null && !sprite.getName().equals("bomb")  && !sprite.getName().equals("spray"))) {
                sprite.draw(batch);
            }
        }
        for (ObstacleSprite sprite : physics.objects) {
            if ("bomb".equals(sprite.getName())) {
                Bomb bomb = (Bomb) sprite.getObstacle().getUserData();
                if (bomb != null && bomb.isFlying()) {
                    bomb.draw(batch);
                }
            }
        }
        // Draw the aiming
        Texture aimTex = directory.getEntry("aiming-range", Texture.class);
        aimTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        if (player.isAiming()) {
            drawAimRange(aimTex);
        }
        for (ObstacleSprite sprite : physics.objects) {
            if (sprite.getName() != null && sprite.getName().equals("chameleon")) {
                sprite.draw(batch);
            }
        }
        batch.setColor(Color.WHITE);
        batch.setTexture(null);
        // Debug overlays
        if (debug) {
            for (ObstacleSprite sprite : physics.objects) {
                sprite.drawDebug(batch);
            }

            // Uncomment to see AI Enemy debugging (from AIController)
//            drawMapCoords(batch);
//
            batch.end();
            for (AIController aiController : aiControllers) {
                aiController.debugRender(camera); // Call debug grid rendering
            }
            batch.begin(); // Resume SpriteBatch rendering
        }

        // Draw the paint container (UI) after objects
        Texture barTex = directory.getEntry("paintBar", Texture.class);
        Texture barOverlayTex = directory.getEntry("paintBar-overlay", Texture.class);
        barTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        barOverlayTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        drawPaintContainer(barTex, barOverlayTex);


        // UI messages
        if (complete && !failed) {
            batch.drawText(goodMessage, width / 2, height / 2);
        } else if (failed) {
            batch.drawText(badMessage, width / 2, height / 2);
        }
        int numFilled = 0;
        for(Goal goal : physics.getGoalList()){
            if(goal.isFull()){
                numFilled +=1;
            }
        }
        float goalPercentage = ((float) numFilled / (float) numGoals) * 100;
        goalMessage.setText("Goals filled: " + goalPercentage + "%");
        goalMessage.setColor(Color.YELLOW);
        goalMessage.setAlignment(TextAlign.middleCenter);
        goalMessage.setFont(displayFont);
        batch.drawText(goalMessage, width / 2, height - 40);


        batch.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * Center the camera on the player and clamp to map bounds.
     */
    /**
     * Center the camera on the player at all times (no boundaries).
     */
    private void updateCamera() {
        // Get the player's position in Box2D world units
        Vector2 playerPos = level.getAvatar().getObstacle().getPosition();

        // Multiply by 'units' to convert to screen/pixel space
        float desiredCamX = playerPos.x * units;
        float desiredCamY = playerPos.y * units;

        // Simply set the camera position to the player's position
        camera.position.set(desiredCamX, desiredCamY, 0);
//        camera.zoom = 0.9f;
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
        if (uiCamera == null) {
            uiCamera = new OrthographicCamera();
        }
        uiCamera.setToOrtho(false, width, height);

        // 1) Compute the uniform scale factor from world→screen
        //    so that worldHeight always fits the new window height
        units = 32;

        // 2) The InputController scale for screen→world
        //    scale.x = (float) screenWidth / worldWidth
        //    scale.y = (float) screenHeight / worldHeight
        scale.x = (this.width  == 0) ? 1.0f : ( (float)this.width  / worldWidth  );
        scale.y = (this.height == 0) ? 1.0f : ( (float)this.height / worldHeight );
        // Rebuild the world for the new scale
        paintBarX = width - 250; // 50px margin from the right
        paintBarY = height - 100;
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

    public OrthographicCamera getCamera() { return camera; }
    public float getWorldWidth() { return worldWidth; }
    public float getWorldHeight() { return worldHeight; }
    public boolean isGlobalChase() { return globalChase; }
    public float getUnits() {
        return units;
    }

}
