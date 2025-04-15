package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.math.Poly2;
import edu.cornell.gdiac.math.PolyTriangulator;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;

public class WallDepth extends ObstacleSprite {

    private Polygon polygon;
    public WallDepth(float[] points, float units, JsonValue settings) {
        super();

        float tile = settings.getFloat("tile");

        // Create the full polygon for drawing.
        Poly2 poly = new Poly2();
        PolyTriangulator triangulator = new PolyTriangulator();
        triangulator.set(points);
        triangulator.calculate();
        triangulator.getPolygon(poly);

        // --- Create a collision polygon that is shorter than the drawn shape ---
        // For the drawn rectangle, the points are expected in this order:
        //   0: top-left (x1,y1)
        //   1: bottom-left (x1, y1 - depth)
        //   2: bottom-right (x2, y2 - depth)
        //   3: top-right (x2, y2)
        //
        // We introduce a collision offset (the amount by which we raise the bottom vertices)
        // so that the physics hitbox is shorter than the drawn wall.
        float collisionOffset = settings.getFloat("collision_offset", 0.5f);
        float[] collisionPoints = new float[points.length];
        // Copy top vertices unchanged.
        collisionPoints[0] = points[0]; // top-left x
        collisionPoints[1] = points[1]; // top-left y

        // Adjust the bottom vertices upward by the collision offset.
        collisionPoints[2] = points[2]; // bottom-left x
        collisionPoints[3] = points[3] + collisionOffset; // bottom-left y

        collisionPoints[4] = points[4]; // bottom-right x
        collisionPoints[5] = points[5] + collisionOffset; // bottom-right y

        // Top-right vertex remains the same.
        collisionPoints[6] = points[6]; // top-right x
        collisionPoints[7] = points[7]; // top-right y

        // --- Create the physics obstacle using the collision polygon ---
        obstacle = new PolygonObstacle(collisionPoints);
        obstacle.setBodyType(BodyDef.BodyType.StaticBody);
        // Turn off sensor so that collision is enabled.
        obstacle.setSensor(false);
        obstacle.setPhysicsUnits(units);
        obstacle.setUserData(this);
        debug = ParserUtils.parseColor(settings.get("debug"), Color.WHITE);

        // Scale the polygon and create the mesh for drawing.
        poly.scl(units);
        mesh.set(poly, tile, tile);
        int count = mesh.vertexCount();
        for (int i = 0; i < count; i++) {
            mesh.setColor(i, new Color(0.6f, 0.6f, 0.6f, 1f));
        }
        // Create a Polygon for point containment checks (for any additional logic).
        this.polygon = new Polygon(points);
        this.polygon.setScale(units, units);
    }
}
