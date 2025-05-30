
package chroma.model;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import edu.cornell.gdiac.math.Poly2;
import edu.cornell.gdiac.math.PolyTriangulator;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;


public class Collision extends ObstacleSprite {
    private Polygon polygon;
    private float units;
    private Vector2 pos;// physics-to-world scale

    public Collision(float[] points, float units,Vector2 location) {
        super();
        this.units = units;

        Poly2 poly = new Poly2();
        PolyTriangulator triangulator = new PolyTriangulator();
        triangulator.set(points);
        triangulator.calculate();
        triangulator.getPolygon(poly);
        obstacle = new PolygonObstacle(points);
        obstacle.setBodyType(BodyDef.BodyType.StaticBody);
        obstacle.setPhysicsUnits(units);
        obstacle.setUserData(this);
        obstacle.setName("collision");
        pos = location;

        // Scale the polygon and create the mesh.
        poly.scl(units);
        mesh.set(poly, 16, 16);

        // Create a Polygon for point containment checks
        this.polygon = new Polygon(points);

    }

    public Vector2 getPos() {return this.pos;}
    public Polygon getPolygon(){
        return this.polygon;
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
