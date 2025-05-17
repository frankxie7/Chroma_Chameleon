package chroma.model;

import chroma.controller.InputController;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

import com.badlogic.gdx.physics.box2d.BodyDef;

import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.graphics.Texture2D;
import edu.cornell.gdiac.math.Path2;
import edu.cornell.gdiac.physics2.CapsuleObstacle;
import edu.cornell.gdiac.physics2.ObstacleSprite;

public class Chameleon extends ObstacleSprite {
    /** The initializing data (to avoid magic numbers) */
    private final JsonValue data;
    /** The width of Chameleon's avatar */
    private float width;
    /** The height of Traci's avatar */
    private float height;

    /** The factor to multiply by the input */
    private float force;
    /** The amount to slow the character down */
    private float damping;
    /** The maximum character speed */
    private float maxspeed;
    private float currentMaxSpeed;

    /** Cooldown (in animation frames) for shooting */
    private int shotLimit;

    /** The current horizontal movement of the character */
    private float movement;
    /** The current vertical movement of the character */
    private float verticalMovement;

    /** How long until we can shoot again */
    private int shootCooldown;
    /** Whether we are actively shooting */
    private boolean isShooting;
    /** Whether we are actively aiming */
    private boolean isAiming;

    private float maxPaint;
    private float currentPaint = maxPaint;

    /** The outline of the sensor obstacle */
    private Path2 sensorOutline;
    /** The debug color for the sensor */
    private Color sensorColor;
    /** The name of the sensor fixture */
    private String sensorName;

    /** Cache for internal force calculations */
    private final Vector2 forceCache = new Vector2();
    /** Cache for the affine flip */
    private final Affine2 flipCache = new Affine2();

    private boolean translucent = false;

    //Position
    private Vector2 position;

    private float orientation = 0.0f;
    private boolean hidden;
    private Vector2 lastSeen;
    private boolean faceRight = true;
    private boolean faceUp = false;
    private boolean faceLeft = false;
    private boolean faceDown = false;
    private enum Direction { UP, DOWN, LEFT, RIGHT }
    private Direction lastDirection = Direction.RIGHT;
    private boolean falling = false;

    private Animation<TextureRegion> walkAnim;
    private Animation<TextureRegion> upWalkAnim;
    private Animation<TextureRegion> downWalkAnim;
    // new, alongside your other Animations
    private Animation<TextureRegion> idleAnim;

    private float animTime;
    private TextureRegion currentFrame;
    private Music walkSound;

//    private Animation<TextureRegion> bombAnim;
//    private float  bombAnimTime  = 0f;
//    private boolean bombPlaying  = false;
//    private boolean bombPaused = false;

    private float drawScale;

    /* ---------- bomb animations ---------- */
    private Animation<TextureRegion> bombWindup;
    private Animation<TextureRegion> bombShoot;
    private Animation<TextureRegion> bombWinddown;
    private Animation<TextureRegion> pinkBombWindup;
    private Animation<TextureRegion> pinkBombShoot;
    private Animation<TextureRegion> pinkBombWinddown;
    private Animation<TextureRegion> upBombWindup,   upBombShoot,   upBombWinddown;
    private Animation<TextureRegion> downBombWindup, downBombShoot, downBombWinddown;

    private Animation<TextureRegion> upPinkBombWindup;
    private Animation<TextureRegion> upPinkBombShoot;
    private Animation<TextureRegion> upPinkBombWinddown;

    private Animation<TextureRegion> downPinkBombWindup;
    private Animation<TextureRegion> downPinkBombShoot;
    private Animation<TextureRegion> downPinkBombWinddown;
    // Spray fields
    private Animation<TextureRegion> sprayAnim;
    private float             sprayTime    = 0f;
    private boolean           sprayPlaying = false;


    private enum BombPhase { NONE, WINDUP, SHOOT, WINDDOWN }
    private BombPhase bombPhase = BombPhase.NONE;
    private float bombTime = 0f;

// keep the setters you already have …

    public boolean isBombPlaying() {            // NEW helper for controller
        return bombPhase != BombPhase.NONE;
    }

    /** advance the three-phase state machine and pick the video frame */


    /**
     * Returns the posiiton of the Chameleon
     * @return position the position of the chameleon
     */
    public Vector2 getPosition(){return position;}

    /**
     * Returns the left/right movement of this character.
     *
     * This is the result of input times force.
     *
     * @return the left/right movement of this character.
     */
    public float getMovement() {
        return movement;
    }
    /**
     * Sets the left/right movement of this character.
     *
     * This is the result of input times force.
     *
     * @param value the left/right movement of this character.
     */
    public void setMovement(float value) {
        movement = value;
        // Change facing if appropriate
        if (movement < 0) {
            faceRight = false;
        } else if (movement > 0) {
            faceRight = true;
        }
    }
    public float getVerticalMovement() {return verticalMovement;}
    public void setVerticalMovement(float value) {
        verticalMovement = value;
    }

    public void setFalling(boolean falling) {
        this.falling = falling;
    }

    /**
     * Returns true if Traci is actively firing.
     *
     * @return true if Traci is actively firing.
     */
    public boolean isShooting() {
        return isShooting && shootCooldown <= 0;
    }

    /**
     * Sets whether Traci is actively firing.
     *
     * @param value whether Traci is actively firing.
     */
    public void setShooting(boolean value) {
        isShooting = value;
    }

    /**
     * Returns true if the player is aiming
     * */
    public boolean isAiming() {return isAiming;}

    /**
     * Sets whether the player is aiming
     * */
    public void setAiming(boolean value) { isAiming = value;}

    /**
     * Returns how much force to apply to get Traci moving
     *
     * Multiply this by the input to get the movement value.
     *
     * @return how much force to apply to get Traci moving
     */
    public float getForce() {
        return force;
    }

    /**
     * Returns how hard the brakes are applied to stop Traci moving
     *
     * @return how hard the brakes are applied to stop Traci moving
     */
    public float getDamping() {
        return damping;
    }

    /**
     * Returns the upper limit on Traci's left-right movement.
     *
     * This does NOT apply to vertical movement.
     *
     * @return the upper limit on Traci's left-right movement.
     */
    public float getMaxSpeed() {
        return currentMaxSpeed;
    }

    /**
     * Returns the name of the ground sensor
     *
     * This is used by the ContactListener. Because we do not associate the
     * sensor with its own obstacle,
     *
     * @return the name of the ground sensor
     */
    public String getSensorName() {
        return sensorName;
    }

    /**
     * Returns true if this character is facing right
     *
     * @return true if this character is facing right
     */
    public boolean isFacingRight() {
        return faceRight;
    }

    public boolean isFaceUp(){
        return faceUp;
    }

    public boolean isFaceLeft(){
        return faceLeft;
    }

    public boolean isFaceDown(){
        return faceDown;
    }

    /** Called by the controller to turn half-see-through on or off. */
    public void setTranslucent(boolean t) {
        this.translucent = t;
    }
    public void setPosition(float x, float y) {
        if (obstacle != null && obstacle.getBody() != null) {
            obstacle.setPosition(x, y);
            position = new Vector2(x, y);
        }
    }

    /**
     * Sets the position of the Chameleon.
     *
     * This updates both the position cache and the physics body.
     *
     * @param pos The new position
     */
    public void setPosition(Vector2 pos) {
        setPosition(pos.x, pos.y);
    }

    /**
     * Creates a new Traci avatar with the given physics data
     *
     * The physics units are used to size the mesh relative to the physics
     * body. All other attributes are defined by the JSON file. Because of
     * transparency around the image file, the physics object will be slightly
     * thinner than the mesh in order to give a tighter hitbox.
     *
     * @param units     The physics units
     * @param dataGlobal      The physics constants for Traci
     */
    public Chameleon(float units, JsonValue dataGlobal, JsonValue dataLevel,
                     Animation<TextureRegion> animation,
                     Animation<TextureRegion> upWalkAnim,
                     Animation<TextureRegion> downWalkAnim,
                     Music walkSound,
        Animation<TextureRegion> idleAnim) {
        this.data = dataGlobal;
        JsonValue debugInfo = dataGlobal.get("debug");

        float x = dataLevel.get("pos").getFloat(0);
        float y = dataLevel.get("pos").getFloat(1);
        this.lastSeen = new Vector2(x, y);

        maxPaint = dataLevel.getFloat("paint");

        float s = dataGlobal.getFloat("size");
        float size = s * units;
        drawScale = dataGlobal.getFloat("drawScale");

        // Create a capsule obstacle
        obstacle = new CapsuleObstacle(x, y, s * dataGlobal.get("inner").getFloat(0), s * dataGlobal.get("inner").getFloat(1));
        ((CapsuleObstacle)obstacle).setTolerance(debugInfo.getFloat("tolerance", 0.5f));

        // Ensure the body is dynamic so it can move.
        obstacle.setBodyType(BodyDef.BodyType.DynamicBody);
        obstacle.setFixedRotation(true);
        obstacle.setPhysicsUnits(units);
        obstacle.setUserData(this);
        obstacle.setName("chameleon");


        // Set up debug colors.
        debug = ParserUtils.parseColor(debugInfo.get("avatar"), Color.WHITE);
        sensorColor = ParserUtils.parseColor(debugInfo.get("sensor"), Color.WHITE);

        maxspeed = dataGlobal.getFloat("maxspeed", 0);
        currentMaxSpeed = maxspeed;
        damping = dataGlobal.getFloat("damping", 0);
        force = dataGlobal.getFloat("force", 0);

        shotLimit = dataGlobal.getInt("shot_cool", 0);

        isShooting = false;
        isAiming = false;
        faceRight = true;
        shootCooldown = 0;

        // Create a rectangular mesh for the chameleon.
        mesh.set(-size / 2.0f, -size / 2.0f, size, size);
//        int count = mesh.vertexCount(); // or however you get the vertex count
//        for (int i = 0; i < count; i++) {
//            mesh.setColor(i, Color.PURPLE);
//        }

        this.walkAnim = animation;
        this.upWalkAnim = upWalkAnim;
        this.downWalkAnim = downWalkAnim;
        this.idleAnim     = idleAnim;
        animTime = 0;
        TextureRegion[] frames = (TextureRegion[]) walkAnim.getKeyFrames();
        currentFrame = frames[6];
        this.walkSound = walkSound;
    }

    /**
     * Applies the force to the body of Traci
     *
     * This method should be called after the force attribute is set.
     */
    public void applyForce() {
        if (!obstacle.isActive()) {
            return;
        }

        Vector2 pos = obstacle.getPosition();
        float vx = obstacle.getVX();
        float vy = obstacle.getVY();
        Body body = obstacle.getBody();
        position = obstacle.getPosition();


        float moveX = getMovement();
        float moveY = getVerticalMovement();

        // Don't want to be moving. Damp out player motion
        if (moveX == 0f && moveY == 0f) {
            forceCache.set(-getDamping()*vx,-getDamping()*vy);
            body.applyForce(forceCache,pos,true);
        } else {
            Vector2 moveForce = new Vector2(moveX, moveY);
            if (moveForce.len() > 1) {
                moveForce.nor(); // Normalize so diagonal movement isn't faster
            }
            moveForce.scl(getForce()); // Scale by movement force
            body.applyForce(moveForce, pos, true);
        }

        // Clamp velocity to max speed
        Vector2 velocity = obstacle.getBody().getLinearVelocity();
        if (velocity.len() > getMaxSpeed()) {
            velocity.nor().scl(getMaxSpeed());
            obstacle.getBody().setLinearVelocity(velocity);
        }
    }

    /**
     * Updates the object's physics state (NOT GAME LOGIC).
     *
     * We use this method to reset cooldowns.
     *
     * @param dt    Number of seconds since last animation frame
     */
    @Override
    public void update(float dt) {
        if (falling) {
            setMovement(0f);
            setVerticalMovement(0f);
            setShooting(false);
            setAiming(false);
            walkSound.stop();
            applyForce();
            return;
        }
        InputController input = InputController.getInstance();

        updateBomb(dt);
        // Update player (chameleon) movement based on input
        float hmove = input.getHorizontal();
        float vmove = input.getVertical();
        setMovement(hmove * getForce());
        setVerticalMovement(vmove * getForce());
        setShooting(input.didLeftClick());
        applyForce();

        // Apply cooldowns
        if (isShooting()) {
            shootCooldown = shotLimit;
        } else {
            shootCooldown = Math.max(0, shootCooldown - 1);
        }

//        stepBombAnim(dt);
//        if (bombPlaying) {
//            super.update(dt);
//            return;
//        }
        if (isBombPlaying()) {
            super.update(dt);           // still step the physics body
            return;                     // skip walk animation changes
        }
        if (sprayPlaying) {
            // keep physics running
            super.update(dt);

            // advance and grab the splat frame
            sprayTime += dt;
            currentFrame = sprayAnim.getKeyFrame(sprayTime, /* looping= */ false);

            // once it’s done, turn it off
            if (sprayAnim.isAnimationFinished(sprayTime)) {
                sprayPlaying = false;
            }
            // skip all of the walk/idle logic below
            return;
        }

        if (hmove > 0) {
            faceRight = true;
            faceUp = false;
            faceLeft = false;
            faceDown = false;
            lastDirection = Direction.RIGHT;
            obstacle.setAngle(0f);
        } else if (hmove < 0) {
            faceRight = false;
            faceUp = false;
            faceLeft = true;
            faceDown = false;
            lastDirection = Direction.LEFT;
            obstacle.setAngle(0f);
        } else if (vmove > 0) {
            faceRight = false;
            faceLeft = false;
            faceDown = false;
            faceUp = true;
            lastDirection = Direction.UP;
        } else if (vmove < 0) {
            faceUp = false;
            faceDown = true;
            faceLeft = false;
            faceRight = false;
            lastDirection = Direction.DOWN;
        }
        /* ---- choose the correct animation bank (normal vs pink) ---- */
        Animation<TextureRegion> bankIdle     = isHidden() ? pinkIdleAnim     : idleAnim;
        Animation<TextureRegion> bankWalk     = isHidden() ? pinkWalkAnim     : walkAnim;
        Animation<TextureRegion> bankUpWalk   = isHidden() ? pinkUpWalkAnim   : upWalkAnim;
        Animation<TextureRegion> bankDownWalk = isHidden() ? pinkDownWalkAnim : downWalkAnim;

        if (hmove == 0 && vmove == 0) {
            // 1) Facing UP? keep original static UP frame
            if (lastDirection == Direction.UP) {
                currentFrame = bankUpWalk.getKeyFrame(0f, false);
                animTime = 0f;
            }
            // 2) Facing DOWN? keep original static DOWN frame
            else if (lastDirection == Direction.DOWN) {
                currentFrame = bankDownWalk.getKeyFrame(0f, false);
                animTime = 0f;
            }
            // 3) LEFT or RIGHT (or anything else): play your two‑frame idle loop
            else {
                animTime += dt;
                currentFrame = bankIdle.getKeyFrame(animTime, true);
            }
            walkSound.stop();
        } else {
            animTime += dt;

            walkSound.play();

            if (Math.abs(vmove) > Math.abs(hmove)) {
                if (vmove > 0) {
                    obstacle.setAngle(1.57f);
                    currentFrame = bankUpWalk.getKeyFrame(animTime, true);
                } else {
                    obstacle.setAngle(1.57f);
                    currentFrame = bankDownWalk.getKeyFrame(animTime, true);
                }
            } else {
                currentFrame = bankWalk.getKeyFrame(animTime, true);
            }
        }
        // Then call the superclass update
        super.update(dt);
//        stepBombAnim(dt);
    }

    /**
     * Draws the physics object.
     *
     * This method is overridden from ObstacleSprite. We need to flip the
     * texture back-and-forth depending on her facing. We do that by creating
     * a reflection affine transform.
     *
     * @param batch The sprite batch to draw to
     */
    @Override
    public void draw(SpriteBatch batch) {
        if (falling) return;
        // Save the current color of the batch
        Color target = Color.WHITE;
//        float alpha = translucent ? 0.2f : 1.0f;
        batch.setColor(target);
//        Color c = batch.getColor();
//        batch.setColor(c.r, c.g, c.b, alpha);
        // Update the mesh vertex colors dynamically.
        int count = mesh.vertexCount();
        for (int i = 0; i < count; i++) {
            mesh.setColor(i, target);
        }

        // Create an Affine2 transform for rotation
        Affine2 transform = new Affine2();
        transform.setToRotation(orientation);


//        Rectangle bounds = mesh.computeBounds();
        float drawWidth  = currentFrame.getRegionWidth() * drawScale;
        float drawHeight = currentFrame.getRegionHeight() * drawScale;
        float px = obstacle.getX() * obstacle.getPhysicsUnits();
        float py = obstacle.getY() * obstacle.getPhysicsUnits();

        if (faceRight || faceLeft) {
            drawWidth *= 0.95f;
        }else{
            drawWidth *= 1.05f;
        }
        if (faceRight) {
            if (currentFrame.isFlipX()) {
                currentFrame.flip(true, false);
            }
        } else {
            if (!currentFrame.isFlipX()) {
                currentFrame.flip(true, false);
            }
        }
        float manualYOffset = 10f; // ← tweak this value until it sits just right
        float manualXOffset = 2f;
        float yOffset = 0f;
        float xOffset = 0f;
        float extraShootOffset = 5.5f;
        if (isBombPlaying() && lastDirection == Direction.UP) {
            yOffset = manualYOffset;
        }
        if (isBombPlaying() && lastDirection == Direction.DOWN) {
            yOffset = -manualYOffset;
        }
        if (isBombPlaying() && lastDirection == Direction.LEFT) {
            xOffset = -manualXOffset;
            if (bombPhase == BombPhase.SHOOT) {
                xOffset += extraShootOffset;  // e.g. +8f or -8f depending on art
            }
        }
        if (isBombPlaying() && lastDirection == Direction.RIGHT) {
            xOffset = manualXOffset;
            if (bombPhase == BombPhase.SHOOT) {
                xOffset -= extraShootOffset;  // e.g. +8f or -8f depending on art
            }
        }
//        Vector2 p = getPosition();
//        float worldPx = p.x * 16f;
//        float worldPy = p.y * 16f;
//        Gdx.app.log("SprayDebug", "Player = (" + worldPx + ", " + worldPy + ")");
        batch.draw(currentFrame,
            px - drawWidth / 2 + xOffset,
            py - drawHeight / 2 + yOffset,
            drawWidth / 2, drawHeight / 2,
            drawWidth, drawHeight,
            1, 1,
            0);
        batch.setColor(Color.WHITE);
    }


    /**
     * Draws the outline of the physics object.
     *
     * This method is overridden from ObstacleSprite. By default, that method
     * only draws the outline of the main physics obstacle. We also want to
     * draw the outline of the sensor, and in a different color. Since it
     * is not an obstacle, we have to draw that by hand.
     *
     * @param batch The sprite batch to draw to
     */
    @Override
    public void drawDebug(SpriteBatch batch) {
        super.drawDebug( batch );

        if (sensorOutline != null) {
            batch.setTexture( Texture2D.getBlank() );
            Color original = batch.getColor();
            batch.setColor( sensorColor );

            Vector2 p = obstacle.getPosition();
            float a = obstacle.getAngle();
            float u = obstacle.getPhysicsUnits();

            // transform is an inherited cache variable
            transform.idt();
            transform.preRotate( (float) (a * 180.0f / Math.PI) );
            transform.preTranslate( p.x * u, p.y * u );

            batch.outline( sensorOutline, transform );
            batch.setColor(original);
        }
    }

    // For asset drawing
    public void updateOrientation() {
        Body body = obstacle.getBody();
        if (body != null) {
            Vector2 vel = body.getLinearVelocity();
            if (vel.len2() > 0.0001f) { // Only update if velocity is significant
                orientation = (float) Math.toDegrees(Math.atan2(vel.y, vel.x));
            }
        }
    }
    /** Abort the bomb animation immediately and return to the normal set. */
    public void cancelBomb() {
        bombPhase = BombPhase.NONE;
        bombTime  = 0f;
    }


//    public void setBombAnimation(Animation<TextureRegion> anim){
//        this.bombAnim = anim;
//    }
//
//    private void stepBombAnim(float dt) {
//        if (!bombPlaying || bombAnim == null) { return; }
//        if (!bombPaused) {
//            bombAnimTime += dt;
//        }
//        currentFrame = bombAnim.getKeyFrame(bombAnimTime, false);
//        if (bombAnim.isAnimationFinished(bombAnimTime)) {
//            bombPlaying  = false;
//            bombAnimTime = 0f;
//            bombPaused   = false;
//            resetToIdleFrame();
//        }
//    }
    private void resetToIdleFrame() {
        animTime = 0f;
        switch (lastDirection) {
            case UP:
                currentFrame = upWalkAnim.getKeyFrame(0f, false);
                break;
            case DOWN:
                currentFrame = downWalkAnim.getKeyFrame(0f, false);
                break;
            default:
                currentFrame = walkAnim.getKeyFrame(0f, false);
                break;
        }
    }
//    public void startBombAnimation() {
//        bombPlaying  = true;
//        bombPaused   = false;    // reset pause
//        bombAnimTime = 0f;
//    }
//    public boolean isBombPlaying() {
//        return bombPlaying;
//    }
//    public void pauseBomfbAnimation() {
//        bombPaused = true;
//    }
//
//    public void resumeBombAnimation() {
//        bombPaused = false;
//    }
//    public void advanceBombFrame(int maxFrame) {
//        if (!bombPlaying || bombAnim == null) return;
//
//        float frameDur   = bombAnim.getFrameDuration();
//        int   currIndex  = (int)(bombAnimTime / frameDur);
//        if (currIndex >= maxFrame) return;                 // already at cap
//
//        bombAnimTime = Math.min(bombAnimTime + frameDur,
//            maxFrame * frameDur);
//        currentFrame = bombAnim.getKeyFrame(bombAnimTime, false);
//    }
public void setSprayAnimation(Animation<TextureRegion> anim) {
    this.sprayAnim = anim;
}

    public void triggerSpray() {
        sprayTime    = 0f;
        sprayPlaying = true;
    }

    /**
     * Returns the primary fixture associated with this game object.
     * This fixture can be used for raycasting.
     *
     * @return the first fixture from the physics body, or null if not available.
     */
    public Fixture getFixture() {
        if (obstacle.getBody() != null && obstacle.getBody().getFixtureList().size > 0) {
            return obstacle.getBody().getFixtureList().first();
        }
        return null;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public float getPaint() {
        return currentPaint;
    }

    public float getMaxPaint() {
        return maxPaint;
    }

    public void setPaint(float paint) {
        this.currentPaint = Math.max(0, Math.min(maxPaint, paint));
    }

    public boolean hasEnoughPaint(float cost) {
        return currentPaint >= cost;
    }

    public Vector2 getLastSeen() { return lastSeen; }
    public void setLastSeen(Vector2 pos) { this.lastSeen = pos; }

    public void setMaxSpeed (float factor) { this.currentMaxSpeed = factor * maxspeed;}



    public void setBombAnimations(Animation<TextureRegion> wind, Animation<TextureRegion> shoot,
        Animation<TextureRegion> down) {
        this.bombWindup   = wind;
        this.bombShoot    = shoot;
        this.bombWinddown = down;
    }

    public void startBombWindup()   { bombPhase = BombPhase.WINDUP;   bombTime = 0; }
    public void startBombShoot()    { bombPhase = BombPhase.SHOOT;    bombTime = 0; }
    public void startBombWinddown() { bombPhase = BombPhase.WINDDOWN; bombTime = 0; }

//    public boolean isBombPlaying()  { return bombPhase != BombPhase.NONE; }

    private void updateBomb(float dt) {
        if (bombPhase == BombPhase.NONE) return;
        bombTime += dt;

        // 1) Select the correct set of animations for this direction:
        Animation<TextureRegion> windupAnim, shootAnim, winddownAnim;
        if (isHidden()) {
            // 隐身时：再按方向分三套粉色动画
            if (lastDirection == Direction.UP) {
                windupAnim   = upPinkBombWindup;
                shootAnim    = upPinkBombShoot;
                winddownAnim = upPinkBombWinddown;
            } else if (lastDirection == Direction.DOWN) {
                windupAnim   = downPinkBombWindup;
                shootAnim    = downPinkBombShoot;
                winddownAnim = downPinkBombWinddown;
            } else {
                windupAnim   = pinkBombWindup;
                shootAnim    = pinkBombShoot;
                winddownAnim = pinkBombWinddown;
            }
        }
        else if (lastDirection == Direction.UP) {
            windupAnim   = upBombWindup;
            shootAnim    = upBombShoot;
            winddownAnim = upBombWinddown;
        } else if (lastDirection == Direction.DOWN) {
            windupAnim   = downBombWindup;
            shootAnim    = downBombShoot;
            winddownAnim = downBombWinddown;
        } else {
            windupAnim   = bombWindup;
            shootAnim    = bombShoot;
            winddownAnim = bombWinddown;
        }
//        Gdx.app.log("BombDbg",
//            "windupDur=" + windupAnim.getAnimationDuration()
//                + " shootDur=" + shootAnim.getAnimationDuration()
//                + " winddownDur=" + winddownAnim.getAnimationDuration()
//                + " phase=" + bombPhase);

        // 2) Phase logic using the chosen animations:
        switch (bombPhase) {
            case WINDUP:
                if (windupAnim.isAnimationFinished(bombTime)) {
                    // clamp & freeze to *this* windup’s duration
                    bombTime = windupAnim.getAnimationDuration();
                }
                break;

            case SHOOT:
                if (shootAnim.isAnimationFinished(bombTime)) {
                    bombPhase = BombPhase.WINDDOWN;
                    bombTime  = 0f;
                }
                break;

            case WINDDOWN:
                if (winddownAnim.isAnimationFinished(bombTime)) {
                    bombPhase = BombPhase.NONE;
                }
                break;
        }

        Animation<TextureRegion> currentAnim =
            bombPhase == BombPhase.WINDUP ? windupAnim
                : bombPhase == BombPhase.SHOOT  ? shootAnim
                    : winddownAnim;
        currentFrame = currentAnim.getKeyFrame(bombTime, false);


    }

    /* ───────── pink animations (hidden-in-paint) ───────── */
    private Animation<TextureRegion> pinkIdleAnim, pinkWalkAnim,
        pinkUpWalkAnim, pinkDownWalkAnim;

    public void setPinkAnimations(Animation<TextureRegion> idle,
        Animation<TextureRegion> walk,
        Animation<TextureRegion> up,
        Animation<TextureRegion> down) {
        pinkIdleAnim  = idle;
        pinkWalkAnim  = walk;
        pinkUpWalkAnim= up;
        pinkDownWalkAnim = down;
    }
    public void setPinkBombAnimations(
        Animation<TextureRegion> wind,
        Animation<TextureRegion> shoot,
        Animation<TextureRegion> down) {
        this.pinkBombWindup   = wind;
        this.pinkBombShoot    = shoot;
        this.pinkBombWinddown = down;
    }
    // 新增上下两套 setter
    public void setUpPinkBombAnimations(
        Animation<TextureRegion> windup,
        Animation<TextureRegion> shoot,
        Animation<TextureRegion> winddown) {
        this.upPinkBombWindup   = windup;
        this.upPinkBombShoot    = shoot;
        this.upPinkBombWinddown = winddown;
    }

    public void setDownPinkBombAnimations(
        Animation<TextureRegion> windup,
        Animation<TextureRegion> shoot,
        Animation<TextureRegion> winddown) {
        this.downPinkBombWindup   = windup;
        this.downPinkBombShoot    = shoot;
        this.downPinkBombWinddown = winddown;
    }
    public void setUpBombAnimations(
        Animation<TextureRegion> wind, Animation<TextureRegion> shoot, Animation<TextureRegion> down) {
        this.upBombWindup   = wind;
        this.upBombShoot    = shoot;
        this.upBombWinddown = down;
    }

    public void setDownBombAnimations(
        Animation<TextureRegion> wind, Animation<TextureRegion> shoot, Animation<TextureRegion> down) {
        this.downBombWindup   = wind;
        this.downBombShoot    = shoot;
        this.downBombWinddown = down;
    }

}



    /* override your ordinary draw() */

