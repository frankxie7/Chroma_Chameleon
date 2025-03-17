package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.physics2.BoxObstacle;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.WheelObstacle;

public class Bomb extends ObstacleSprite{

    public Bomb (float units, JsonValue settings, Vector2 pos) {

        float s = settings.getFloat( "size" );
        float radius = s * units / 2.0f;

        // Create a circular obstacle
        obstacle = new WheelObstacle( pos.x, pos.y, s/2 );
        obstacle.setDensity( settings.getFloat( "density", 0 ) );
        obstacle.setFriction( settings.getFloat( "friction", 0 ) );
        obstacle.setRestitution( settings.getFloat( "restitution", 0 ) );
        obstacle.setPhysicsUnits( units );
        obstacle.setBodyType( BodyDef.BodyType.StaticBody);
        obstacle.setSensor(true);
        obstacle.setUserData( this );
        obstacle.setName( "bomb" );

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
