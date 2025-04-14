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
    private static final float GRAVITY = 30f; // tune as needed for arc
    private static final float LIFETIME = 4f;

    /**
     * Horizontal velocity in physics units (straight line)
     */
    private Vector2 velocity;
    /**
     * Target position for horizontal movement
     */
    private Vector2 target;
    /**
     * Time this bomb has been alive
     */
    private float timeAlive;

    private Vector2 startPos;

    // "Arc" parameters in physics units
    private float z;   // vertical offset
    private float vz;  // vertical velocity

    private boolean flying;

    public Bomb(float units,
        JsonValue settings,
        Vector2 startPos,
        Vector2 velocity,
        Vector2 targetPos) {
        this.startPos = new Vector2(startPos);
        float s = settings.getFloat("size");
        float radius = s * units / 2.0f;

        obstacle = new WheelObstacle(targetPos.x, targetPos.y, s / 2);

        obstacle.setPhysicsUnits(units);
        // For manual movement, a Kinematic body is often best:
        obstacle.setBodyType(BodyType.DynamicBody);

        obstacle.setSensor(true);
        obstacle.setUserData(this);
        obstacle.setName("bomb");


        // 2) Initialize debug color and mesh to match the circle
        debug = ParserUtils.parseColor(settings.get("debug"), Color.WHITE);
        mesh.set(-radius, -radius, 2 * radius, 2 * radius);
        int count = mesh.vertexCount();
        for (int i = 0; i < count; i++) {
            mesh.setColor(i, new Color(0.5f, 0.0f, 1.0f, 0.5f));
        }
        // 3) Store everything
        this.timeAlive = 0;
        this.velocity = new Vector2(velocity);
        this.target = new Vector2(targetPos);

        // Start the “arc” at z=0, with an initial upward velocity
        this.z = 0f;
        this.vz = 10f; // tweak as desired for arc height
        this.flying = true;
    }

    public boolean isExpired() {
        return timeAlive >= LIFETIME;
    }

    public boolean isFlying() {
        return flying;
    }

    public float getZ() {
        return z;
    }

    public float getVz() {
        return vz;
    }

    public void setZ(float value) {
        z = value;
    }

    public void setVz(float value) {
        vz = value;
    }

    @Override
    public void update(float dt) {
        timeAlive += dt;
        if (!flying) {
            return;
        }

        z += vz * dt;
        vz -= GRAVITY * dt;

        if (z < 0) {
            z = 0;
            vz = 0;
            flying = false;
        }
    }


    /**
     * Override how we draw so we can offset the sprite by z (in pixels).
     * <p>
     * The parent code in ObstacleSprite draws as if the bomb is at (x * units, y * units). We do
     * the same transform, but add an extra vertical offset of (z * units).
     */
    @Override
    public void draw(SpriteBatch batch) {
        if (obstacle == null || mesh == null) {
            return;
        }

        float arcDuration = 0.5f;
        float t = Math.min(timeAlive / arcDuration, 1.0f);

        float interpX = startPos.x + t * (target.x - startPos.x);
        float interpY = startPos.y + t * (target.y - startPos.y);

        float peakHeight = 2.0f;
        float offset = 4 * peakHeight * t * (1 - t);

        Vector2 displayPos = new Vector2(interpX, interpY + offset);

        float u = obstacle.getPhysicsUnits();

        transform.idt();


        float a = obstacle.getAngle();
        float degrees = (float)(a * 180.0 / Math.PI);
        transform.preRotate(degrees);

        transform.preTranslate(displayPos.x * u, displayPos.y * u);

        batch.setTextureRegion(sprite);
        batch.drawMesh(mesh, transform, false);
        batch.setTexture(null);
    }
    public float getTimeAlive() {
        return timeAlive;
    }
}

