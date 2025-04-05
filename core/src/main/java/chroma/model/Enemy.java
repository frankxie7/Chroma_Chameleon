package chroma.model;

import com.badlogic.gdx.math.Affine2;
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
        GUARD, JANITOR, CAMERA
    }

    private float detectionRange;
    private float fov;
    private Vector2 position;

    /** Whether the guard wanders or patrols as neutral state */
    private boolean patrol;
    private List<float[]> patrolPath;

    /** The factor to multiply by the input */
    private float force;
    /** The amount to slow the character down */
    private float damping;
    /** The maximum character speed */
    private float maxspeed;

    /** The current horizontal movement of the character */
    private float movement;
    /** The current vertical movement of the character */
    private float verticalMovement;
    /** Which direction is the character facing */
    private boolean faceRight;

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

    private Enemy.Type type;

    public Enemy(float[] position, String name, String type, boolean patrol, List<float[]> patrolPath, float detectionRange, float fov, float startRotation, float rotateAngle, float units, JsonValue data) {
        this.type = Type.valueOf(type);
        this.patrol = patrol;
        this.patrolPath = patrolPath;
        this.detectionRange = detectionRange;
        this.fov = fov;
        this.startRotation = startRotation;
        this.rotation = startRotation;
        this.minRotation = startRotation - rotateAngle % 360;
        this.maxRotation = startRotation + rotateAngle % 360;

        JsonValue debugInfo = data.get("debug");
        float s = data.getFloat("size");
        float size = s * units;

        if (this.type == Type.CAMERA) {
            this.position = new Vector2(position[0], position[1]);
            obstacle = null;
        } else {
            obstacle = new CapsuleObstacle(position[0], position[1], s * data.get("inner").getFloat(0), s * data.get("inner").getFloat(1));
            obstacle.setBodyType(BodyDef.BodyType.DynamicBody);
        }
        if (obstacle != null) {
            ((CapsuleObstacle) obstacle).setTolerance(debugInfo.getFloat("tolerance", 0.5f));
            obstacle.setDensity(data.getFloat("density", 0));
            obstacle.setFriction(data.getFloat("friction", 0));
            obstacle.setRestitution(data.getFloat("restitution", 0));
            obstacle.setFixedRotation(true);
            obstacle.setPhysicsUnits(units);
            obstacle.setUserData(this);
            obstacle.setName(name);

            // Filter allows collisions with everything except other enemies
            Filter enemyFilter = new Filter();
            enemyFilter.groupIndex = -1;
            obstacle.setFilterData(enemyFilter);
        }

        // Set up debug colors, mesh, etc.
        debug = ParserUtils.parseColor(debugInfo.get("avatar"), Color.WHITE);
        sensorColor = ParserUtils.parseColor(debugInfo.get("sensor"), Color.WHITE);

        maxspeed = data.getFloat("maxspeed", 0);
        damping = data.getFloat("damping", 0);
        force = data.getFloat("force", 0);

        faceRight = true;

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
    public float getDetectionRange() { return detectionRange; }
    public float getFov() { return fov; }
    public Type getType() { return type; }
    public float getStartRotation() { return startRotation; }
    public Vector2 getPosition() { return (type == Type.CAMERA) ? position : obstacle.getPosition(); }
    public boolean getPatrol() { return patrol; }
    public List<float[]> getPatrolPath() { return patrolPath; }

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
        if (targetVelocity.len() > 1) {
            targetVelocity.nor();
        }

        // Scale by force and apply smoothing
        targetVelocity.scl(getForce());
        currentVelocity.lerp(targetVelocity, turnSmoothing); // Smoothly transition

//        System.out.println(getName() + ": " + currentVelocity);

        // Apply the interpolated force
        body.applyForce(currentVelocity, pos, true);

        // Apply damping to slow down gradually
        if (currentVelocity.len2() < 0.01f) { // Prevent tiny movements
            body.setLinearVelocity(0, 0);
        } else if (currentVelocity.len() > getMaxSpeed()) {
            currentVelocity.nor().scl(getMaxSpeed());
            body.setLinearVelocity(currentVelocity);
        }
    }

    public void update(float delta) { super.update(delta); }

    @Override
    public void draw(SpriteBatch batch) {
        if (faceRight) {
            flipCache.setToScaling( 1,1 );
        } else {
            flipCache.setToScaling( -1,1 );
        }
        super.draw(batch,flipCache);
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
