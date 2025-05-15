package chroma.model;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.WheelObstacle;
import edu.cornell.gdiac.graphics.SpriteBatch;

public class Bomb extends ObstacleSprite {

    // For the bomb to appear “in flight” visually
    private static final float GRAVITY = 60f; // tune as needed for arc
    static final float LIFETIME = 20f;

    // Random rotation for splatter
    private float splatterRotation = 0f;
    // Visual scale constants
    static final float FLY_SCALE  = 0.5f;
    static final float LAND_SCALE = 1.8f;
    private Texture flyTex;
    private Texture splatterTex;
    private Sound splatterSound;
    // Bomb.java  – add near the other constants
    private static final float FADE_DURATION = 5f;   // seconds to fade out
    private float fadeClock  = 15f;     // counts from 0 → FADE_DURATION
    private float alpha      = 1f;
    private boolean fading   = false;  // start after we “pop”

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

    private float size;

    // "Arc" parameters in physics units
    private float z;   // vertical offset
    private float vz;  // vertical velocity

    private boolean flying;
    private final float initialVz;
    private final float arcDuration;

    public Bomb(float units,
                JsonValue settings,
                Vector2 startPos,
                Vector2 velocity,
                Vector2 targetPos,Texture flyTex,
                Texture splatterTex,
                Sound splatterSound) {
        this.startPos = new Vector2(startPos);
        size = settings.getFloat("size");
        float radius = size * units / 2.0f;

        obstacle = new WheelObstacle(targetPos.x, targetPos.y, size / 2);

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
            mesh.setColor(i, new Color( 1.0f, 1.0f, 1.0f, 1f));
        }
        // Store arc data
        this.timeAlive   = 0f;
        this.velocity    = new Vector2(velocity);
        this.target      = new Vector2(targetPos);
        this.z           = 0f;
        this.vz          = 20f;  // initial vertical speed
        this.initialVz   = this.vz;
        this.arcDuration = 2 * initialVz / GRAVITY;
        this.flying      = true;

        this.flyTex   = flyTex;
        this.splatterTex = splatterTex;
        setTexture(flyTex);
        this.splatterSound = splatterSound;
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
        if (!fading && timeAlive >= LIFETIME - FADE_DURATION) {
            beginFade();                       // will set fading = true, fadeClock = 0
        }
        if (fading) {
            fadeClock += dt;
            float t = Math.min(fadeClock / FADE_DURATION, 1f); // 0 → 1
            alpha = 1f - t;            // linear fade-out

            if (fadeClock >= FADE_DURATION) {
                alpha = 0f;
            }
        }
        if (!flying) {
            return;
        }

        z += vz * dt;
        vz -= GRAVITY * dt;

        if (z < 0) {
            z = 0;
            vz = 0;
            flying = false;
            setTexture(splatterTex);
            // Randomize splatter rotation
            splatterRotation = MathUtils.random(0f, 360f);

            splatterSound.play();
//            beginFade();
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

        // 1) Compute draw center in world coords
        Vector2 center;
        if (flying) {
            float t   = Math.min(timeAlive / arcDuration, 1f);
            float ix  = startPos.x + t * (target.x - startPos.x);
            float iy  = startPos.y + t * (target.y - startPos.y);
            float peak = 2f;
            float offset = 4 * peak * t * (1 - t);
            center = new Vector2(ix, iy + offset);
        } else {
            center = target;
        }

        // 2) Choose visual scale
        float visScale = flying ? FLY_SCALE : LAND_SCALE;

        // 3) Build transform: scale → rotate(if landed) → translate
        float u = obstacle.getPhysicsUnits();
        transform.idt();
        transform.preScale(visScale, visScale);
        if (!flying) {
            transform.preRotate(splatterRotation);
        }
        transform.preTranslate(center.x * u, center.y * u);

        Color saved = new Color(batch.getColor());

        // Apply fading alpha; keep existing RGB
        batch.setColor(saved.r, saved.g, saved.b, alpha);
        batch.setTextureRegion(sprite);
        batch.drawMesh(mesh, transform, true);
        batch.setTexture(null);
        batch.setColor(saved);
    }
    public void beginFade() {
        fading    = true;
        fadeClock = 0f;
    }

}

