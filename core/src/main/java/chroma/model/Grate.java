package chroma.model;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.physics.box2d.BodyDef;
import edu.cornell.gdiac.physics2.BoxObstacle;
import edu.cornell.gdiac.physics2.ObstacleSprite;

public class Grate extends ObstacleSprite {

    public Grate(TextureRegion region, float units, float x, float y) {
        super();

        obstacle = new BoxObstacle(x + 0.5f, y + 0.5f, 1.0f, 1.0f);
        obstacle.setPhysicsUnits(units);
        obstacle.setBodyType(BodyDef.BodyType.StaticBody);
        obstacle.setSensor(true);
        obstacle.setUserData(this);
        obstacle.setName("grate");

        setTextureRegion(region);
        mesh.set(-units / 2.0f, -units / 2.0f, units, units);
    }
}
