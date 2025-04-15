package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.utils.JsonValue;
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
    private static final float LIFETIME = 5f;
    private float timeAlive;
    /**
     * Creates a new Spray object from the given points and world unit scale.
     *
     * @param points   The polygon vertices in local coordinates.
     * @param units    The physics scale factor (pixels per world unit).
     */
    public Spray(float[] points, float units) {
        // Create the underlying physics shape (a PolygonObstacle).

        obstacle = new PolygonObstacle(points);
        obstacle.setBodyType(BodyDef.BodyType.StaticBody);
        obstacle.setSensor(true);       // No physical collisions; only sensor hits
        obstacle.setUserData(this);
        obstacle.setName("spray");
        obstacle.setPhysicsUnits(units);

        // Create the polygon for the mesh (rendering).

        short[] indices = { 0, 1, 2};
        Poly2 poly = new Poly2(points, indices);

        poly.scl(units);

        // If the shared texture is not created yet, make it now.
        if (sprayTexture == null) {
            int texSize = 128;
            Pixmap pixmap = new Pixmap(texSize, texSize, Pixmap.Format.RGBA8888);
            // Fill with a translucent purple color
            Color purpleTranslucent = new Color(1.0f, 0f, 0.9f, 0.85f);
            pixmap.setColor(purpleTranslucent);
            pixmap.fill();
            sprayTexture = new Texture(pixmap);
            pixmap.dispose();

            // Make the texture repeat when UV coords exceed [0..1].
            sprayTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        }
        mesh.set(poly,sprayTexture.getWidth(), sprayTexture.getHeight());
        setTexture(sprayTexture);
    }

    public void update(float dt) {
        // Calculate time existing
        timeAlive += dt;

        // Then call the superclass update
        super.update(dt);
    }

    public boolean isExpired() {
        return timeAlive >= LIFETIME;
    }
}
