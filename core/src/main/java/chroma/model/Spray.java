package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;


public class Spray extends ObstacleSprite{

    public Spray (float[] points, JsonValue settings) {


        // Create a circular obstacle
        obstacle = new PolygonObstacle(points);
        obstacle.setDensity( 0);
        obstacle.setFriction( 0 );
        obstacle.setRestitution( 0 );
        obstacle.setPosition(points[0]*1.75f,points[1]*1.75f);
        obstacle.setBodyType( BodyDef.BodyType.StaticBody);
        obstacle.setSensor(true);
        obstacle.setUserData( this );
        obstacle.setName( "spray" );

        debug = ParserUtils.parseColor( settings.get( "debug" ), Color.ORANGE);

    }
}
