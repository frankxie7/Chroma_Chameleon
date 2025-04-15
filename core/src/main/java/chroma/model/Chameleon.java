package chroma.model;

import chroma.controller.InputController;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

import com.badlogic.gdx.physics.box2d.BodyDef;

import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.graphics.Texture2D;
import edu.cornell.gdiac.math.Path2;
import edu.cornell.gdiac.math.PathFactory;
import edu.cornell.gdiac.physics2.CapsuleObstacle;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.graphics.shaders.SpriteShader;

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

    private float maxPaint = 50f;
    private float currentPaint = 50f;

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

    //Position
    private Vector2 position;

    private float orientation = 0.0f;
    private boolean hidden;
    private Vector2 lastSeen;
    private boolean faceRight = true;

    private Animation<TextureRegion> walkAnim;
    private float animTime;
    private TextureRegion currentFrame;

    private float drawScale;
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

    /**
     * Creates a new Traci avatar with the given physics data
     *
     * The physics units are used to size the mesh relative to the physics
     * body. All other attributes are defined by the JSON file. Because of
     * transparency around the image file, the physics object will be slightly
     * thinner than the mesh in order to give a tighter hitbox.
     *
     * @param units     The physics units
     * @param data      The physics constants for Traci
     */
    public Chameleon(float units, JsonValue data, Animation<TextureRegion> animation) {
        this.data = data;
        JsonValue debugInfo = data.get("debug");

        float x = data.get("pos").getFloat(0);
        float y = data.get("pos").getFloat(1);
        this.lastSeen = new Vector2(x, y);

        float s = data.getFloat("size");
        float size = s * units;
        drawScale = data.getFloat("drawScale");

        // Create a capsule obstacle
        obstacle = new CapsuleObstacle(x, y, s * data.get("inner").getFloat(0), s * data.get("inner").getFloat(1));
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

        maxspeed = data.getFloat("maxspeed", 0);
        currentMaxSpeed = maxspeed;
        damping = data.getFloat("damping", 0);
        force = data.getFloat("force", 0);

        shotLimit = data.getInt("shot_cool", 0);

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
        animTime = 0;
        TextureRegion[] frames = (TextureRegion[]) walkAnim.getKeyFrames();
        currentFrame = frames[6];
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
        InputController input = InputController.getInstance();

        // Update player (chameleon) movement based on input
        float hmove = input.getHorizontal();
        float vmove = input.getVertical();
        setMovement(hmove * getForce());
        setVerticalMovement(vmove * getForce());
        setShooting(input.didSecondary());
        applyForce();

        // Apply cooldowns
        if (isShooting()) {
            shootCooldown = shotLimit;
        } else {
            shootCooldown = Math.max(0, shootCooldown - 1);
        }
        // Update orientation based on current velocity
//        updateOrientation();

        if (hmove > 0) {
            faceRight = true;
        } else if (hmove < 0) {
            faceRight = false;
        }

        if (hmove == 0 && vmove == 0) {
            TextureRegion[] frames = (TextureRegion[]) walkAnim.getKeyFrames();
            currentFrame = frames[6];
            animTime += dt;
            currentFrame = walkAnim.getKeyFrame(animTime, true);
            animTime = 6;
        } else {
            animTime += dt;
            currentFrame = walkAnim.getKeyFrame(animTime, true);
        }
        // Then call the superclass update
        super.update(dt);
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
        // Save the current color of the batch
        Color target = isHidden() ? Color.PURPLE : Color.WHITE;
        batch.setColor(target);
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

        if (faceRight) {
            if (currentFrame.isFlipX()) {
                currentFrame.flip(true, false);
            }
        } else {
            if (!currentFrame.isFlipX()) {
                currentFrame.flip(true, false);
            }
        }
        batch.draw(currentFrame,
            px - drawWidth / 2,
            py - drawHeight / 2,
            drawWidth / 2, drawHeight / 2,
            drawWidth, drawHeight,
            1, 1,
            0);
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

}
