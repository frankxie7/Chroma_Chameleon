package chroma.model;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import com.badlogic.gdx.graphics.Color;
import edu.cornell.gdiac.graphics.SpriteBatch;

// This creates a pure visual tile that has no physics property.
public class BackgroundTile {
    private Polygon polygon;
    private Rectangle bounds;
    private Texture texture;
    private float units;
    private float tileSize;
    private float width;
    private float x;
    private float y;

    public BackgroundTile(float[] points, float units, JsonValue settings) {
        this.units = units;
        this.tileSize = settings.getFloat("tile");

        polygon = new Polygon(points);
        polygon.setScale(units, units);
        bounds = polygon.getBoundingRectangle();
        width = settings.getInt("width");
    }


    public void setTexture(Texture texture) {
        this.texture = texture;
    }


    public void draw(SpriteBatch batch) {
        batch.draw(texture, bounds.x, bounds.y, bounds.width, bounds.height);
    }

    public Rectangle getBounds() {
        return bounds;
    }

    public float getWidth(){return width;}

}
