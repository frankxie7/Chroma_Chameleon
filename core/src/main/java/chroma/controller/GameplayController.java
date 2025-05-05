package chroma.controller;

/**
 * GameplayController ------------------ This class acts as the high-level coordinator for the game.
 * It is responsible for: - Managing game state and flow (reset, win, lose, and level transitions).
 * - Handling player input (via InputController) and applying it to game objects. - Delegating
 * physics simulation to the PhysicsController and level construction to the Level class. -
 * Rendering all game objects and UI messages.
 */

import chroma.model.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
//import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import edu.cornell.gdiac.assets.AssetDirectory;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.graphics.TextAlign;
import edu.cornell.gdiac.graphics.TextLayout;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.util.ScreenListener;
import com.badlogic.gdx.utils.Queue;

//import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GameplayController implements Screen {

    public enum GameState {
        PLAYING,
        WON,
        LOST
    }

    private GameState gameState = GameState.PLAYING;

    public static final int EXIT_QUIT = 100;
    public static final int EXIT_NEXT = 101;
    public static final int EXIT_PREV = 102;
    public static final int EXIT_MAP = 103;
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

    private Chameleon player;
    private float splatterCost = 4f;
//    private static final float BOMB_INITIAL_COST = 6f;
    private static final float BOMB_SUBSEQUENT_COST = 1f;
    private boolean waitingForDoorAnim = false;


    private OrthographicCamera camera;
    private OrthographicCamera uiCamera;

    private PhysicsController physics;
    private Level level;
    private int numGoals;
    private LevelSelector levelSelector;

    private List<AIController> aiControllers;
    private boolean globalChase = false;
    private long alertSoundId = -1;
    private boolean alertSoundPlaying = false;

    // For UI messages
    private BitmapFont displayFont;
    private TextLayout goodMessage;
    private TextLayout badMessage;
    private TextLayout goalMessage;
    Rectangle retryButton = new Rectangle(width/2 - 50, height/2 - 80, 100, 40);
    Rectangle menuButton = new Rectangle(width/2 - 50, height/2 - 140, 100, 40);
    Rectangle nextButton = new Rectangle(width/2 - 50, height/2 - 200, 100, 40);

    //Single scale factor for world→screen
    private float units;
    // For input conversion (screen→world).
    // scale.x = (screenWidth / worldWidth), scale.y = (screenHeight / worldHeight).
    protected Vector2 scale;

    protected Rectangle bounds;

    private float accumulator = 0f;

    // ───── new Bomb ───────────────────────────────
    // ───── new Bomb ───────────────────────────────
    private enum BombSkillState {IDLE, READY, PAINTING, FIRING, COOLDOWN}

    ;

    private BombSkillState bombState = BombSkillState.IDLE;

    Sound enemiesAlertSound;

    private final Array<Vector2> planned = new Array<>();
    private Vector2 lastPlanned = new Vector2();

    private static final float STEP_PX = 24f;
    private static final float BOMB_COOLDOWN = 0.5f;
    private static final int MAX_PLANNED = 50;
    private float cooldownTimer = 0f;

    private Queue<Vector2> bombQueue = new Queue<>();
    private float bombFireDelay = 0.05f;
    private float bombFireTimer = 0f;


    private static final float RANGE_MIN = 5f;
    private static final float RANGE_MAX = 20f;
    private static final float RANGE_GROWTH = 12f;
    private static final float ZOOM_DEFAULT = 0.45f;
    private static final float ZOOM_OUT_MAX = 0.6f;
    private static final float ZOOM_FOCUS = 0.20f;  // tune this until the chameleon fills the view
    private static final float ZOOM_LERP = 5f;



    /* ───────── Aim‑range charging ───────── */
    private float aimRangeCurrent = RANGE_MIN;
    private float cameraZoom = ZOOM_DEFAULT;
    private float targetZoom = ZOOM_DEFAULT;

    private int baseWidth;      // window width at start‑up (pixels)
    private int baseHeight;     // window height at start‑up (pixels)
    private boolean baseResolutionSet = false;

    private com.badlogic.gdx.graphics.glutils.ShapeRenderer shapeRenderer;
    private boolean goal1Complete = false;
    private boolean goal2Complete = false;
    private boolean goal3Complete = false;


    public GameplayController(AssetDirectory directory, LevelSelector levelSelector) {
        this.directory = directory;
        this.constants = directory.getEntry("platform-constants", JsonValue.class);

        this.levelSelector = levelSelector;
        // Read world configuration from JSON
        JsonValue worldConf = constants.get("world");
        this.worldWidth = worldConf.get("bounds").getFloat(0);
        this.worldHeight = worldConf.get("bounds").getFloat(1);
        float gravityY = worldConf.getFloat("gravity", -10f);

        System.out.println(levelSelector.getCurrentLevel());

        // For converting input coordinates
        scale = new Vector2();
        bounds = new Rectangle(0, 0, worldWidth, worldHeight);

        // Initialize the camera
        camera = new OrthographicCamera();
        uiCamera = new OrthographicCamera();
        uiCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        // Set default values (real values assigned in resize)
        this.width = Gdx.graphics.getWidth();
        this.height = Gdx.graphics.getHeight();
        this.units = 1.0f;

        // Initialize the PhysicsController
        physics = new PhysicsController(gravityY, numGoals, directory);
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

        this.enemiesAlertSound = directory.getEntry("enemies_alert", Sound.class);

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

        shapeRenderer = new ShapeRenderer();
        shapeRenderer.setAutoShapeType(true);

        goalMessage = new TextLayout();
        this.width = Gdx.graphics.getWidth();
        this.height = Gdx.graphics.getHeight();

        baseWidth = (int) this.width;
        baseHeight = (int)this.height;
        // Now that everything is ready, build the level, etc.
        // (But we will do the final init after calling resize)
        resize((int) this.width, (int) this.height);
    }

    public void setBaseResolution(int width, int height) {
        this.baseWidth = width;
        this.baseHeight = height;
    }

    /**
     * Rebuilds the world state (physics, level objects, etc.).
     */
    public void reset() {
        // Reset game state so overlays disappear
        gameState = GameState.PLAYING;

        // Dispose previous physics world if necessary
        if (physics != null) {
            physics.dispose();
        }
        float gravityY = constants.get("world").getFloat("gravity", -10f);

        //level must be defined above physics to get number of goals

        level = new Level(directory, units, levelSelector);
        numGoals =
            (level.getGoalTiles().size() + level.getGoal2Tiles().size() + level.getGoal3Tiles()
                .size()) * 16;
        physics = new PhysicsController(gravityY, numGoals, directory);
        physics.getGoalList().clear();
        physics.getGoal2List().clear();
        physics.getGoal3List().clear();
        complete = false;
        failed = false;
        countdown = -1;
        targetZoom = ZOOM_DEFAULT;
        bombState = BombSkillState.IDLE;
        goal1Complete = false;
        goal2Complete = false;
        goal3Complete = false;
        if(bombQueue != null){
            bombQueue.clear();
        }
        // Build the level with the current `units`

        // Add all walls
        for (Collision wall : level.getCollision()) {
            physics.addObject(wall);
        }

        for (Grate grates : level.getGrates()) {
            physics.addObject(grates);
        }

        // Add key objects to the physics world
        player = level.getAvatar();

        player.setPaint(player.getMaxPaint());
        if (level.getGoalDoor() != null) {
            physics.setGoalDoor(level.getGoalDoor());
            physics.addObject(level.getGoalDoor());
        }
        physics.addObject(player);

        for (BackgroundTile machine : level.getGoalTiles()) {
            Rectangle rec = machine.getBounds();

            float y = (rec.getY() / 16) + 0.1f;
            float x = (rec.getX() / 16) + 0.1f;

            physics.createGoal(new Vector2(x, y), 4, 0.125f, units, constants, 1);
        }

        for (BackgroundTile machine : level.getGoal2Tiles()) {
            Rectangle rec = machine.getBounds();

            float y = (rec.getY() / 16) + 0.1f;
            float x = (rec.getX() / 16) + 0.1f;

            physics.createGoal(new Vector2(x, y),4,0.125f,units,constants,2);
        }
        for (BackgroundTile machine : level.getGoal3Tiles()) {
            Rectangle rec = machine.getBounds();

            float y = (rec.getY() / 16) + 0.1f;
            float x = (rec.getX() / 16) + 0.1f;

            physics.createGoal(new Vector2(x, y),4,0.125f,units,constants,3);
        }

        // Initialize AI
        aiControllers = new ArrayList<>();
        for (Enemy enemy : level.getEnemies()) {
            if (enemy.getType() != Enemy.Type.CAMERA1 && enemy.getType() != Enemy.Type.CAMERA2) { // Only add physical enemies
                physics.addObject(enemy);
            }
            aiControllers.add(new AIController(enemy, this, physics, level));
        }

        for (Laser laser : level.getLasers()) {
            physics.addObject(
                laser);       // builds the body via activatePhysics(world) :contentReference[oaicite:0]{index=0}&#8203;:contentReference[oaicite:1]{index=1}
            laser.toggle(false);
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
            listener.exitScreen(this, EXIT_QUIT);
            return false;
        }
        if (input.didMenu()) {
            listener.exitScreen(this, EXIT_MAP);
            return false;
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
            } else {
                // This handles proceeding to next level or previous level if this level is done
                if (input.didRetreat()) {
                    listener.exitScreen(this, EXIT_PREV);
                    return false;
                } else if (input.didAdvance()) {
                    listener.exitScreen(this, EXIT_NEXT);
                    return false;
                }
            }
        }
        handleBombSkill(dt);
        return true;
    }

    /**
     * Game logic update
     */
    private void update(float dt) {
        if (gameState == GameState.WON || gameState == GameState.LOST) {
            updateCamera();
            return; // Skip updates
        }

        InputController input = InputController.getInstance();
        boolean allowSpray = bombState == BombSkillState.IDLE || bombState == BombSkillState.COOLDOWN;
        player.setShooting(allowSpray && input.didLeftClick());
        player.updateOrientation();

        // Update AI enemies
        boolean anyChasing = false;
        for (AIController ai : aiControllers) {
            ai.update(dt);
            if (ai.getState() == AIController.State.CHASE) {
                anyChasing = true;
            }
        }
        // Update AI enemies, and turn lasers on if any are ALERT or CHASE
        boolean anyThreat = false;
        for (AIController ai : aiControllers) {
            ai.update(dt);
            AIController.State s = ai.getState();
            if (s == AIController.State.ALERT || s == AIController.State.CHASE) {
                anyThreat = true;
            }
        }
        if (!aiControllers.isEmpty()) {
            if (anyThreat) {
                if (!alertSoundPlaying) {
                    alertSoundId = enemiesAlertSound.loop();  // store the ID so we can stop it
                    alertSoundPlaying = true;
                }
            } else {
                if (alertSoundPlaying) {
                    enemiesAlertSound.stop(alertSoundId);  // stop that specific loop
                    alertSoundPlaying = false;
                }
            }
        }

        globalChase = anyChasing;

        // Update the state of aiming
        player.setAiming(input.didAim() && player.hasEnoughPaint(BOMB_SUBSEQUENT_COST ));
        for (Bomb b : level.getBombs()) {
            b.update(dt);
            // If you want to check collisions or do "landing" logic, do it here:
            if (b.isExpired()) {
                removeBomb(b);
            }
            // Or if b hits a certain target or distance, b.setFlying(false);
        }

        if(physics.goals1Full() && !goal1Complete){

            goal1Complete = true;
            for(Goal g : physics.getGoalList()){
                g.setComplete();
            }
        }
        if(physics.goals2Full() && !goal2Complete) {
            goal2Complete = true;
            for(Goal g : physics.getGoal2List()){
                g.setComplete();
            }
        }
        if(physics.goals3Full() && !goal3Complete){
            goal3Complete = true;
            for(Goal g : physics.getGoal3List()){
                g.setComplete();
            }
        }
        for (Laser laser : level.getLasers()) {
            laser.toggle(anyThreat);
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
            float sprayAngle = (float) Math.atan2(mouseWorld.y - avatarPos.y,
                mouseWorld.x - avatarPos.x);
            physics.shootRays(player, sprayAngle);
            physics.addPaint(player, units);
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
        updateBombQueue(dt);
        updateCamera();
    }

    /**
     * Helper for clamping the bomb position in aiming range
     * */
    private Vector2 clampBombPos(Vector3 screenPos, float rangePhys) {
        float startX = player.getPosition().x * units;
        float startY = player.getPosition().y * units;

        float aimRangePx = rangePhys * units;
        float bombX = screenPos.x;
        float bombY = screenPos.y;

        float dx = bombX - startX;
        float dy = bombY - startY;
        float dist = (float)Math.sqrt(dx*dx + dy*dy);

        if (dist > aimRangePx / 2f) {
            float scale = (aimRangePx / 2f) / dist;
            bombX = startX + dx * scale;
            bombY = startY + dy * scale;
        }
        return new Vector2(bombX, bombY);
    }

//    private Vector2 clampToValidBombArea(Vector2 worldPos) {
//        int tileX = (int)(worldPos.x);
//        int tileY = (int)(worldPos.y);
//
//        if (level.isTileBombable(tileX, tileY)) {
//            return worldPos;
//        }
//
//        float closestDist = Float.MAX_VALUE;
//        Vector2 closest = null;
//
//        for (Point p : level.getBombableTiles()) {
//            Vector2 tileCenter = new Vector2(p.x + 0.5f, p.y + 0.5f);
//            float dist = tileCenter.dst2(worldPos);
//            if (dist < closestDist) {
//                closestDist = dist;
//                closest = tileCenter;
//            }
//        }
//
//        return closest != null ? closest : new Vector2(0, 0);
//    }

    /**
     * Removes a bomb from the physics world.
     */
    public void removeBomb(ObstacleSprite bomb) {
        bomb.getObstacle().markRemoved(true);
    }

    private void handleBombSkill(float dt) {
        InputController in = InputController.getInstance();
        boolean skillKey   = in.didSkill();
        BombSkillState old = bombState;
        switch (bombState) {
            case READY:
            case PAINTING:
            case FIRING:
                player.setMaxSpeed(0f);
                break;
            default:
                player.setMaxSpeed(1f);
        }
        switch (bombState) {
            case IDLE:
                if (skillKey && player.hasEnoughPaint(BOMB_SUBSEQUENT_COST )) {
                    bombState = BombSkillState.READY;
                    aimRangeCurrent = RANGE_MAX;               // instant full range
                    targetZoom = ZOOM_OUT_MAX;            // start zoom-out
                }
                break;

            case READY:
                if (in.didLeftClick()) {
                    player.startBombAnimation();   // start fresh
                    player.pauseBombAnimation();   // freeze at frame‑0
                    bombState = BombSkillState.PAINTING;
                    startPainting();               // first bomb + first frame
                } else if (skillKey) {
                    bombState = BombSkillState.IDLE;
                    targetZoom = ZOOM_DEFAULT;
                    aimRangeCurrent = RANGE_MIN;
                }
                break;

            case PAINTING:
                if (!player.isBombPlaying()) {     // animation done
                    bombState = BombSkillState.COOLDOWN;
                    cooldownTimer = BOMB_COOLDOWN;
                }

                if (in.didSkill()) {               // cancel
                    planned.clear();
                    bombState = BombSkillState.IDLE;
                    aimRangeCurrent = RANGE_MIN;
                    targetZoom = ZOOM_DEFAULT;
                } else if (!in.isLeftHeld()) {     // mouse released
                    firePlannedBombs();            // will resume animation
                } else {
                    updatePainting();              // add bombs / advance frame
                }
                break;

            case COOLDOWN:
                cooldownTimer -= dt;
                if (cooldownTimer <= 0f) {
                    bombState = BombSkillState.IDLE;
                    aimRangeCurrent = RANGE_MIN;
                    targetZoom = ZOOM_DEFAULT;
                }
                break;
        }

        if (old != bombState) {
            com.badlogic.gdx.Gdx.app.log("BombSkill", old + " -> " + bombState);
        }
    }


    /**
     * read json aim range
     */
    private float maxRangePhys() {
        return aimRangeCurrent;
    }


    private void startPainting() {
        planned.clear();

        Vector3 raw = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(raw);
        Vector2 firstPix = clampBombPos(raw, aimRangeCurrent);

        if (!player.hasEnoughPaint(BOMB_SUBSEQUENT_COST )) {
            bombState = BombSkillState.IDLE;
            aimRangeCurrent = RANGE_MIN;
            targetZoom = ZOOM_DEFAULT;
            return;
        }

        player.setPaint(player.getPaint() - BOMB_SUBSEQUENT_COST );

        lastPlanned.set(firstPix);
        planned.add(firstPix.cpy().scl(1f / units));
//        Vector2 bombWorld = clampToValidBombArea(firstPix.cpy().scl(1f / units));
//        planned.add(bombWorld);

        player.advanceBombFrame(7);         // first frame shown
    }


    /**
     * decide if a new region is selected
     */
    private void updatePainting() {
        Vector3 raw = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(raw);
        Vector2 clampedScreen = clampBombPos(raw, aimRangeCurrent);
//        Vector2 firstPix = clampBombPos(raw, aimRangeCurrent);


        if (clampedScreen.dst2(lastPlanned) >= STEP_PX * STEP_PX
            && planned.size < MAX_PLANNED) {

            if (player.hasEnoughPaint(BOMB_SUBSEQUENT_COST)) {
                player.setPaint(player.getPaint() - BOMB_SUBSEQUENT_COST);
                planned.add(clampedScreen.cpy().scl(1f / units));
//                Vector2 bombWorld = clampToValidBombArea(firstPix.cpy().scl(1f / units));
//                planned.add(bombWorld);

                lastPlanned.set(clampedScreen);

                player.advanceBombFrame(7);      // one frame per bomb
            } else {
                firePlannedBombs();
            }
        }
    }

    /**
     * launch bomb & consume
     */
    private void firePlannedBombs() {
        player.resumeBombAnimation();   // play the rest of the clip
        int n = planned.size;
        if (n == 0) {
            bombState = BombSkillState.IDLE;
            return;
        }
        for (Vector2 target : planned) {
            bombQueue.addLast(target);
        }
        planned.clear();
        bombFireTimer = bombFireDelay;
        bombState = BombSkillState.FIRING;
    }

    private void updateBombQueue(float dt) {
        if (bombState == BombSkillState.FIRING) {
            bombFireTimer -= dt;
            if (bombFireTimer <= 0f && bombQueue.size > 0) {
                Vector2 target = bombQueue.removeFirst();

                Vector2 playerPos = player.getObstacle().getPosition();

                float speed = 6f;
                Vector2 vel = new Vector2(target).sub(playerPos).nor().scl(speed);
                Texture bombTex = directory.getEntry("platform-bullet", Texture.class);
                Texture bulletTex = directory.getEntry("platform-bullet", Texture.class);
                Texture splatterTex = directory.getEntry("bomb-splatter", Texture.class);
                Sound splatterSound = directory.getEntry("bomb", Sound.class);
                splatterTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                JsonValue bombData = constants.get("bomb");
                Bomb bomb = new Bomb(units, bombData,
                    playerPos, vel, target,
                    bulletTex, splatterTex,
                    splatterSound);
                bomb.setTexture(bombTex);
                bomb.getObstacle().setName("bomb");

                level.getBombs().add(bomb);
                physics.addObject(bomb);

                bombFireTimer = bombFireDelay;
            }
            if (bombQueue.size == 0) {
                bombState = BombSkillState.COOLDOWN;
                cooldownTimer = BOMB_COOLDOWN;
            }
        }
    }


    /**
     * Step physics after update.
     */
    private void postUpdate(float dt) {
        if (gameState == GameState.WON || gameState == GameState.LOST) {
            return; // Skip updates
        }

        // Check collisions
        if (!failed && physics.didPlayerCollideWithEnemy() && !physics.didWin()) {
            setFailure(true);
            physics.resetCollisionFlags();
        }

        // Laser kill?
        if (!failed && physics.didPlayerHitByLaser() && !physics.didWin()) {
            setFailure(true);
            physics.resetLaserFlag();
        }
        // ——— Win + chameleon fall all in controller ———
            if (!failed && physics.didWin()) {
                    Door door = level.getGoalDoor();
                    Chameleon cham = player;

                        // 1) first frame we detect win: start moving‑to‑vent logic
                           if (!door.isFallPlayed()) {
                            if (cham != null) {
                                    Vector2 pos = cham.getObstacle().getPosition();
                                    Vector2 center = door.getCenter();
                                   if (pos.dst(center) < 0.05f) {
                                           // at vent: trigger fall animation
                                                cham.setFalling(true);
                                            door.playChameleonFallAnimation();
                                            door.setFallPlayed(true);
                                            // reset physics flags but keep waitingForDoorAnim == true
                                                waitingForDoorAnim = true;
                                        } else {
                                           // move toward center
                                                Vector2 dir = center.cpy().sub(pos).nor();
                                            cham.getObstacle().getBody().setLinearVelocity(dir.scl(5.0f));
                                        }
                                }
                        }
                    physics.resetCollisionFlags();
               }

                // 2) once animation is done, actually show the win‑UI
                    if (waitingForDoorAnim) {
                    Door door = level.getGoalDoor();
                    if (door != null && !door.isChameleonFalling()) {
                            setVictory();
                            waitingForDoorAnim = false;
                        }
                }

        physics.update(dt);
    }

    /**
     * Draw the paint container UI with a solid color fill.
     */
    private void drawPaintContainer(Texture bar, Texture barOverlay) {
        if (player == null) {
            return;
        }

        batch.setProjectionMatrix(uiCamera.combined);

        float heightRatio = constants.get("paintBar").getFloat("height");
        float widthRatio = constants.get("paintBar").getFloat("width");
        float posXRatio = constants.get("paintBar").getFloat("posX");
        float posYRatio = constants.get("paintBar").getFloat("posY");
        float textRatio = constants.get("paintBar").getFloat("textRatio");
        float textOffset = constants.get("paintBar").getFloat("textOffset");
        float paintPercent = player.getPaint() / player.getMaxPaint();
        float currentBarHeight = paintPercent * heightRatio;

        batch.draw(barOverlay, width * posXRatio, height * posYRatio, width * widthRatio,
            height * heightRatio);

        if (paintPercent > 0.5f) {
            batch.setColor(Color.WHITE);
        } else if (paintPercent > 0.2f) {
            batch.setColor(Color.YELLOW);
        } else {
            batch.setColor(Color.RED);
        }

        batch.draw(bar, width * posXRatio, height * posYRatio, width * widthRatio,
            height * currentBarHeight);

        displayFont.getData().setScale(width * widthRatio / textRatio);
        String paintText = String.format("%.0f%%", paintPercent * 100);
        batch.drawText(paintText, displayFont, width * posXRatio,
            height * (posYRatio - textOffset));

        batch.setColor(Color.WHITE);
    }

//    /**
//     * Debug helper to see all tiles and coordinates labelled in debug view. Uncomment call in
//     * 'draw' method to view.
//     */
//    private void drawMapCoords(SpriteBatch batch) {
//        BitmapFont font = new BitmapFont(); // Default font
//        font.setColor(Color.WHITE); // Set color to white for visibility
//        font.getData().setScale(0.5f); // Scale down text if needed
//
//        for (int x = 0; x < worldWidth; x++) {
//            for (int y = 0; y < worldHeight; y++) {
//                float tileX = x * units;
//                float tileY = y * units;
//                String coordText = "(" + x + "," + y + ")";
//
//                font.draw(batch, coordText, tileX, tileY);
//            }
//        }
//    }

    /**
     * Draws the Win screen UI.
     */
    private void drawWinScreen(Texture youWinTexture) {
        batch.setColor(Color.WHITE); // Just in case you changed it elsewhere
        batch.draw(youWinTexture, 0, 0, width, height);

        // Optional: text overlay if needed
        // batch.drawText(goodMessage, width / 2, height / 2);

        drawButton(batch, retryButton);
        drawButton(batch, menuButton);
        drawButton(batch, nextButton);
    }

    /**
     * Draws the Lose screen UI.
     */
    private void drawLoseScreen(Texture youLoseTexture) {
        batch.setColor(Color.WHITE);
        batch.draw(youLoseTexture, 0, 0, width, height);

        // Optional: text overlay if needed
        // batch.drawText(badMessage, width / 2, height / 2);

        drawButton(batch, retryButton);
        drawButton(batch, menuButton);
    }

    private void drawButton(SpriteBatch batch, Rectangle rect) {
        // Optional: Draw button background (replace with your own texture if available)
        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        batch.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1.0f, 1.0f, 1.0f, 1); // dark gray
        shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height);
        shapeRenderer.end();
        batch.begin();
    }

    /**
     * Main draw method.
     */
    private void draw(float dt) {

        ScreenUtils.clear(new Color(0.12f, 0.16f, 0.2f, 1f));
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Draw tiled background
        if (level.getBackgroundTiles() != null) {
            for (BackgroundTile tile : level.getBackgroundTiles()) {
                tile.draw(batch);
            }
        }

        batch.flush();
        batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        // Draw all bombs
        for (ObstacleSprite sprite : physics.objects) {
            if (sprite.getName() != null && sprite.getName().equals("bomb")) {
                Bomb bomb = (Bomb) sprite.getObstacle().getUserData();
                if (bomb != null && !bomb.isFlying()) {
                    bomb.draw(batch);
                }
            }
        }
        batch.flush();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        for (ObstacleSprite sprite : physics.objects) {
            if (sprite.getName() != null && sprite.getName().equals("spray")) {
                sprite.draw(batch);
            }
        }

        if (level.getWallsNoCover() != null) {
            for (BackgroundTile tile : level.getWallsNoCover()) {
                tile.draw(batch);
            }
        }

        if (level.getGrates() != null) {
            for (Grate grate : level.getGrates()) {
                grate.draw(batch);
            }
        }

        if (level.getGoalDoor() != null) {
            level.getGoalDoor().draw(batch);
        }

        batch.setColor(Color.WHITE);
        batch.setTexture(null);


        for (ObstacleSprite sprite : physics.objects) {
            if (sprite.getName() != null && sprite.getName().equals("enemy")) {
                sprite.draw(batch);
            }
        }
        for (AIController ai : aiControllers) {
            int frameIndex = ai.getEnemy().getAlertAnimationFrame(); // you set this from the AI logic

            if (frameIndex != -1) {
                TextureRegion frame = ai.getEnemy().getAlertAnimation().getKeyFrames()[frameIndex];

                float drawWidth = frame.getRegionWidth() * ai.getEnemy().getDrawScale() * 2;
                float drawHeight = frame.getRegionHeight() * ai.getEnemy().getDrawScale() * 2;
                float px = ai.getEnemy().getPosition().x * units;
                float py = ai.getEnemy().getPosition().y * units;

                float hoverOffsetPixels = 40f;  // same as the enemies
                batch.draw(frame,
                    px - drawWidth / 2,
                    py - drawHeight / 2 + hoverOffsetPixels,
                    drawWidth / 2, drawHeight / 2,
                    drawWidth, drawHeight,
                    1, 1,
                    0);
            }
        }
        if (levelSelector.getCurrentLevel() == 2) {
            Texture hintTex = directory.getEntry("tutorialhints2", Texture.class);
            float worldX = 2f * units;
            float worldY = 10f * units;
            batch.draw(hintTex,
                worldX, worldY,
                hintTex.getWidth(),
                hintTex.getHeight());
        }
        if (levelSelector.getCurrentLevel() == 1) {
            Texture hintTex = directory.getEntry("tutorialhints3", Texture.class);
            float worldX = 2f * units;
            float worldY = 17f * units;
            batch.draw(hintTex,
                worldX, worldY,
                hintTex.getWidth(),
                hintTex.getHeight());
        }
        if (levelSelector.getCurrentLevel() == 3) {
            Texture hintTex = directory.getEntry("tutorialhints1", Texture.class);
            float worldX = 2f * units;
            float worldY = 19f * units;
            batch.draw(hintTex,
                worldX, worldY,
                hintTex.getWidth(),
                hintTex.getHeight());
        }
        player.draw(batch);
        batch.end();
        for (AIController aiController : aiControllers) {
            if (aiController.getEnemy().getType() == Enemy.Type.CAMERA1) {
                aiController.drawEnemyVision(camera);
            }
        }
        batch.begin();
        for (Laser laser : level.getLasers()) {
            laser.draw(batch);
        }
        if (level.getWallsCover() != null) {
            for (BackgroundTile tile : level.getWallsCover()) {
                tile.draw(batch);
            }
        }

        // Draw goal tiles
        if (level.getGoalTiles() != null) {
            for (BackgroundTile tile : level.getGoalTiles()) {
                tile.draw(batch);
            }
        }
        if (level.getGoal2Tiles() != null) {
            for (BackgroundTile tile : level.getGoal2Tiles()) {
                tile.draw(batch);
            }
        }
        if (level.getGoal3Tiles() != null) {
            for (BackgroundTile tile : level.getGoal3Tiles()) {
                tile.draw(batch);
            }
        }
        if (level.getWallsTop() != null) {
            for (BackgroundTile tile : level.getWallsTop()) {
                tile.draw(batch);
            }
        }

        for (ObstacleSprite sprite : physics.objects) {
            if (sprite.getName() != null && sprite.getName().equals("goal")) {
                sprite.draw(batch);
            }
        }

//        batch.flush();


//        batch.flush();
//        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
// ───── new bomb ──────────────────────────
        if (
            bombState == BombSkillState.READY ||
                bombState == BombSkillState.PAINTING) {
            float r = aimRangeCurrent * units;
            Vector2 p = player.getPosition();
            batch.end();
            Gdx.gl.glLineWidth(3f);
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(Color.PINK);
            shapeRenderer.circle(p.x * units, p.y * units, r / 2, 64);
            shapeRenderer.end();
            Gdx.gl.glLineWidth(1f);
            batch.begin();
        }

        if (bombState == BombSkillState.PAINTING) {
            Texture ghost = directory.getEntry("aiming-range", Texture.class);
            ghost.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            float s = constants.get("bomb").getFloat("size") * units;
            for (Vector2 phys : planned) {
                batch.draw(ghost, phys.x * units - s / 2, phys.y * units - s / 2, s, s);
            }
            Vector3 raw = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(raw);
            Vector2 curPix = clampBombPos(raw, aimRangeCurrent);
            batch.draw(ghost, curPix.x - s / 2, curPix.y - s / 2, s, s);
        }
        for (ObstacleSprite sprite : physics.objects) {
            if ("bomb".equals(sprite.getName())) {
                Bomb bomb = (Bomb) sprite.getObstacle().getUserData();
                if (bomb != null && bomb.isFlying()) {
                    bomb.draw(batch);
                }
            }
        }
        batch.end();
        for (AIController aiController : aiControllers) {
            if (aiController.getEnemy().getType() == Enemy.Type.CAMERA2) {
                aiController.drawEnemyVision(camera);
            }
        }
        batch.begin();
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
//                if (aiController.getEnemy().getType() != Enemy.Type.CAMERA) {
                aiController.debugRender(camera); // Call debug grid rendering
//                }
            }
            batch.begin();// Resume SpriteBatch rendering
        }

        // Draw the paint container (UI) after objects
        Texture barTex = directory.getEntry("paintBar", Texture.class);
        Texture barOverlayTex = directory.getEntry("paintBar-overlay", Texture.class);
        barTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        barOverlayTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        drawPaintContainer(barTex, barOverlayTex);

        Texture youWinTexture = directory.getEntry("win-screen", Texture.class);
        Texture youLoseTexture =  directory.getEntry("lose-screen", Texture.class);
        youWinTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        youLoseTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        if (gameState == GameState.WON) {
            drawWinScreen(youWinTexture);
        } else if (gameState == GameState.LOST) {
            // only pop up the lose UI once we've zoomed all the way in
            if (Math.abs(cameraZoom - targetZoom) < 0.01f) {
                drawLoseScreen(youLoseTexture);
            }
        }


//        // UI messages
//        if (complete && !failed) {
//            batch.drawText(goodMessage, width / 2, height / 2);
//        } else if (failed) {
//            batch.drawText(badMessage, width / 2, height / 2);
//        }

        // ——— Goal-region percentage notification ———
        int numFilled = 0;
        List<Goal> goals1 = physics.getGoalList();
        List<Goal> goals2 = physics.getGoal2List();
        List<Goal> goals3 = physics.getGoal3List();

        for (Goal g : goals1) {
            if (g != null && g.isFull()) {
                numFilled++;

            }
        }
        for (Goal g : goals2) {
            if (g != null && g.isFull()) {
                numFilled++;
            }
        }
        for (Goal g : goals3) {
            if (g != null && g.isFull()) {
                numFilled++;
            }
        }

// compute percent painted
        float pct = (float) numFilled / ((float) goals1.size() + (float) goals2.size()
            + (float) goals3.size()) * 100f;
        if(Float.isNaN(pct)){
            pct = 100;
        }

// update the TextLayout
        goalMessage.setText(String.format("Goal Painted: %.0f%%", pct));
        goalMessage.setColor(Color.YELLOW);
        goalMessage.setAlignment(TextAlign.middleCenter);
        goalMessage.setFont(displayFont);

// draw it at the top center, 20px down from the top
        batch.drawText(goalMessage, width / 2, height - 20);

        batch.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * Keeps the camera centered on the player and guarantees that the visible world area stays the
     * same even when the window is resized.
     */
    private void updateCamera() {
        Vector2 pos = player.getObstacle().getPosition();
        camera.position.set(pos.x * units, pos.y * units, 0);

        // smooth‐lerp to targetZoom on both zoom in and zoom out
        cameraZoom += (targetZoom - cameraZoom) *
            Math.min(1, ZOOM_LERP * Gdx.graphics.getDeltaTime());

    /* -----------------------------------------------
       Window‑size compensation
       ----------------------------------------------- */
        float zoomAdjust = (float) baseWidth / width;   // width is current window width
        camera.zoom = cameraZoom * zoomAdjust;

        camera.update();
    }

    /**
     * The main render loop.
     */
    @Override
    public void render(float delta) {
        if (!active) {
            return;
        }
        if (!preUpdate(delta)) {
            return;
        }

//        if (gameState != GameState.PLAYING && Gdx.input.justTouched()) {
//            Vector3 touch = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
//            camera.unproject(touch); // if you’re using a camera
//
//            if (retryButton.contains(touch.x, touch.y)) {
//                reset();
//                gameState = GameState.PLAYING;
//            } else if (menuButton.contains(touch.x, touch.y)) {
//                listener.exitScreen(this, EXIT_MAP);
//            } else if (nextButton.contains(touch.x, touch.y) && gameState == GameState.WON) {
//                listener.exitScreen(this, EXIT_NEXT);
//            }
//        }

        float frameTime = Math.min(delta, 0.25f);
        accumulator += frameTime;
        final float FIXED_TIMESTEP = 1 / 120f;

        while (accumulator >= FIXED_TIMESTEP) {
            update(FIXED_TIMESTEP);
            postUpdate(FIXED_TIMESTEP);
            accumulator -= FIXED_TIMESTEP;
        }
        float alpha = accumulator / FIXED_TIMESTEP;
        draw(alpha);
    }

    // Screen interface methods
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
        units = 16;

        // 2) The InputController scale for screen→world
        //    scale.x = (float) screenWidth / worldWidth
        //    scale.y = (float) screenHeight / worldHeight
        scale.x = (this.width == 0) ? 1.0f : ((float) this.width / worldWidth);
        scale.y = (this.height == 0) ? 1.0f : ((float) this.height / worldHeight);
        // Rebuild the world for the new scale
//        reset();
    }

    @Override
    public void dispose() {
        physics.dispose();
        shapeRenderer.dispose();
    }

    public void setScreenListener(ScreenListener listener) {
        this.listener = listener;
    }

    public void setSpriteBatch(SpriteBatch batch) {
        this.batch = batch;
    }

    private void setVictory() {
        complete = true;
        gameState = GameState.WON;
        countdown = EXIT_COUNT;
        if (alertSoundPlaying) {
            enemiesAlertSound.stop(alertSoundId);
            alertSoundPlaying = false;
        }
    }

    private void setFailure(boolean value) {
        if (value) {
            countdown = EXIT_COUNT;
            // immediately start zooming in on the chameleon
            targetZoom = ZOOM_FOCUS;
        }
        failed = value;
        gameState = GameState.LOST;
        if (alertSoundPlaying) {
            enemiesAlertSound.stop(alertSoundId);
            alertSoundPlaying = false;
        }
    }

    public OrthographicCamera getCamera() {
        return camera;
    }

    public float getWorldWidth() {
        return worldWidth;
    }

    public float getWorldHeight() {
        return worldHeight;
    }

    public boolean isGlobalChase() {
        return globalChase;
    }

    public float getUnits() {
        return units;
    }
}
