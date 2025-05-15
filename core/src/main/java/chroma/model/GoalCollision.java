
package chroma.model;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Filter;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.math.Poly2;
import edu.cornell.gdiac.math.PolyTriangulator;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;


public class GoalCollision extends ObstacleSprite {
    private Polygon polygon;
    private float units;
    private Vector2 pos;
    private TextureRegion notFullTex;
    private TextureRegion fullTex;
    private static final float WIDTH = 4f;  // goal width in world units
    private static final float HEIGHT = 5f;// goal height in world units
    private boolean complete = false;

    public GoalCollision(Vector2 center, float units, Texture notFull, Texture full) {
        super();
        this.units = units;


        float halfWidth = WIDTH / 2f;
        float halfHeight = HEIGHT / 2f;

        float[] verts = {
            -halfWidth, -halfHeight,   // bottom-left
            halfWidth, -halfHeight,   // bottom-right
            halfWidth,  halfHeight,   // top-right
            -halfWidth,  halfHeight    // top-left
        };
        PolygonObstacle poly = new PolygonObstacle(verts);
        poly.setBodyType(BodyDef.BodyType.StaticBody);
        poly.setPhysicsUnits(units);
        poly.setPosition(center);
        poly.setUserData(this);
        poly.setName("goal");
        Filter filter = new Filter(); filter.groupIndex = -1;
        poly.setFilterData(filter);
        this.obstacle = poly;

        // Fix texture scaling

        fullTex = new TextureRegion(full);
        notFullTex = new TextureRegion(notFull);

        // Scale the polygon and create the mesh
        mesh.set(-halfWidth, -halfHeight, halfWidth*2, halfHeight*2);

    }

    public Vector2 getPos() {return this.pos;}
    public Polygon getPolygon(){
        return this.polygon;
    }

    public void setComplete() {
        this.complete = true;
    }

    public void draw(SpriteBatch batch) {
        float px = obstacle.getX() * units;
        float py = obstacle.getY() * units;
        float w  = WIDTH * units;
        float h  = HEIGHT * units;
        if(complete){
            batch.draw(fullTex,
                px - w/2, py - h/2,
                w, h);
        }else{
            batch.draw(notFullTex,
                px - w/2, py - h/2,
                w, h);
        }

    }


}
