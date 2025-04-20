package chroma.model;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import edu.cornell.gdiac.graphics.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Filter;
import edu.cornell.gdiac.physics2.PolygonObstacle;
import edu.cornell.gdiac.physics2.ObstacleSprite;

/**
 * Door with frame-based animations, drawn manually due to lack of setRegion().
 * Closed door loops closedAnim; on open(), plays openAnim once and becomes sensor.
 */
public class Door extends ObstacleSprite {
    private final Animation<TextureRegion> closedAnim;
    private final Animation<TextureRegion> openAnim;
    private boolean opened = false;

    private float animTime = 0f;
    private TextureRegion currentFrame;

    private final float units;      // physicsUnits
    private static final float SIZE = 4f; // door size in world units

    public Door(float units,
        Animation<TextureRegion> closedAnim,
        Animation<TextureRegion> openAnim,
        Vector2 center) {
        this.units      = units;
        this.closedAnim = closedAnim;
        this.openAnim   = openAnim;

        // Build 4×4‑unit static polygon
        float half = SIZE / 2f;
        float[] verts = { -half, -half,  half, -half,  half, half,  -half, half };
        PolygonObstacle poly = new PolygonObstacle(verts);
        poly.setBodyType(BodyDef.BodyType.StaticBody);
        poly.setPhysicsUnits(units);
        poly.setPosition(center);
        poly.setUserData(this);
        poly.setName("door");
        Filter filter = new Filter(); filter.groupIndex = -1;
        poly.setFilterData(filter);
        this.obstacle = poly;
        obstacle.setSensor(true);

        currentFrame = closedAnim.getKeyFrame(0f, true);
        mesh.set(-half, -half, half*2, half*2);
    }

    /**
     * Trigger door opening: switch to openAnim and set sensor
     */
    public void open() {
        if (!opened) {
            opened = true;
            animTime = 0f;
            // allow player pass through
            obstacle.getBody().getFixtureList().first().setSensor(true);
        }
    }

    public boolean isOpen() {
        return opened;
    }

    @Override
    public void update(float dt) {
        animTime += dt;
        if (opened) {
            currentFrame = openAnim.getKeyFrame(animTime, false);
        } else {
            currentFrame = closedAnim.getKeyFrame(animTime, true);
        }
        super.update(dt);
    }

    public void draw(SpriteBatch batch) {
        float px = obstacle.getX() * units;
        float py = obstacle.getY() * units;
        float w  = SIZE * units;
        float h  = SIZE * units;
        batch.draw(currentFrame,
            px - w/2, py - h/2,
            w, h);
    }
}
