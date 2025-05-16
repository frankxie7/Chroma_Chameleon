package chroma.model;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.utils.ShortArray;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.graphics.SpriteSheet;
import edu.cornell.gdiac.math.Poly2;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;

/**
 * A class representing a "spray" paint effect in the game.
 * It creates a translucent polygon mesh with a repeating texture.
 */
public class Spray extends ObstacleSprite {
    /**
     * A single static reference to the spray texture.
     * (Reused by all Spray objects so we only create it once.)
     */
    private static Texture sprayTexture = null;
    private static final float LIFETIME = 8f;
    private static final float FADE_DURATION = 4f;
    private float timeAlive;
    private float alpha     = 1f;
    private float[] trianglePoints;
    private final float angleDeg;
    private Poly2 poly;
    /**
     * Creates a new Spray object from the given points and world unit scale.
     *
     * @param points   The polygon vertices in local coordinates.
     * @param units    The physics scale factor (pixels per world unit).
     */
//    public Spray(float[] points, float units) {
//        this.trianglePoints = points.clone(); // Store a copy of original points
//
//        Vector2 centroid = computeCentroid(points);
//
//        // Shift points to be relative to centroid
//        for (int i = 0; i < points.length; i += 2) {
//            points[i] -= centroid.x;
//            points[i + 1] -= centroid.y;
//        }
//
//        // Create the underlying physics shape (a PolygonObstacle).
//
//        obstacle = new PolygonObstacle(points);
//        obstacle.setBodyType(BodyDef.BodyType.DynamicBody);
//        obstacle.setSensor(true);       // No physical collisions; only sensor hits
//        obstacle.setUserData(this);
//        obstacle.setName("spray");
//        obstacle.setPhysicsUnits(units);
//        obstacle.setPosition(centroid); // Now getPosition() will work
//        obstacle.setActive(true);
//
//
//        // Create the polygon for the mesh (rendering).
//
//        short[] indices = { 0, 1, 2};
//        this.poly = new Poly2(points, indices);
//
//        poly.scl(units);
//
//        // If the shared texture is not created yet, make it now.
//        if (sprayTexture == null) {
//            int texSize = 128;
//            Pixmap pixmap = new Pixmap(texSize, texSize, Pixmap.Format.RGBA8888);
//            // Fill with a translucent purple color
//            Color pinkTranslucent = new Color(1.0f, 0.1f, 0.7f, 1.0f);
//            pixmap.setColor(pinkTranslucent);
//            pixmap.fill();
//            sprayTexture = new Texture(pixmap);
//            pixmap.dispose();
//
//            // Make the texture repeat when UV coords exceed [0..1].
//            sprayTexture.setWrap(TextureWrap.Repeat, Texture.TextureWrap.Repeat);
//        }
//        mesh.set(poly,sprayTexture.getWidth(), sprayTexture.getHeight());
//        setTexture(sprayTexture);
//    }
    // How we drive the per-frame fade animation
    private final Animation<TextureRegion> fadeAnim;

    public Spray(float[] points,
        float units,
        Texture sprayTex,
        float angleDeg,Animation<TextureRegion>fadeAnim) {

        this.angleDeg = angleDeg;
        this.sprayTexture = sprayTex;
        Vector2 origin = new Vector2(points[0], points[1]);
        this.fadeAnim = fadeAnim;
        float[] localPx = points.clone();
        for (int i = 0; i < localPx.length; i += 2) {
            localPx[i]   -= origin.x;
            localPx[i+1] -= origin.y;
        }

        float cos = MathUtils.cosDeg(-angleDeg);
        float sin = MathUtils.sinDeg(-angleDeg);
        for (int i = 0; i < localPx.length; i += 2) {
            float px = localPx[i];
            float py = localPx[i+1];
            localPx[i]   = px * cos - py * sin;
            localPx[i+1] = px * sin + py * cos;
        }
        /* ------------------------------------------------------------
         * 2)
         * ---------------------------------------------------------- */
        float[] localMeters = localPx.clone();
        for (int i = 0; i < localMeters.length; i++) {
            localMeters[i] *= units;
        }

        obstacle = new PolygonObstacle(localPx);
        obstacle.setBodyType(BodyDef.BodyType.DynamicBody);
        obstacle.setSensor(true);
        obstacle.setUserData(this);
        obstacle.setName("spray");
        obstacle.setPhysicsUnits(units);
        obstacle.setAngle(angleDeg * MathUtils.degreesToRadians);
        obstacle.setPosition(origin);

        obstacle.setActive(true);

        /* ------------------------------------------------------------
         * 3) Poly2
         * ---------------------------------------------------------- */
        EarClippingTriangulator triangulator = new EarClippingTriangulator();
        ShortArray tris = triangulator.computeTriangles(localMeters);
        Poly2 poly = new Poly2(localMeters, tris.toArray());   // 像素坐标

        /* ------------------------------------------------------------
         * 4) Mesh
         * ---------------------------------------------------------- */
        sprayTex.setWrap(Texture.TextureWrap.ClampToEdge,
            Texture.TextureWrap.ClampToEdge);

        this.sprite = new SpriteSheet(sprayTex, 1, 1);

        float texW = sprayTex.getWidth()*1.2f/14f;
        float texH = sprayTex.getHeight()*1.2f;

        mesh.set(poly, 0, -texH * 0.5f, texW, texH);
    }




//    public void update(float dt) {
//        // Calculate time existing
//        timeAlive += dt;
//        float remaining = LIFETIME - timeAlive;
//        if (remaining <= FADE_DURATION) {
//            alpha = Math.max(0f, remaining / FADE_DURATION); // linear fade
//        } else {
//            alpha = 1f;
//        }
//        // Then call the superclass update
//        super.update(dt);
//    }
@Override
public void update(float dt) {
    // 1) advance our timer
    timeAlive += dt;

    // 2) compute fade window
    float fadeStart = LIFETIME - FADE_DURATION;

    TextureRegion frame;
    if (timeAlive < fadeStart) {
        // before fade: hold first frame, full opacity
        frame = fadeAnim.getKeyFrame(0f, false);
        alpha = 1f;
    } else if (timeAlive < LIFETIME) {
        // during fade: advance animation & lerp alpha → 0
        float animTime = timeAlive - fadeStart;
        frame = fadeAnim.getKeyFrame(animTime, false);
        alpha = Math.max(0f, 1.2f - (animTime / FADE_DURATION));
    } else {
        // after end: last frame (or blank) at zero opacity
        frame = fadeAnim.getKeyFrame(FADE_DURATION, false);
        alpha = 0f;
    }

    // 3) swap in the new frame
    sprite.setRegion(frame);

    // 4) run normal movement/removal logic
    super.update(dt);
}


    private Vector2 computeCentroid(float[] points) {
        float cx = 0, cy = 0;
        int count = points.length / 2;
        for (int i = 0; i < points.length; i += 2) {
            cx += points[i];
            cy += points[i + 1];
        }
        return new Vector2(cx / count, cy / count);
    }
    @Override
    public void draw(SpriteBatch batch) {
        /* 1) 保存当前批次颜色 */
        Color saved = new Color(batch.getColor());

        /* 2) 应用透明度（淡出） */
        batch.setColor(saved.r, saved.g, saved.b, alpha);

        /* 3) 构建局部变换（无需旋转时 angleDeg 可为 0） */
        float x = obstacle.getX();
        float y = obstacle.getY();
        float u = obstacle.getPhysicsUnits();

        transform.idt();
        transform.preRotate(angleDeg);
        transform.preTranslate(x * u, y * u);

        Gdx.app.log("SprayDebug",
            "Sprite size: " +
                sprite.getRegionWidth() + "×" +
                sprite.getRegionHeight()
        );
        /* 4) 绘制多边形网格 */
        batch.setTextureRegion(sprite);          // 绑定贴图
        batch.drawMesh(mesh, transform, true);   // true -> 乘以 batch 颜色
        batch.setTexture((Texture) null);        // 解绑

        /* 5) 恢复批次颜色 */
        batch.setColor(saved);
    }




    public boolean contains(Vector2 point){
        return poly.contains(point);
    }

    public boolean isExpired() {
        return timeAlive >= LIFETIME;
    }
    public void setExpired() { this.timeAlive = LIFETIME; }
    public Vector2 getPosition() { return computeCentroid(trianglePoints); }
}
