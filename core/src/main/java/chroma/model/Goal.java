package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Filter;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.math.Poly2;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;

/**
 * A class representing a "spray" paint effect in the game.
 * It creates a translucent polygon mesh with a repeating texture.
 */
public class Goal extends ObstacleSprite {
    /**
     * A single static reference to the spray texture.
     * (Reused by all Spray objects so we only create it once.)
     */
    private static Texture sprayTexture = null;
    private boolean full = false;
    private static Texture sprayTextureFull = null;
    float units;
    float[] points;




    /**
     * Creates a new Spray object from the given points and world unit scale.
     *
     * @param points   The polygon vertices in local coordinates.
     * @param units    The physics scale factor (pixels per world unit).
     * @param settings Additional settings from a JSON config (unused here).
     */
    public Goal(float[] points, float units, JsonValue settings) {
        // Create the underlying physics shape (a PolygonObstacle).
        obstacle = new PolygonObstacle(points);
        obstacle.setDensity(0);
        obstacle.setFriction(0);
        obstacle.setRestitution(0);
        obstacle.setBodyType(BodyType.StaticBody);
        obstacle.setUserData(this);
        obstacle.setName("goal");
        obstacle.setPhysicsUnits(units);
        this.units = units;
        this.points = points;



        short[] indices = { 0, 1, 2, 0, 2, 3};
        // Create the polygon for the mesh (rendering).
        Poly2 poly = new Poly2(points,indices);

        poly.scl(units);

        // If the shared texture is not created yet, make it now.
        if (sprayTexture == null) {
            int texSize = 128;
            Pixmap pixmap = new Pixmap(texSize, texSize, Pixmap.Format.RGBA8888);
            // Fill with a translucent purple color
            Color purpleTranslucent = new Color(0.5f, 0.5f, 0.5f, 0.3f);
            pixmap.setColor(purpleTranslucent);
            pixmap.fill();
            sprayTexture = new Texture(pixmap);
            pixmap.dispose();
        }
        if(sprayTextureFull == null){
            int texSize = 128;
            Pixmap pixmap = new Pixmap(texSize, texSize, Pixmap.Format.RGBA8888);
            // Fill with a translucent purple color
            Color purpleTranslucent = new Color(0.5f, 0.0f, 0.5f, 0.5f);
            pixmap.setColor(purpleTranslucent);
            pixmap.fill();
            sprayTextureFull = new Texture(pixmap);
            pixmap.dispose();
        }
        mesh.set(poly,sprayTexture.getWidth(), sprayTexture.getHeight());
        setTexture(sprayTexture);
    }

    public void setFull(){
        setTexture(sprayTextureFull);
        full = true;
    }

    public float getX(){
        return obstacle.getX();
    }

    public float getY(){
        return obstacle.getY();
    }

    public boolean isFull(){
        return full;
    }
}
