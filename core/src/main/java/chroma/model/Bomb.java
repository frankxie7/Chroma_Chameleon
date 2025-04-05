package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.WheelObstacle;
import edu.cornell.gdiac.graphics.SpriteBatch;

public class Bomb extends ObstacleSprite {
    // For the bomb to appear “in flight” visually
    private static final float GRAVITY  = 30f; // tune as needed for arc
    private static final float LIFETIME = 3f;

    /** Horizontal velocity in physics units (straight line) */
    private Vector2 velocity;
    /** Target position for horizontal movement */
    private Vector2 target;
    /** Time this bomb has been alive */
    private float timeAlive;

    // "Arc" parameters in physics units
    private float z;   // vertical offset
    private float vz;  // vertical velocity

    private boolean flying;

    public Bomb(float units,
        JsonValue settings,
        Vector2 startPos,
        Vector2 velocity,
        Vector2 targetPos) {

        // 1) Create the underlying WheelObstacle
        float s      = settings.getFloat("size");
        float radius = s * units / 2.0f;

        obstacle = new WheelObstacle(startPos.x, startPos.y, s/2);
        obstacle.setPhysicsUnits(units);
        // For manual movement, a Kinematic body is often best:
        obstacle.setBodyType(BodyType.KinematicBody);

        obstacle.setSensor(true);
        obstacle.setUserData(this);
        obstacle.setName("bomb");

        // If needed from JSON:
        obstacle.setDensity(settings.getFloat("density", 0));
        obstacle.setFriction(settings.getFloat("friction", 0));
        obstacle.setRestitution(settings.getFloat("restitution", 0));

        // 2) Initialize debug color and mesh to match the circle
        debug = ParserUtils.parseColor(settings.get("debug"), Color.WHITE);
        mesh.set(-radius, -radius, 2*radius, 2*radius);
        int count = mesh.vertexCount();
        for (int i = 0; i < count; i++) {
            mesh.setColor(i, new Color(0.5f,0.0f,1.0f,0.5f));
        }
        // 3) Store everything
        this.timeAlive = 0;
        this.velocity  = new Vector2(velocity);
        this.target    = new Vector2(targetPos);

        // Start the “arc” at z=0, with an initial upward velocity
        this.z   = 0f;
        this.vz  = 8f; // tweak as desired for arc height
        this.flying = true;
    }

    public boolean isExpired() {
        return timeAlive >= LIFETIME;
    }

    public boolean isFlying() {
        return flying;
    }

    public float getZ()  { return z; }
    public float getVz() { return vz; }
    public void setZ(float value)  { z  = value; }
    public void setVz(float value) { vz = value; }

    @Override
    public void update(float dt) {
        timeAlive += dt;
        if (!flying) {
            return;
        }

        // -- 1) Horizontal movement in a straight line --
        Vector2 pos           = obstacle.getPosition();
        float distanceToTarget= pos.dst(target);
        float moveThisFrame   = velocity.len() * dt;

        // If we'd overshoot, clamp & stop horizontally
        if (distanceToTarget <= moveThisFrame) {
            obstacle.setPosition(target.x, target.y);
            velocity.setZero();
        } else {
            pos.add(velocity.x * dt, velocity.y * dt);
            obstacle.setPosition(pos.x, pos.y);
        }

        // -- 2) Vertical “arc” via a manual z + vz + gravity
        z  += vz * dt;     // move up/down
        vz -= GRAVITY * dt; // apply downward acceleration

        // Optionally, stop flight when the bomb hits “ground” again:
        if (z < 0) {
            z = 0;
            vz= 0;
            flying = false;
        }
    }

    /**
     * Override how we draw so we can offset the sprite by z (in pixels).
     *
     * The parent code in ObstacleSprite draws as if the bomb
     * is at (x * units, y * units). We do the same transform,
     * but add an extra vertical offset of (z * units).
     */
    @Override
    public void draw(SpriteBatch batch) {
        if (obstacle == null || mesh == null) {
            return;
        }

        // read from the obstacle
        float x = obstacle.getX();          // in physics units
        float y = obstacle.getY();          // in physics units
        float a = obstacle.getAngle();      // in radians
        float u = obstacle.getPhysicsUnits(); // "pixels per physics unit"

        // Rebuild the Affine2 transform (just like the parent’s code) …
        transform.idt();

        if (flying) {
            float scaleFactor = 0.5f;  // choose any factor you like
            transform.preScale(scaleFactor, scaleFactor);
        }

        // 1) Rotate by angle (in degrees)
        float degrees = (float)(a * 180.0 / Math.PI);
        transform.preRotate(degrees);

        // 2) Translate by (x * u, y * u), plus z offset
        transform.preTranslate(x * u, y * u + z * u);

        // Now do exactly what ObstacleSprite’s draw does:
        batch.setTextureRegion(sprite);
        batch.drawMesh(mesh, transform, false);
        batch.setTexture(null);
    }
}
