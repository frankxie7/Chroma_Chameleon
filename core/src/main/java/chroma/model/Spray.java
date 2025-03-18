package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.math.Poly2;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;

public class Spray extends ObstacleSprite {

    public Spray(float[] points, float units, JsonValue settings) {
        // 1) Create the physics obstacle in Box2D coordinates
        obstacle = new PolygonObstacle(points);
        obstacle.setDensity(0);
        obstacle.setFriction(0);
        obstacle.setRestitution(0);
        obstacle.setBodyType(BodyDef.BodyType.StaticBody);
        obstacle.setSensor(true);
        obstacle.setUserData(this);
        obstacle.setName("spray");
        // 2) Tell the Obstacle how many screen pixels = 1 Box2D unit
        obstacle.setPhysicsUnits(units);
        // 3) Use a separate Poly2 to define the polygon mesh in scaled coordinates
        Poly2 poly = new Poly2(points);
        poly.scl(units); // multiply all vertex coords by 'units'
        mesh.set(poly);  // or mesh.set(poly, tile, tile) if you want repeated tiling

        // 4) Set debug color (so you can see the shape in debug mode)
        debug = ParserUtils.parseColor(settings.get("debug"), Color.ORANGE);

    }
}
