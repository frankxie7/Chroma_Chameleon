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
        int count = mesh.vertexCount();
        for (int i = 0; i < count; i++) {
            mesh.setColor(i, new Color(0.6f, 0.6f, 0.6f, 1f));;
        }
        // Create a Polygon for point containment checks
        this.polygon = new Polygon(points);
        this.polygon.setScale(units, units);
    }
}

