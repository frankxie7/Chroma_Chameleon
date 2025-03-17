package chroma.controller;

import chroma.model.Chameleon;
import chroma.model.Enemy;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import edu.cornell.gdiac.assets.AssetDirectory;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.physics2.PolygonObstacle;
import edu.cornell.gdiac.util.PooledList;
import java.util.Iterator;

/**
 * PhysicsController
 * -----------------
 * This class is responsible for managing the Box2D physics simulation.
 * It creates and steps a Box2D World, maintains a collection of physics objects (ObstacleSprites),
 * and handles updating and disposing of these objects. It also implements ContactListener to allow
 * global processing of collision events. (idk if we should have this)
 */
public class PhysicsController implements ContactListener {
    private World world;
    public PooledList<ObstacleSprite> objects;
    public PooledList<ObstacleSprite> addQueue;
    private static final int WORLD_VELOC = 6;
    private static final int WORLD_POSIT = 2;
    private AssetDirectory directory;
    private boolean playerCollidedWithEnemy = false;



    public PhysicsController(float gravityY,AssetDirectory directory) {
        world = new World(new Vector2(0, gravityY), false);
        world.setContactListener(this);
        objects = new PooledList<>();
        addQueue = new PooledList<>();
        this.directory = directory;
    }

    public World getWorld() {
        return world;
    }

    public void addObject(ObstacleSprite obj) {
        objects.add(obj);
        obj.getObstacle().activatePhysics(world);
    }

    public void queueObject(ObstacleSprite obj) {
        addQueue.add(obj);
    }

    public void update(float dt) {
        // Add newly queued objects
        while (!addQueue.isEmpty()) {
            ObstacleSprite spr = addQueue.poll();
            objects.add(spr);
            spr.getObstacle().activatePhysics(world);
        }
        // Step the physics world
        world.step(dt, WORLD_VELOC, WORLD_POSIT);
        // Update each object and remove it if needed
        Iterator<PooledList<ObstacleSprite>.Entry> iterator = objects.entryIterator();
        while (iterator.hasNext()) {
            PooledList<ObstacleSprite>.Entry entry = iterator.next();
            ObstacleSprite spr = entry.getValue();
            if (spr.getObstacle().isRemoved()) {
                spr.getObstacle().deactivatePhysics(world);
                entry.remove();
            } else {
                spr.update(dt);
            }
        }
    }

    public void addPaint(Chameleon avatar) {
        for (int i = 0; i < avatar.getNumRays() - 1; i++) {
            if (avatar.getPosition() != null
                && avatar.getEndpoints()[i] != null) {
                Vector2 v1 = avatar.getPosition();
                Vector2 v2 = avatar.getEndpoints()[i].cpy();
                Vector2 v3 = avatar.getEndpoints()[i + 1].cpy();
                float x1 = v1.x;
                float y1 = v1.y;
                float x2 = v2.x;
                float y2 = v2.y;
                float x3 = v3.x;
                float y3 = v3.y;
                float[] points = new float[]{x1,y1,x2,y2,x3,y3};
                PolygonObstacle triangle = new PolygonObstacle(points);
                triangle.setPosition(x1*1.75f,y1*1.75f);
                triangle.setBodyType(BodyType.StaticBody);
                triangle.setSensor(true);
                Texture Tex = directory.getEntry("platform-chameleon", Texture.class);
                ObstacleSprite sprite = new ObstacleSprite(triangle,false);
                sprite.setDebugColor(Color.ORANGE);
                sprite.setTexture(Tex);
                addObject(sprite);
                System.out.println("complete");
            }
        }
    }

    /**
     * Casts a ray from start to end and returns the first fixture hit.
     * Used for AI vision and paint collision detection.
     *
     * @param start The starting point of the ray.
     * @param end The ending point of the ray.
     * @return The first fixture hit by the ray, or null if nothing is hit.
     */
    public Fixture raycast(Vector2 start, Vector2 end) {
        if (world == null || start == null || end == null) return null;
        if (Float.isNaN(start.x) || Float.isNaN(start.y) ||
            Float.isNaN(end.x) || Float.isNaN(end.y)) return null;

        final Fixture[] hitFixture = {null};
        world.rayCast((fixture, point, normal, fraction) -> {
            hitFixture[0] = fixture;
            return fraction;
        }, start, end);
        return hitFixture[0];
    }

    public void castVisionCone(Vector2 start, float angle, float coneWidth, int numRays) {
        float stepAngle = coneWidth / (numRays - 1);
        for (int i = 0; i < numRays; i++) {
            float rayAngle = angle - coneWidth / 2 + stepAngle * i;
            Vector2 direction = new Vector2(1, 0).rotateRad(rayAngle);
            direction.scl(100);
            Fixture hitFixture = raycast(start, start.cpy().add(direction));
            if (hitFixture != null) {
                // Handle the collision (if any)
            }
        }
    }

    @Override
    public void beginContact(Contact contact) {
        // You can process global collision events here.
        // For example, if the player collides with the goal door,
        // you might notify a higher-level controller.
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        Object userDataA = fixtureA.getBody().getUserData();
        Object userDataB = fixtureB.getBody().getUserData();

        // Check if the player collides with an enemy
        if ((userDataA instanceof Chameleon && userDataB instanceof Enemy) ||
            (userDataA instanceof Enemy && userDataB instanceof Chameleon)) {
            playerCollidedWithEnemy = true;
        }
    }

    @Override public void endContact(Contact contact) {}
    @Override public void preSolve(Contact contact, Manifold oldManifold) {}
    @Override public void postSolve(Contact contact, ContactImpulse impulse) {}

    public boolean didPlayerCollideWithEnemy() {
        return playerCollidedWithEnemy;
    }

    public void resetCollisionFlags() {
        playerCollidedWithEnemy = false;
    }

    public void dispose() {
        for (ObstacleSprite spr : objects) {
            spr.getObstacle().deactivatePhysics(world);
        }
        objects.clear();
        addQueue.clear();
        if (world != null) {
            world.dispose();
        }
        world = null;
    }
}
