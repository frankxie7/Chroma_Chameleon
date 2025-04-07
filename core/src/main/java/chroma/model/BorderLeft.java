package chroma.model;

/*
 * Surface.java
 *
 * This class is a ObstacleSprite referencing either a wall or a platform. All
 * it does is override the constructor. We do this for organizational purposes.
 * Otherwise we have to put a lot of initialization code in the scene, and that
 * just makes the scene too long and unreadable.
 *
 * Note that we have similar classes in the other scenes (rocket and ragdoll).
 * We do this because we want to keep each mini-game self-contained.
 *
 * Based on the original PhysicsDemo Lab by Don Holden, 2007
 *
 * Author:  Walker M. White
 * Version: 2/8/2025
 */

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.math.Poly2;
import edu.cornell.gdiac.math.PolyTriangulator;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;

public class BorderLeft extends ObstacleSprite {
    private Polygon polygon;

    public BorderLeft(float[] points, float units, JsonValue settings) {
        super();

        float tile = settings.getFloat("tile");

        Poly2 poly = new Poly2();
        PolyTriangulator triangulator = new PolyTriangulator();
        triangulator.set(points);
        triangulator.calculate();
        triangulator.getPolygon(poly);
        obstacle = new PolygonObstacle(points);
        obstacle.setBodyType(BodyDef.BodyType.StaticBody);
        obstacle.setDensity(settings.getFloat("density", 0));
        obstacle.setFriction(settings.getFloat("friction", 0));
        obstacle.setRestitution(settings.getFloat("restitution", 0));
        obstacle.setPhysicsUnits(units);
        obstacle.setUserData(this);
        debug = ParserUtils.parseColor(settings.get("debug"), Color.WHITE);

        // Scale the polygon and create the mesh.
        poly.scl(units);
        mesh.set(poly, tile, tile);

        // Create a Polygon for point containment checks
        this.polygon = new Polygon(points);
        this.polygon.setScale(units, units);
    }
    /**
     * Returns the primary fixture associated with this terrain object.
     *
     * @return the first fixture from the physics body, or null if not available.
     */
    public Fixture getFixture() {
        if (obstacle.getBody() != null && obstacle.getBody().getFixtureList().size > 0) {
            return obstacle.getBody().getFixtureList().first();
        }
        return null;
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
