/*
 * Door.java
 *
 * This class is a ObstacleSprite referencing the "win door". All it does is
 * override the constructor. We do this for organizational purposes. Otherwise
 * we have to put a lot of initialization code in the scene, and that just makes
 * the scene too long and unreadable.
 *
 * Based on the original PhysicsDemo Lab by Don Holden, 2007
 *
 * Author:  Walker M. White
 * Version: 2/8/2025
 */
 package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.physics2.BoxObstacle;
import edu.cornell.gdiac.physics2.ObstacleSprite;

/**
 * The win door.
 *
 * An ObstacleSprite is a sprite (specifically a textured mesh) that is
 * connected to a obstacle. It is designed to be the same size as the
 * physics object, and it tracks the physics object, matching its position
 * and angle at all times.
 *
 * The reason we use a textured mesh instead of a image is because it allows
 * us more control over the size and shape of the image. We will talk about
 * how to use these later in class. For now, just notice how we create meshes.
 *
 * The associated obstacle is a sensor. That means that collisions will be
 * detected, but nothing happens to the game physics. Instead, we decide the
 * result of the collision.
 */
public class Door extends ObstacleSprite {

    /**
     * Creates a door with the given physics units and settings
     *
     * The physics units are used to size the mesh relative to the physics
     * body. All other attributes are defined by the JSON file
     *
     * @param units     The physics units
     * @param settings  The door physics constants
     */
    public Door(float units, JsonValue settings) {
        super();

        float x = settings.get("pos").getFloat(0);
        float y = settings.get("pos").getFloat(1);
        float s = settings.getFloat("size");
        float size = s * units;

        obstacle = new BoxObstacle(x, y, s, s);
        obstacle.setPhysicsUnits(units);
        obstacle.setBodyType(BodyDef.BodyType.StaticBody);
        obstacle.setSensor(true);
        obstacle.setUserData(this);
        obstacle.setName("goal");

        debug = ParserUtils.parseColor(settings.get("debug"), Color.WHITE);

        mesh.set(-size / 2.0f, -size / 2.0f, size, size);
    }

    /**
     * Returns the primary fixture associated with this door.
     *
     * @return the first fixture from the physics body, or null if not available.
     */
    public Fixture getFixture() {
        if (obstacle.getBody() != null && obstacle.getBody().getFixtureList().size > 0) {
            return obstacle.getBody().getFixtureList().first();
        }
        return null;
    }
}
