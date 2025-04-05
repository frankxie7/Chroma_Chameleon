package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Filter;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.WheelObstacle;

public class Bomb extends ObstacleSprite{
    private static final float LIFETIME = 3f;
    private float timeAlive;

    public Bomb (float units, JsonValue settings, Vector2 pos) {

        float s = settings.getFloat( "size" );
        float radius = s * units / 2.0f;

        // Create a circular obstacle
        obstacle = new WheelObstacle( pos.x, pos.y, s/2 );
        obstacle.setDensity( settings.getFloat( "density", 0 ) );
        obstacle.setFriction( settings.getFloat( "friction", 0 ) );
        obstacle.setRestitution( settings.getFloat( "restitution", 0 ) );
        obstacle.setPhysicsUnits( units );
        obstacle.setBodyType( BodyType.DynamicBody);
        obstacle.setSensor(true);
        obstacle.setUserData( this );
        obstacle.setName( "bomb" );

        debug = ParserUtils.parseColor( settings.get( "debug" ), Color.WHITE );
        mesh.set( -radius, -radius, 2 * radius, 2 * radius );
        int count = mesh.vertexCount();
        for (int i = 0; i < count; i++) {
            mesh.setColor(i, new Color(0.5f,0.0f,1.0f,0.5f));
        }

        timeAlive = 0;
    }

    @Override
    public void update(float dt) {
        // Calculate time existing
        timeAlive += dt;

        // Then call the superclass update
        super.update(dt);
    }

    public boolean isExpired() {
        return timeAlive >= LIFETIME;
    }
}
