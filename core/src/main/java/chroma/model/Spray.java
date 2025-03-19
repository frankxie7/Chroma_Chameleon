package chroma.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import edu.cornell.gdiac.math.Poly2;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;

public class Spray extends ObstacleSprite {

    Texture sprayTexture = null;
    public Spray(float[] points, float units, JsonValue settings) {
        // Create the physics obstacle as before.
        obstacle = new PolygonObstacle(points);
        obstacle.setDensity(0);
        obstacle.setFriction(0);
        obstacle.setRestitution(0);
        obstacle.setBodyType(BodyDef.BodyType.StaticBody);
        obstacle.setSensor(true);
        obstacle.setUserData(this);
        obstacle.setName("spray");
        obstacle.setPhysicsUnits(units);


//        float minX = points[0], minY = points[1], maxX = points[0], maxY = points[1];
//        for (int i = 2; i < points.length; i += 2) {
//            minX = Math.min(minX, points[i]);
//            minY = Math.min(minY, points[i + 1]);
//            maxX = Math.max(maxX, points[i]);
//            maxY = Math.max(maxY, points[i + 1]);
//        }
//        minX *= units;
//        minY *= units;
//        maxX *= units;
//        maxY *= units;
//        float width = maxX - minX;
//        float height = maxY - minY;
//        mesh.set(minX, minY, width+1, height+1);

//        float[] scaledPoints = new float[points.length];
//        for (int i = 0; i < points.length; i++) {
//            scaledPoints[i] = points[i] * units;
//        }
//        mesh.set(scaledPoints);

//        Poly2 poly = new Poly2(points);
//        poly.scl(units);
//        mesh.set(poly,1,6);



        // Optionally, set vertex colors (as Bomb does).
        int count = mesh.vertexCount();
        for (int i = 0; i < count; i++) {
            mesh.setColor(i, ParserUtils.parseColor(settings.get("debug"), Color.ORANGE));
        }

        // Create the spray texture if it doesn't already exist.
        if (sprayTexture == null) {
            int texSize = 128;
            Pixmap pixmap = new Pixmap(texSize, texSize, Pixmap.Format.RGBA8888);
            Color purpleTranslucent = new Color(0.5f, 0f, 0.5f, 0.1f);
            pixmap.setColor(purpleTranslucent);
            pixmap.fill();
            sprayTexture = new Texture(pixmap);
            pixmap.dispose();
            sprayTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        }
        setTexture(sprayTexture);
    }

}
