package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Filter;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;

public class Grate extends ObstacleSprite {

    public Grate(float[] points, float units, JsonValue settings) {

        obstacle = new PolygonObstacle(points);
        obstacle.setBodyType(BodyType.StaticBody);
        obstacle.setSensor(true);
        obstacle.setUserData(this);
        obstacle.setName("grate");
        obstacle.setPhysicsUnits(units);

    }
}
