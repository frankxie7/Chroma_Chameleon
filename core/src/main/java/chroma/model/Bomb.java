package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
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
        obstacle.setBodyType( BodyType.StaticBody);
        obstacle.setSensor(true);
        obstacle.setUserData( this );
        obstacle.setName( "bomb" );

        debug = ParserUtils.parseColor( settings.get( "debug" ), Color.WHITE );

        mesh.set( -radius, -radius, 2 * radius, 2 * radius );
    }
}
