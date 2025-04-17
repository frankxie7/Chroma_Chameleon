package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Filter;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;

public class Grate extends ObstacleSprite {

    public Grate(float[] vertices, float units, JsonValue settings) {
        super();

        obstacle = new PolygonObstacle(vertices, 0, 0);
        obstacle.setPhysicsUnits(units);
        obstacle.setBodyType(BodyDef.BodyType.StaticBody);
        obstacle.setSensor(true); // It's a sensor, so chameleon won't physically bump into it
        obstacle.setUserData(this);
        obstacle.setName("grate");

        debug = ParserUtils.parseColor(settings.get("debug"), Color.GRAY);

        // Setup mesh bounds (you could make this smarter later)
        mesh.set(vertices);
    }
}
