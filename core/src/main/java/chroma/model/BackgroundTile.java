package chroma.model;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import edu.cornell.gdiac.graphics.SpriteBatch;

// This creates a pure visual tile that has no physics property.
public class BackgroundTile {
    private TextureRegion region;
    private float units;
    private float x, y;
    private final Rectangle bounds = new Rectangle();

    /**
     * @param region the sub-texture for this tile
     * @param units  physics-unit conversion factor
     */
    public BackgroundTile(TextureRegion region, float units) {
        this.region = region;
        this.units   = units;
    }

    /** set its tile-grid position (in tile units) */
    public void setPosition(int tx, int ty) {
        this.x = tx * units;
        this.y = ty * units;
    }

    public void draw(SpriteBatch batch) {
        // draw the region at world coords (x,y)
        batch.draw(region, x, y, units, units);
    }

    public Rectangle getBounds() {
        bounds.set(x, y, units, units);
        return bounds;

    }
}

