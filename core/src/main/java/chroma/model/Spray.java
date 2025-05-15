package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.math.Poly2;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;

/**
 * A class representing a "spray" paint effect in the game.
 * It creates a translucent polygon mesh with a repeating texture.
 */
public class Spray extends ObstacleSprite {
    /**
     * A single static reference to the spray texture.
     * (Reused by all Spray objects so we only create it once.)
     */
    private static Texture sprayTexture = null;
    private static final float LIFETIME = 7f;
    private static final float FADE_DURATION = 3f;
    private float timeAlive;
    private float alpha     = 1f;
    private float[] trianglePoints;
    private Poly2 poly;
    /**
     * Creates a new Spray object from the given points and world unit scale.
     *
     * @param points   The polygon vertices in local coordinates.
     * @param units    The physics scale factor (pixels per world unit).
     */
    public Spray(float[] points, float units) {
        this.trianglePoints = points.clone(); // Store a copy of original points

        Vector2 centroid = computeCentroid(points);

        // Shift points to be relative to centroid
        for (int i = 0; i < points.length; i += 2) {
            points[i] -= centroid.x;
            points[i + 1] -= centroid.y;
        }

        // Create the underlying physics shape (a PolygonObstacle).

        obstacle = new PolygonObstacle(points);
        obstacle.setBodyType(BodyDef.BodyType.DynamicBody);
        obstacle.setSensor(true);       // No physical collisions; only sensor hits
        obstacle.setUserData(this);
        obstacle.setName("spray");
        obstacle.setPhysicsUnits(units);
        obstacle.setPosition(centroid); // Now getPosition() will work
        obstacle.setActive(true);


        // Create the polygon for the mesh (rendering).

        short[] indices = { 0, 1, 2};
        this.poly = new Poly2(points, indices);

        poly.scl(units);

        // If the shared texture is not created yet, make it now.
        if (sprayTexture == null) {
            int texSize = 128;
            Pixmap pixmap = new Pixmap(texSize, texSize, Pixmap.Format.RGBA8888);
            // Fill with a translucent purple color
            Color pinkTranslucent = new Color(1.0f, 0.1f, 0.7f, 1.0f);
            pixmap.setColor(pinkTranslucent);
            pixmap.fill();
            sprayTexture = new Texture(pixmap);
            pixmap.dispose();

            // Make the texture repeat when UV coords exceed [0..1].
            sprayTexture.setWrap(TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        }
        mesh.set(poly,sprayTexture.getWidth(), sprayTexture.getHeight());
        setTexture(sprayTexture);
    }

    public void update(float dt) {
        // Calculate time existing
        timeAlive += dt;
        float remaining = LIFETIME - timeAlive;
        if (remaining <= FADE_DURATION) {
            alpha = Math.max(0f, remaining / FADE_DURATION); // linear fade
        } else {
            alpha = 1f;
        }
        // Then call the superclass update
        super.update(dt);
    }

    private Vector2 computeCentroid(float[] points) {
        float cx = 0, cy = 0;
        int count = points.length / 2;
        for (int i = 0; i < points.length; i += 2) {
            cx += points[i];
            cy += points[i + 1];
        }
        return new Vector2(cx / count, cy / count);
    }
    @Override
    public void draw(SpriteBatch batch) {
        // 1. Save current tint
        Color saved = new Color(batch.getColor());

        // 2. Apply fading alpha
        batch.setColor(saved.r, saved.g, saved.b, alpha);

        // 3. Build the local transform (copied from ObstacleSprite)
        float x = obstacle.getX();
        float y = obstacle.getY();
        float a = obstacle.getAngle();
        float u = obstacle.getPhysicsUnits();

        transform.idt();
        transform.preRotate(a * 180f / (float)Math.PI);
        transform.preTranslate(x * u, y * u);

        // 4. Draw the mesh **with tinting enabled**
        batch.setTextureRegion(sprite);
        batch.drawMesh(mesh, transform, true);   //  ← true multiplies by batch color
        batch.setTexture((Texture) null);

        // 5. Restore tint for everything that follows
        batch.setColor(saved);
    }


    public boolean contains(Vector2 point){
        return poly.contains(point);
    }

    public boolean isExpired() {
        return timeAlive >= LIFETIME;
    }
    public void setExpired() { this.timeAlive = LIFETIME; }
    public Vector2 getPosition() { return computeCentroid(trianglePoints); }
}
