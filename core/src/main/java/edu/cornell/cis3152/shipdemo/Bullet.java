/*
 * Bullet.java
 *
 * This class is a ObstacleSprite referencing a bullet. All it does is override
 * the constructor. We do this for organizational purposes. Otherwise we have
 * to put a lot of initialization code in the scene, and that just makes the
 * scene too long and unreadable.
 *
 * Based on the original PhysicsDemo Lab by Don Holden, 2007
 *
 * Author:  Walker M. White
 * Version: 2/8/2025
 */
 package edu.cornell.cis3152.shipdemo;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.WheelObstacle;

/**
 * A bullet fired by Traci
 *
 * An ObstacleSprite is a sprite (specifically a textured mesh) that is
 * connected to a obstacle. It is designed to be the same size as the
 * physics object, and it tracks the physics object, matching its position
 * and angle at all times.
 *
 * The reason we use a textured mesh instead of a image is because it allows
 * us more control over the size and shape of the image. We will talk about
 * how to use these later in class. For now, just notice how we create meshes
 */
public class Bullet extends ObstacleSprite {

    /**
     * Creates a bullet with the given physics units and settings
     *
     * The physics units are used to size the mesh relative to the physics
     * body. The other attributes (pos, right) are used to position to bullet
     * relative to Traci.
     *
     * @param units     The physics units
     * @param settings  The bullet physics constants
     * @param pos       Traci's position
     * @param right     Whether to go to the right of Traci
     */
    public Bullet(float units, JsonValue settings, Vector2 pos, boolean right) {
        float offset = settings.getFloat( "offset", 0 );
        offset *= (right ? 1 : -1);
        float s = settings.getFloat( "size" );
        float radius = s * units / 2.0f;

        // Create a circular obstacle
        obstacle = new WheelObstacle( pos.x + offset, pos.y, s/2 );
        obstacle.setDensity( settings.getFloat( "density", 0 ) );
        obstacle.setPhysicsUnits( units );
        obstacle.setBullet( true );
        obstacle.setGravityScale( 0 );
        obstacle.setUserData( this );
        obstacle.setName( "bullet" );

        float speed = settings.getFloat( "speed", 0 );
        speed *= (right ? 1 : -1);
        obstacle.setVX( speed );

        debug = ParserUtils.parseColor( settings.get( "debug" ), Color.WHITE );

        // While the bullet is a circle, we want to create a rectangular mesh.
        // That is because the image is a rectangle. The width/height of the
        // rectangle should be the same as the diameter of the circle (adjusted
        // by the physics units). Note that radius has ALREADY been multiplied
        // by the physics units. In addition, for all meshes attached to a
        // physics body, we want (0,0) to be in the center of the mesh. So
        // the method call below is (x,y,w,h) where x, y is the bottom left.
        mesh.set( -radius, -radius, 2 * radius, 2 * radius );
    }

}
