package chroma.model;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2; import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Filter;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.graphics.Texture2D;
import edu.cornell.gdiac.math.Path2;
import edu.cornell.gdiac.physics2.CapsuleObstacle;
import edu.cornell.gdiac.physics2.ObstacleSprite;

import java.util.List;

public class Enemy extends ObstacleSprite {
    public enum Type {
        GUARD, SWEEPER, CAMERA
    }

    private Vector2 position;
    private float bodyWidth;
    private float bodyHeight;

    /** Whether the guard wanders or patrols as neutral state */
    private boolean patrol;
    private List<float[]> patrolPath;

    /** The factor to multiply by the input */
    private float force;
    /** The maximum character speed */
    private float maxspeed;

    /** The current horizontal movement of the character */
    private float movement;
    /** The current vertical movement of the character */
    private float verticalMovement;
    /** Which direction is the character facing */
    private boolean faceRight;

    // DETECTION RANGE and FOV:
    private float baseDetectionRange;
    private float alertDetectionRange;
    private float cameraFOV = 60;
    private float guardFOV = 135;

    // ROTATION (CAMERA):
    private float rotation;
    private float startRotation;
    /** Min and Max angles for camera rotation */
    private float minRotation;
    private float maxRotation;

    /** Sensor */
    private Path2 sensorOutline;
    private Color sensorColor;

    /** Cache for internal force calculations */
    private final Vector2 forceCache = new Vector2();
    private final Affine2 flipCache = new Affine2();

    // ENEMY TYPE
    private Enemy.Type type;

    // ANIMATIONS:
    private Animation<TextureRegion> alertAnim;
    private int alertFrame;
    private Animation<TextureRegion> blueRedAnim;
    private float blueRedTime;
    private Animation<TextureRegion> frontAnim;
    private int frontFrame;
    private Animation<TextureRegion> sideAnim;
    private int sideFrame;
    private Animation<TextureRegion> backAnim;
    private int backFrame;
    private float animTime;
    private float drawScale;

    public Enemy(float[] position, String type, boolean patrol, List<float[]> patrolPath, float startRotation,
                 float rotateAngle, float units, JsonValue data, Animation<TextureRegion> enemyAlertAnim,
                 Animation<TextureRegion> enemyBlueRedAnim, Animation<TextureRegion> enemyFrontAnim, Animation<TextureRegion> enemySideAnim,
                 Animation<TextureRegion> enemyBackAnim) {
        this.type = Type.valueOf(type);
        this.patrol = patrol;
        this.patrolPath = patrolPath;
        if (this.type == Type.CAMERA) {
            this.baseDetectionRange = 7;
            this.alertDetectionRange = 7;
        } else {
            this.baseDetectionRange = 5;
            this.alertDetectionRange = 7;
        }
        this.startRotation = startRotation;
        this.rotation = startRotation;
        this.minRotation = startRotation - rotateAngle % 360;
        this.maxRotation = startRotation + rotateAngle % 360;

        JsonValue debugInfo = data.get("debug");
        float s = data.getFloat("size");
        float size = s * units;
        drawScale = data.getFloat("drawScale");

        if (this.type == Type.CAMERA) {
            this.position = new Vector2(position[0], position[1]);
            obstacle = null;
            bodyWidth = 0;
            bodyHeight = 0;
        } else {
            bodyWidth = s * data.get("inner").getFloat(0);
            bodyHeight = s * data.get("inner").getFloat(1);
            obstacle = new CapsuleObstacle(position[0], position[1], bodyWidth, bodyHeight);
            obstacle.setBodyType(BodyDef.BodyType.DynamicBody);
            ((CapsuleObstacle) obstacle).setTolerance(debugInfo.getFloat("tolerance", 0.5f));
            obstacle.setFixedRotation(true);
            obstacle.setPhysicsUnits(units);
            obstacle.setUserData(this);
            obstacle.setName("enemy");

            // Filter allows collisions with everything except other enemies
            Filter enemyFilter = new Filter();
            enemyFilter.groupIndex = -1;
            obstacle.setFilterData(enemyFilter);
        }

        // Set up debug colors, mesh, etc.
        debug = ParserUtils.parseColor(debugInfo.get("avatar"), Color.WHITE);
        sensorColor = ParserUtils.parseColor(debugInfo.get("sensor"), Color.WHITE);

        maxspeed = data.getFloat("maxspeed", 0);
        force = data.getFloat("force", 0);

        faceRight = true;

        alertAnim = enemyAlertAnim;
        alertFrame = -1;
        blueRedAnim = enemyBlueRedAnim;
        blueRedTime = 0;
        frontAnim = enemyFrontAnim;
        frontFrame = -1;
        sideAnim = enemySideAnim;
        sideFrame = -1;
        backAnim = enemyBackAnim;
        backFrame = -1;

        animTime = 0;

        // Create a rectangular mesh for the enemy.
        mesh.set(-size / 2.0f, -size / 2.0f, size, size);
}

    public float getMovement() { return movement; }
    public void setMovement(float value) {
        movement = value;
        // Change facing if appropriate
        if (movement < 0) {
            faceRight = false;
        } else if (movement > 0) {
            faceRight = true;
        }
    }
    public float getVerticalMovement() { return verticalMovement; }
    public void setVerticalMovement(float value) { verticalMovement = value; }
    public float getForce() { return force; }
    public float getMaxSpeed() { return maxspeed; }
    public void setMaxSpeed(float value) { this.maxspeed = value; }
    public float getBaseDetectionRange() { return baseDetectionRange; }
    public float getAlertDetectionRange() { return alertDetectionRange; }
    public float getFov() { return type == Type.CAMERA ? cameraFOV : guardFOV; }
    public Type getType() { return type; }
    public float getStartRotation() { return startRotation; }
    public Vector2 getPosition() { return (type == Type.CAMERA) ? position : obstacle.getPosition(); }
    public float getWidth() { return bodyWidth; }
    public float getHeight() { return bodyHeight; }
    public boolean getPatrol() { return patrol; }
    public List<float[]> getPatrolPath() { return patrolPath; }
    public Animation<TextureRegion> getAlertAnimation() { return alertAnim; }
    public int getAlertAnimationFrame() { return alertFrame; }
    public void setAlertAnimationFrame(int index) { this.alertFrame = index; }
    public Animation<TextureRegion> getBlueRedAnimation() { return blueRedAnim; }
    public void setBlueRedTime(float index) { this.blueRedTime = index; }
    public float getDrawScale() { return drawScale; }

    public float getRotation() {
        if (type == Type.CAMERA) {
            return (float) Math.toRadians(rotation); // Cameras use their own stored rotation
        }

        Vector2 velocity = obstacle.getBody().getLinearVelocity();

        // If the enemy is stationary, return the last known facing angle
        if (velocity.len2() < 0.01f) {
            return faceRight ? 0 : (float) Math.PI;
        }

        return (float) Math.atan2(velocity.y, velocity.x);
    }
    public void setRotation(float angle) {
        this.rotation = ((float) Math.toDegrees(angle)) % 360f;
        if (this.rotation < 0) this.rotation += 360f; // Keep it in [0, 360)
    }
    public float getMinRotation() { return (float) Math.toRadians(minRotation); }
    public float getMaxRotation() { return (float) Math.toRadians(maxRotation); }

    private Vector2 targetVelocity = new Vector2();
    private Vector2 currentVelocity = new Vector2();
    private float turnSmoothing = 0.1f;

    public void applyForce() {
        if (type == Type.CAMERA || obstacle == null || !obstacle.isActive()) {
            return;
        }

        Vector2 pos = obstacle.getPosition();
        Body body = obstacle.getBody();

        // Get the current movement direction
        targetVelocity.set(getMovement(), getVerticalMovement());

        // Normalize if needed
//        if (targetVelocity.len() > 1) {
//            targetVelocity.nor();
//        }

        // Scale by force and apply smoothing
        targetVelocity.scl(getForce());
        currentVelocity.lerp(targetVelocity, turnSmoothing); // Smoothly transition

//        System.out.println(getName() + ": " + currentVelocity);

        // Apply the interpolated force
        body.applyForce(currentVelocity, pos, true);

        body.setLinearDamping(0.9f);

        // Apply damping to slow down gradually

        if (currentVelocity.len2() < 0.01f) { // Prevent tiny movements
            body.setLinearVelocity(0, 0);
        } else if (currentVelocity.len() > getMaxSpeed()) {
            currentVelocity.nor().scl(getMaxSpeed());
            body.setLinearVelocity(currentVelocity);
        }
    }

    public void update(float delta) {
        updateAnimation(delta);
        super.update(delta);
    }
    public void updateAnimation(float delta) {
        animTime += delta;

        if (obstacle == null || obstacle.getBody() == null) { return; }

        Vector2 velocity = obstacle.getBody().getLinearVelocity();

        if (velocity.len2() < 0.01f) {
            // Standing still: set frame to first of the side or front anim (depends on your preference)
            sideFrame = -1;
            frontFrame = 0;
            return;
        }

        // Determine facing angle
        float angle = getRotation();  // This gives you a world-facing angle
        float degrees = (float) Math.toDegrees(angle);
        degrees = (degrees + 360) % 360;  // normalize

        // You can adjust this range depending on how "front" vs "side" should behave
        boolean isFront = (degrees > 225 && degrees < 315);
        boolean isBack = (degrees > 45 && degrees < 135);

        Animation<TextureRegion> activeAnim = isFront ? frontAnim : sideAnim;

        int frameIndex = activeAnim.getKeyFrameIndex(animTime);

        if (isFront) {
            frontFrame = frameIndex;
            sideFrame = -1;
            backFrame = -1;
        } else if (isBack) {
            frontFrame = -1;
            sideFrame = -1;
            backFrame = frameIndex;
        } else {
            frontFrame =-1;
            sideFrame = frameIndex;
            backFrame = -1;
        }

        // Update faceRight for flipping (side animations only — you might skip this for front)
        faceRight = velocity.x >= 0;
    }

    public void setBlueRedAnimationTime(float time) {
        this.blueRedTime = MathUtils.clamp(time, 0f, blueRedAnim.getAnimationDuration());
    }

    @Override
    public void draw(SpriteBatch batch) {
//        if (faceRight) {
//            flipCache.setToScaling( 1,1 );
//        } else {
//            flipCache.setToScaling( -1,1 );
//        }
//        super.draw(batch,flipCache);

//        if (blueRedTime != 0) {
//            TextureRegion frame = blueRedAnim.getKeyFrame(blueRedTime);
//
//            float drawWidth = frame.getRegionWidth() * drawScale;
//            float drawHeight = frame.getRegionHeight() * drawScale;
//            float px = obstacle.getX() * obstacle.getPhysicsUnits();
//            float py = obstacle.getY() * obstacle.getPhysicsUnits();
//
//            batch.draw(frame,
//                px - drawWidth / 2, py - drawHeight / 2,
//                drawWidth / 2, drawHeight / 2,
//                drawWidth, drawHeight,
//                1, 1,
//                0);
//        }

        TextureRegion frame = null;

        if (sideFrame != -1) {
            frame = new TextureRegion(sideAnim.getKeyFrames()[sideFrame]);
        } else if (frontFrame != -1) {
            frame = new TextureRegion(frontAnim.getKeyFrames()[frontFrame]);
        } else if (backFrame != -1) {
            frame = new TextureRegion(backAnim.getKeyFrames()[backFrame]);
        }

        if (frame == null) return;

        float drawWidth = frame.getRegionWidth() * drawScale;
        float drawHeight = frame.getRegionHeight() * drawScale;
        float px = obstacle.getX() * obstacle.getPhysicsUnits();
        float py = obstacle.getY() * obstacle.getPhysicsUnits();

        if (!faceRight) {
            if (frame.isFlipX()) frame.flip(true, false);
        } else {
            if (!frame.isFlipX()) frame.flip(true, false);
        }

        batch.draw(frame,
            px - drawWidth / 2,
            py - drawHeight / 2,
            drawWidth / 2, drawHeight / 2,
            drawWidth, drawHeight,
            1, 1,
            0);
    }

    @Override
    public void drawDebug(SpriteBatch batch) {
        super.drawDebug( batch );

        if (sensorOutline != null) {
            batch.setTexture( Texture2D.getBlank() );
            batch.setColor( sensorColor );

            Vector2 p = obstacle.getPosition();
            float a = obstacle.getAngle();
            float u = obstacle.getPhysicsUnits();

            // transform is an inherited cache variable
            transform.idt();
            transform.preRotate( (float) (a * 180.0f / Math.PI) );
            transform.preTranslate( p.x * u, p.y * u );

            //
            batch.outline( sensorOutline, transform );
        }
    }
}
