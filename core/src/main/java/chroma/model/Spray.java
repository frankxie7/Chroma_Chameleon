package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.math.Poly2;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;

public class Spray extends ObstacleSprite {

    // A static texture for spray objects, created once.
    private static Texture sprayTexture = null;

    public Spray(float[] points, float units, JsonValue settings) {
        // Create the physics obstacle using the provided points.
        obstacle = new PolygonObstacle(points);
        obstacle.setDensity(0);
        obstacle.setFriction(0);
        obstacle.setRestitution(0);
        obstacle.setBodyType(BodyDef.BodyType.StaticBody);
        obstacle.setSensor(true);
        obstacle.setUserData(this);
        obstacle.setName("spray");

        // Set the scale for converting Box2D units to screen pixels.
        obstacle.setPhysicsUnits(units);

        // Build the mesh from the scaled polygon.
        Poly2 poly = new Poly2(points);
        poly.scl(units);
        mesh.set(poly);

        // Set the debug color (used in debug mode).
        debug = ParserUtils.parseColor(settings.get("debug"), Color.ORANGE);

        // Create a static purple translucent texture if not already created.
        if (sprayTexture == null) {
            int texSize = 128; // Texture size in pixels.
            Pixmap pixmap = new Pixmap(texSize, texSize, Pixmap.Format.RGBA8888);
            // Purple color with 50% opacity.
            Color purpleTranslucent = new Color(0.5f, 0f, 0.5f, 0.5f);
            pixmap.setColor(purpleTranslucent);
            pixmap.fill();
            sprayTexture = new Texture(pixmap);
            pixmap.dispose();
        }
        // Assign the texture to this Spray.
        setTexture(sprayTexture);
    }
}
