package chroma.model;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.physics.box2d.Body;
import edu.cornell.gdiac.graphics.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import edu.cornell.gdiac.physics2.PolygonObstacle;
import edu.cornell.gdiac.physics2.ObstacleSprite;

/**
 * A 1×1-tile sensor that kills the player on contact when active.
 */
public class Laser extends ObstacleSprite {
    private static final float SIZE = 1f;       // in world units (tile size)
    private final float units;                  // physicsUnits
    private final TextureRegion region;
    private boolean active = false;

    public Laser(float units, TextureRegion region, Vector2 center) {
        this.units  = units;
        this.region = region;

        // Build 1×1-unit static polygon, centered on tile
        float half = SIZE / 2f;
        float[] verts = { -half, -half,  half, -half,  half, half,  -half, half };
        PolygonObstacle poly = new PolygonObstacle(verts);
        poly.setBodyType(BodyDef.BodyType.StaticBody);
        poly.setPhysicsUnits(units);
        poly.setPosition(center);
        poly.setUserData(this);           // for collision lookup
        poly.setSensor(true);             // no physical blocking
        this.obstacle = poly;

        // Match rendering mesh to physics bounds
        mesh.set(-half, -half, SIZE, SIZE);
        // Start deactivated
//        obstacle.getBody().setActive(false);
    }

    /** Turn the laser on or off. */
    /** Turn the laser on or off. */
    public void toggle(boolean on) {
        this.active = on;
        Body body = obstacle.getBody();
        if (body != null) {
            body.setActive(on);
        }
    }


    public boolean isActive() { return active; }

    @Override
    public void update(float dt) {
        // no animation for now
        super.update(dt);
    }

    @Override
    public void draw(SpriteBatch batch) {
        if (!active) { return; }  // draw only when on
        float px = obstacle.getX() * units - (SIZE*units)/2f;
        float py = obstacle.getY() * units - (SIZE*units)/2f;
        batch.draw(region, px, py, SIZE*units, SIZE*units);
    }
}
