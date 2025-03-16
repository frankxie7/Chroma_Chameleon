package chroma.model;

import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Vector2; import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.graphics.Texture2D;
import edu.cornell.gdiac.math.Path2;
import edu.cornell.gdiac.physics2.CapsuleObstacle;
import edu.cornell.gdiac.physics2.ObstacleSprite;

public class Enemy extends ObstacleSprite {
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

    /** The current horizontal movement of the character */
    private float movement;
    /** The current vertical movement of the character */
    private float verticalMovement;
    /** Which direction is the character facing */
    private boolean faceRight;

    /** Sensor */
    private Path2 sensorOutline;
    private Color sensorColor;
    private String sensorName;

    /** Cache for internal force calculations */
    private final Vector2 forceCache = new Vector2();
    private final Affine2 flipCache = new Affine2();

    private Color color;
    private float detectionRange;

    public Enemy(float[] position, String name, float units, JsonValue data) {
//        this.color = Color.MAGENTA; // enemy's body color
//        this.detectionRange = 200f;

        this.data = data;
        JsonValue debugInfo = data.get("debug");

        float s = data.getFloat("size");
        float size = s * units;

        // Create a capsule obstacle
        obstacle = new CapsuleObstacle(position[0], position[1], s * data.get("inner").getFloat(0), s * data.get("inner").getFloat(1));
        ((CapsuleObstacle)obstacle).setTolerance(debugInfo.getFloat("tolerance", 0.5f));

        obstacle.setDensity(data.getFloat("density", 0));
        obstacle.setFriction(data.getFloat("friction", 0));
        obstacle.setRestitution(data.getFloat("restitution", 0));
        // Ensure the body is dynamic so it can move.
        obstacle.setBodyType(BodyDef.BodyType.DynamicBody);
        obstacle.setFixedRotation(true);
        obstacle.setPhysicsUnits(units);
        obstacle.setUserData(this);
        obstacle.setName(name);

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

    public void setVerticalMovement(float value) {
        verticalMovement = value;
    }

    public float getForce() {
        return force;
    }

    public float getDamping() {
        return damping;
    }

    public float getMaxSpeed() {
        return maxspeed;
    }

    public boolean isFacingRight() {
        return faceRight;
    }

    public Color getColor() {
        return color;
    }

//    public float getDetectionRange() {
//        return detectionRange;
//    }
//
//    public void setDetectionRange(float range) {
//        this.detectionRange = range;
//    }

    public void applyForce() {
        if (!obstacle.isActive()) {
            return;
        }

        Vector2 pos = obstacle.getPosition();
        float vx = obstacle.getVX();
        float vy = obstacle.getVY();
        Body body = obstacle.getBody();

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
