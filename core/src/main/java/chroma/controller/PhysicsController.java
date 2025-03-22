package chroma.controller;

import chroma.model.Chameleon;
import chroma.model.Enemy;
import chroma.model.Spray;
import chroma.model.Bomb;
import chroma.model.Terrain;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.BSpline;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.AssetDirectory;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.physics2.Obstacle;
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

    private boolean playerWithBomb = false;

    private int sprayContactCount = 0;

    //Number of rays to shoot
    private int numRays = 30;
    //Length of the rays
    private float rayLength = 3f;
    //Endpoints of the rays
    private Vector2[] endpoints;
    //Points
    private float[] points;

    public PhysicsController(float gravityY,AssetDirectory directory) {
        world = new World(new Vector2(0, gravityY), false);
        world.setContactListener(this);
        objects = new PooledList<>();
        addQueue = new PooledList<>();
        points = new float[6];
        this.directory = directory;
        endpoints = new Vector2[numRays];
    }

    public World getWorld() {
        return world;
    }

    public void addObject(ObstacleSprite obj) {
        objects.add(obj);
        obj.getObstacle().activatePhysics(world);
    }

    public void removeObject(ObstacleSprite obj) {
        if (obj != null) {
            obj.getObstacle().deactivatePhysics(world);
            objects.remove(obj);
        }
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

    public void shootRays(Chameleon obstacle,float angle) {
        float angleStep = (float) Math.PI/2f / (float) numRays;
        for (int i = 0; i < numRays; i++) {
            float angleOffset = (i - numRays / 2f) * angleStep;
            Vector2 direction = new Vector2((float)  Math.cos(angle + angleOffset),
                (float) Math.sin(angle +angleOffset)).nor();
            Vector2 endPoint = new Vector2(obstacle.getPosition()).add(direction.scl(rayLength));
            RayCastCallback callback = (fixture, point, normal, fraction) -> {
                Object userData = fixture.getBody().getUserData();
                if (userData instanceof Spray && ((Spray) userData).isExpired()) {
                    return -1f;
                }
                endPoint.set(point);
                return fraction;
            };
            world.rayCast(callback,obstacle.getPosition(),endPoint);
            endpoints[i] = new Vector2(endPoint);
        }
    }

    public void addPaint(Chameleon avatar, float units, JsonValue settings) {
        for (int i = 0; i < numRays - 1; i++) {
            if (avatar.getPosition() != null
                && endpoints[i] != null) {
                Vector2 v1 = avatar.getPosition();
                Vector2 v2 = endpoints[i];
                Vector2 v3 = endpoints[i + 1];
                float x1 = v1.x;
                float y1 = v1.y;
                float x2 = v2.x;
                float y2 = v2.y;
                float x3 = v3.x;
                float y3 = v3.y;
                points[0] = x1;
                points[1] = y1;
                points[2] = x2;
                points[3] = y2;
                points[4] = x3;
                points[5] = y3;
                if(!isConcave(points) && isCounterClockwise(points)){
                    try{
                        Spray paintTriangle = new Spray(points, units, settings);
                        addObject(paintTriangle);
                    }catch(IllegalArgumentException ignored){
                    }
                }
            }
        }
    }

    /**
     * Determines whether or not points are CCW or not
     * @param vertices the vertices to check
     * @return True if CCW
     */
    private static boolean isCounterClockwise(float[] vertices) {
        float sum = 0;
        for (int i = 0; i < vertices.length; i += 2) {
            int next = (i + 2) % vertices.length;
            sum += (vertices[next] - vertices[i]) * (vertices[i + 1] + vertices[next + 1]);
        }
        return sum < 0; // CCW if sum < 0, CW if sum > 0
    }

    /**
     * Returns true if points will create a concave shape
     * @param vertices points to use
     * @return true if Concave
     */
    public static boolean isConcave(float[] vertices) {
        int numPoints = vertices.length / 2;  // Number of (x, y) pairs
        boolean sign = false;  // To track the direction of turns

        // Loop through each set of three consecutive vertices
        for (int i = 0; i < numPoints; i++) {
            int prev = (i - 1 + numPoints) % numPoints;  // Previous vertex
            int current = i;  // Current vertex
            int next = (i + 1) % numPoints;  // Next vertex

            // Get the (x, y) coordinates of the vertices
            float x1 = vertices[2 * prev], y1 = vertices[2 * prev + 1];
            float x2 = vertices[2 * current], y2 = vertices[2 * current + 1];
            float x3 = vertices[2 * next], y3 = vertices[2 * next + 1];

            // Calculate the cross product to determine the direction of the turn
            float crossProduct = (x2 - x1) * (y3 - y2) - (y2 - y1) * (x3 - x2);

            if (crossProduct != 0) {  // Ignore collinear points
                if (!sign) {
                    sign = crossProduct > 0;  // Set the initial direction
                } else {
                    // If the sign changes, it's a concave polygon
                    if (sign != (crossProduct > 0)) {
                        return true; // Polygon is concave
                    }
                }
            }
        }
        return false; // All turns are in the same direction; polygon is convex
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

    @Override
    public void beginContact(Contact contact) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        Object userDataA = fixtureA.getBody().getUserData();
        Object userDataB = fixtureB.getBody().getUserData();

        // Handle spray contacts
        if ((userDataA instanceof Chameleon && userDataB instanceof Spray) ||
            (userDataA instanceof Spray && userDataB instanceof Chameleon)) {
            sprayContactCount++;
            Chameleon player = userDataA instanceof Chameleon ? (Chameleon) userDataA : (Chameleon) userDataB;
            player.setHidden(true);
            System.out.println("Player entered spray; count = " + sprayContactCount);
        }

        // Handle bomb contacts (unchanged or similar counter logic if needed)
        if ((userDataA instanceof Chameleon && userDataB instanceof Bomb) ||
            (userDataA instanceof Bomb && userDataB instanceof Chameleon)) {
            playerWithBomb = true;
            Chameleon player = userDataA instanceof Chameleon ? (Chameleon) userDataA : (Chameleon) userDataB;
            player.setHidden(true);
            System.out.println("Player is hidden in bomb!");
        }

        // Check enemy collisions, etc.
        if ((userDataA instanceof Chameleon && userDataB instanceof Enemy) ||
            (userDataA instanceof Enemy && userDataB instanceof Chameleon)) {
            playerCollidedWithEnemy = true;
        }
    }

    @Override
    public void endContact(Contact contact) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        Object userDataA = fixtureA.getBody().getUserData();
        Object userDataB = fixtureB.getBody().getUserData();

        // Handle bomb contacts ending
        if ((userDataA instanceof Chameleon && userDataB instanceof Bomb) ||
            (userDataA instanceof Bomb && userDataB instanceof Chameleon)) {
            playerWithBomb = false;
            // Only set visible if no spray contact remains.
            if (sprayContactCount <= 0) {
                Chameleon player = userDataA instanceof Chameleon ? (Chameleon) userDataA : (Chameleon) userDataB;
                player.setHidden(false);
                System.out.println("Player is visible again (bomb ended)!");
            }
        }

        // Handle spray contacts ending
        if ((userDataA instanceof Chameleon && userDataB instanceof Spray) ||
            (userDataA instanceof Spray && userDataB instanceof Chameleon)) {
            sprayContactCount--;
            if (sprayContactCount <= 0 && !playerWithBomb) {
                sprayContactCount = 0; // Ensure counter doesn't go negative
                Chameleon player = userDataA instanceof Chameleon ? (Chameleon) userDataA : (Chameleon) userDataB;
                player.setHidden(false);
                System.out.println("Player is visible again (spray ended)!");
            } else {
                System.out.println("Remaining spray contacts: " + sprayContactCount);
            }
        }
    }

    @Override public void preSolve(Contact contact, Manifold oldManifold) {}
    @Override public void postSolve(Contact contact, ContactImpulse impulse) {}

    public boolean didPlayerCollideWithEnemy() {
        return playerCollidedWithEnemy;
    }

    public void resetCollisionFlags() {
        playerCollidedWithEnemy = false;
    }

    public boolean didPlayerCollideWithBomb() {
        return playerWithBomb;
    }

    public void resetBombFlags() {
        playerWithBomb = false;
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
