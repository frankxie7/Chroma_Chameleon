package chroma.model;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.ParserUtils;
import com.badlogic.gdx.graphics.Color;
import edu.cornell.gdiac.graphics.SpriteBatch;

public class BackgroundTile {
    private Polygon polygon;
    private Rectangle bounds;
    private Texture texture;
    private float units;
    private float tileSize;

    public BackgroundTile(float[] points, float units, JsonValue settings) {
        this.units = units;
        this.tileSize = settings.getFloat("tile");

        polygon = new Polygon(points);
        polygon.setScale(units, units);
        bounds = polygon.getBoundingRectangle();
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
}
