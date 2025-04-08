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

        // 如果需要重复纹理，则建议在 Level 中设置 texture 的 wrap 模式
        // 这里仅保存多边形数据用于确定位置和尺寸
        // 注意：如果背景仅为矩形，可直接根据tileSize计算出bounds，否则可以利用多边形的包围矩形
        polygon = new Polygon(points);
        polygon.setScale(units, units);
        bounds = polygon.getBoundingRectangle();
    }


    public void setTexture(Texture texture) {
        this.texture = texture;
    }


    public void draw(SpriteBatch batch) {
        // 直接绘制整个 bounds 区域的纹理（适用于单块填充的情况）
        batch.draw(texture, bounds.x, bounds.y, bounds.width, bounds.height);
    }

    public Rectangle getBounds() {
        return bounds;
    }
}
