package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.math.Poly2;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;


public class Spray extends ObstacleSprite{

    public Spray (float[] points,JsonValue settings) {
        super();

        // Create a circular obstacle
        Poly2 poly = new Poly2();
        poly.vertices.addAll(points);
        poly.indices.add((short)0,(short)2,(short)1);
        poly.scl(1.75f);
        obstacle = new PolygonObstacle(poly);
        obstacle.setDensity( 0);
        obstacle.setFriction( 0 );
        obstacle.setRestitution( 0 );
        obstacle.setPosition(points[0],points[1]);
        obstacle.setBodyType( BodyDef.BodyType.StaticBody);
        obstacle.setSensor(true);
        obstacle.setUserData( this );
        obstacle.setName( "spray" );

        setDebugColor(Color.ORANGE);

    }
}
