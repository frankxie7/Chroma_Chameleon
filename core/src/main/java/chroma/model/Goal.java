package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Vector2;
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
    private Poly2 poly;
    private int id;

    /**
     * Creates a new Spray object from the given points and world unit scale.
     *
     * @param points   The polygon vertices in local coordinates.
     * @param units    The physics scale factor (pixels per world unit).
     * @param settings Additional settings from a JSON config (unused here).
     */
    public Goal(float[] points, float units, JsonValue settings,int id) {
        // Create the underlying physics shape (a PolygonObstacle).
        obstacle = new PolygonObstacle(points);
        obstacle.setBodyType(BodyType.StaticBody);
        obstacle.setUserData(this);
        obstacle.setName("goal");
        obstacle.setPhysicsUnits(units);
        this.units = units;
        this.points = points;
        this.id = id;

        // Filter allows collisions with everything except other enemies
        Filter goalFilter = new Filter();
        goalFilter.groupIndex = -1;
        obstacle.setFilterData(goalFilter);

        short[] indices = { 0, 1, 2, 0, 2, 3};
        // Create the polygon for the mesh (rendering).
        this.poly = new Poly2(points,indices);
        this.poly.scl(units);


        // If the shared texture is not created yet, make it now.
        if (sprayTexture == null) {
            int texSize = 128;
            Pixmap pixmap = new Pixmap(texSize, texSize, Pixmap.Format.RGBA8888);
            // Fill with a translucent purple color
            Color grey = new Color(0.5f, 0.5f, 0.5f, 0.0f);
            pixmap.setColor(grey);
            pixmap.fill();
            sprayTexture = new Texture(pixmap);
            pixmap.dispose();
        }
        if(sprayTextureFull == null){
            int texSize = 128;
            Pixmap pixmap = new Pixmap(texSize, texSize, Pixmap.Format.RGBA8888);
            // Fill with a translucent purple color
            Color purpleTranslucent = new Color(1.0f, 0f, 1.0f, 0.2f);
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

    public boolean contains(Vector2 point) {
        // Get transformed vertices from Poly2 (already scaled correctly)
        Vector2[] polyVertices = poly.getVertices();
        float[] vertices = new float[polyVertices.length * 2];

        // Convert Vector2 array to float array for Polygon
        for (int i = 0; i < polyVertices.length; i++) {
            vertices[i * 2] = polyVertices[i].x; // No need to apply units again, already done in Poly2
            vertices[i * 2 + 1] = polyVertices[i].y;
        }

        // Create the polygon (no need to scale, Poly2 is already transformed)
        Polygon polygon = new Polygon(vertices);

        // Check if the point is inside the polygon
        if (polygon.contains(point.x, point.y)) {
            return true;
        }

        // Check if the point is on any of the edges
        float[] transformedVertices = polygon.getTransformedVertices();
        int numVertices = transformedVertices.length / 2;

        for (int i = 0; i < numVertices; i++) {
            int next = (i + 1) % numVertices;
            float x1 = transformedVertices[i * 2], y1 = transformedVertices[i * 2 + 1];
            float x2 = transformedVertices[next * 2], y2 = transformedVertices[next * 2 + 1];

            // Check if the point is exactly on an edge
            if ((point.x == x1 && point.y == y1) || (point.x == x2 && point.y == y2)) {
                return true;
            }
            if ((point.x == x1 && point.x == x2 && point.y >= Math.min(y1, y2) && point.y <= Math.max(y1, y2)) ||
                (point.y == y1 && point.y == y2 && point.x >= Math.min(x1, x2) && point.x <= Math.max(x1, x2))) {
                return true;
            }
        }
        return false;
    }

    public void update(float dt) {
        // Then call the superclass update
        super.update(dt);
    }

    public int getId(){return id;}
}
