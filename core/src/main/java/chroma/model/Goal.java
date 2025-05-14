package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Filter;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.math.Poly2;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;

/**
 * A class representing a "Goal" in the game
 * It creates a translucent polygon with three different possible textures
 */
public class Goal extends ObstacleSprite {
    private static Texture sprayTexture = null;
    private boolean full = false;
    private static Texture sprayTextureFull = null;
    private static Texture sprayTextureComplete = null;
    private boolean complete = false;
    private Polygon polygon;
    float units;
    float[] points;
    private Poly2 poly;
    int id;

    /**
     * Creates a new Goal object from the given points and world unit scale.
     *
     * @param points   The polygon vertices in local coordinates.
     * @param units    The physics scale factor (pixels per world unit).
     * @param settings Additional settings from a JSON config (unused here).
     * @param id This is the goal to which this goal tile is attached to
     */
    public Goal(float[] points, float units, JsonValue settings,int id) {
        // Create the underlying physics shape (a PolygonObstacle).
        obstacle = new PolygonObstacle(points);
        obstacle.setBodyType(BodyType.StaticBody);
        obstacle.setUserData(this);
        obstacle.setName("goal");
        obstacle.setPhysicsUnits(units);
        this.id = id;
        this.units = units;
        this.points = points;


        // Filter allows collisions with everything except other enemies
        Filter goalFilter = new Filter();
        goalFilter.groupIndex = -1;
        obstacle.setFilterData(goalFilter);

        short[] indices = { 0, 1, 2, 0, 2, 3};
        // Create the polygon for the mesh (rendering).
        this.poly = new Poly2(points,indices);
        this.poly.scl(units);
        this.polygon = new Polygon(points);


        // If the shared texture is not created yet, make it now.
        if (sprayTexture == null) {
            int texSize = 128;
            Pixmap pixmap = new Pixmap(texSize, texSize, Pixmap.Format.RGBA8888);
            Color grey = new Color(0.5f, 0.5f, 0.5f, 0.0f);
            pixmap.setColor(grey);
            pixmap.fill();
            sprayTexture = new Texture(pixmap);
            sprayTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
            pixmap.dispose();
        }
        if(sprayTextureFull == null){
            int texSize = 1;
            Pixmap pixmap = new Pixmap(texSize, texSize, Pixmap.Format.RGBA8888);
            // Fill with a translucent pink color if we just got hit
            Color pinkTranslucent = new Color(1.0f, 0.4f, 0.7f, 0.7f);
            pixmap.setColor(pinkTranslucent);
            pixmap.fill();
            sprayTextureFull = new Texture(pixmap,true);
            sprayTextureFull.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            sprayTextureFull.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
            pixmap.dispose();
        }
        if(sprayTextureComplete == null){
            int texSize = 256;
            Pixmap pixmap = new Pixmap(texSize, texSize, Pixmap.Format.RGBA8888);
            // Fill with a green if the goal region we are apart of is full
            Color purpleTranslucent = new Color(1.0f, 1.0f, 0.0f, 0.4f);
            pixmap.setColor(purpleTranslucent);
            pixmap.fill();
            sprayTextureComplete = new Texture(pixmap,true);
            sprayTextureComplete.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            sprayTextureComplete.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
            pixmap.dispose();
        }
        mesh.set(poly,sprayTexture.getWidth(), sprayTexture.getHeight());
        setTexture(sprayTexture);
    }

    /**
     * Sets this goal region to full (we just got hit)
     */
    public void setFull(){
        setTexture(sprayTextureFull);
        full = true;
    }

    /**
     * Sets this goal tile as "complete" our goal is full enough
     */
    public void setComplete(){
        setTexture(sprayTextureComplete);
        complete = true;
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
    public boolean isComplete(){return complete;}
    public float getId(){return id;}

    public void update(float dt) {
        // Then call the superclass update
        super.update(dt);
    }

    public boolean contains(Vector2 point) {
        if (polygon.contains(point.x, point.y)) {
            return true;
        }

        float[] vertices = polygon.getTransformedVertices();
        int numVertices = vertices.length / 2;

        for (int i = 0; i < numVertices; i++) {
            int next = (i + 1) % numVertices;
            float x1 = vertices[i * 2], y1 = vertices[i * 2 + 1];
            float x2 = vertices[next * 2], y2 = vertices[next * 2 + 1];

            // Check if point is on the right or top edge
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
}
