package chroma.controller;

import static chroma.model.Level.createAnimation;

import chroma.model.*;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.AssetDirectory;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.util.PooledList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
    private boolean playerHitByLaser = false;

    private int bombContactCount = 0;

    // Whether the player reaches the door
    private boolean playerWithDoor = false;

    private int sprayContactCount = 0;
    private int grateContactCount = 0;

    //Number of rays to shoot
    private int numRays = 15;
    private float rayLength = 5f;
    //Endpoints of the rays
    private Vector2[] endpoints;
    private static final int   MAX_SPRAY_VERTICES = 15;
    private static final float DUPLICATE_EPS2     = 1e-6f;   // squared dist
    private static final float COLLINEAR_EPS      = 1e-6f;   // cross-area threshold
    private static final float MIN_POLY_AREA      = 1e-6f;   // world-units
    private static final float ORIGIN_NUDGE       = 0.01f;   // world-units
    //Points
    private float[] points;
    //Goal Tile Points
    private float[] goalPoints;
    //List of Goal Tiles
    private List<Goal> goalList;
    //List of Goal Tiles
    private List<Goal> goal2List;
    //List of Goal Tiles
    private List<Goal> goal3List;
    //hits
    //private ArrayList<RayCastHit> hits;
    //Index
    private int index;
    private Door goalDoor;


    public PhysicsController(float gravityY,int numGoals,AssetDirectory directory) {
        world = new World(new Vector2(0, gravityY), false);
        world.setContactListener(this);
        objects = new PooledList<>();
        addQueue = new PooledList<>();
        points = new float[6];
        goalPoints = new float[8];
        goalList = new ArrayList<>();
        goal2List = new ArrayList<>();
        goal3List = new ArrayList<>();
        //hits = new ArrayList<>();
        index = 0;
        this.directory = directory;
        endpoints = new Vector2[numRays];
    }

    public World getWorld() {
        return world;
    }

    public List<Goal> getGoalList(){ return goalList; }
    public List<Goal> getGoal2List(){ return goal2List; }
    public List<Goal> getGoal3List(){ return goal3List; }

    public void setGoalDoor(Door door) {
        this.goalDoor = door;
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
        if (goalDoor != null && !goalDoor.isOpen() && (goals1Full() && goals2Full() && goals3Full())) {
            goalDoor.open();
        }
    }

    /**
     * Shoots rays from the chameleon outward in a fan
     * @param obstacle the Chameleon
     * @param angle the angle to shoot the rays
     */
    public void shootRays(Chameleon obstacle, float angle) {
        float angleStep = (float)(Math.PI/3.0) / numRays;
        for (int i = 0; i < numRays; i++) {
            float angleOffset   = (i - numRays/2.0f) * angleStep;
            float currentAngle  = angle + angleOffset;
            float customRadius  = computeRadiusForAngle(angleOffset);
            Vector2 direction   = new Vector2(
                (float)Math.cos(currentAngle),
                (float)Math.sin(currentAngle)
            ).nor();

            if (obstacle.getPosition() == null) {
                return;
            }
            // start from the chameleon’s “nozzle”
            Vector2 position = obstacle.getPosition().cpy();
//            if (obstacle.isFacingRight()) position.x += 0.9f;
//            if (obstacle.isFaceUp())     position.y += 0.9f;
//            if (obstacle.isFaceLeft())   position.x -= 0.9f;
//            if (obstacle.isFaceDown())   position.y -= 0.9f;

            // shoot out to the variable radius
            Vector2 endPoint = new Vector2(position).add(direction.scl(customRadius));

            RayCastCallback callback = new RayCastCallback() {
                /**
                 * Override of reportRayFixture
                 * @param fixture the fixture we hit
                 * @param point the point at which we hit
                 * @param normal normal vector
                 * @param fraction how far along the ray we hit
                 * @return fraction if we want to store the hit, -1 to ignore
                 */
                @Override
                public float reportRayFixture(Fixture fixture, Vector2 point, Vector2 normal, float fraction) {
                    Object userData = fixture.getBody().getUserData();
                    // If we are a Collision, register the hit and stop the ray there
                    if (userData instanceof Collision) {
                        endPoint.set(point);
                        return fraction;
                    } else {
                        return -1f;
                    }
                }
            };

            world.rayCast(callback, position, endPoint);
            endpoints[i] = new Vector2(endPoint);
        }
    }


    private float computeRadiusForAngle(float angleOffset) {
        float halfFanAngle = (float)(Math.PI / 4.0);
        float normalized = angleOffset / halfFanAngle;
        float centerFactor = 1.2f;
        float tailFactor = 0.1f;
        float factor = tailFactor + (centerFactor - tailFactor) * (float)Math.cos(normalized * Math.PI / 2);
        return rayLength * factor;
    }

    public void addPaint(Chameleon avatar, float units) {
        // 0) Basic checks
        if (avatar.getPosition() == null || endpoints == null || endpoints.length < 2) {
            return;
        }

        // 1) Nudge origin a tiny bit toward the first endpoint
        Vector2 origin = avatar.getPosition().cpy();
        Vector2 firstHit = endpoints[0];
        if (firstHit != null) {
            Vector2 dir = firstHit.cpy().sub(origin);
            if (dir.len2() > 0) {
                dir.nor().scl(ORIGIN_NUDGE);
                origin.add(dir);
            }
        }

        // 2) Build raw list: origin + all non-null endpoints
        List<Vector2> raw = new ArrayList<>(endpoints.length + 1);
        raw.add(origin);
        for (Vector2 p : endpoints) {
            if (p != null) {
                raw.add(p);
            }
        }
        if (raw.size() < 3) {
            return;  // not enough for a polygon
        }

        // 3) Remove near-duplicates
        List<Vector2> filtered = new ArrayList<>(raw.size());
        Vector2 last = null;
        for (Vector2 v : raw) {
            if (last == null || v.dst2(last) > DUPLICATE_EPS2) {
                filtered.add(v);
                last = v;
            }
        }


        // 4) Remove collinear points
        List<Vector2> noCollinear = new ArrayList<>(filtered.size());
        int n = filtered.size();
        for (int i = 0; i < n; i++) {
            Vector2 prev = filtered.get((i - 1 + n) % n);
            Vector2 curr = filtered.get(i);
            Vector2 next = filtered.get((i + 1) % n);

            float cross = Math.abs(
                (curr.x - prev.x) * (next.y - prev.y)
                    - (curr.y - prev.y) * (next.x - prev.x)
            );
            if (cross > COLLINEAR_EPS) {
                noCollinear.add(curr);
            }
        }
        filtered = noCollinear;

        // 5) Cap to Box2D’s 8-vertex limit
        if (filtered.size() > MAX_SPRAY_VERTICES) {
            filtered = filtered.subList(0, MAX_SPRAY_VERTICES);
        }
        if (filtered.size() < 3) {
            return;
        }

        // 6) Compute area (shoelace) and bail if too small
        float area = 0;
        int m = filtered.size();
        for (int i = 0; i < m; i++) {
            Vector2 a = filtered.get(i);
            Vector2 b = filtered.get((i + 1) % m);
            area += a.x * b.y - b.x * a.y;
        }
        area = Math.abs(area) * 0.5f;
        if (area < MIN_POLY_AREA) {
            return;
        }

        // 7) Flatten and spawn
        float[] polyVerts = new float[m * 2];
        for (int i = 0; i < m; i++) {
            Vector2 v = filtered.get(i);
            polyVerts[2 * i    ] = v.x;
            polyVerts[2 * i + 1] = v.y;
        }
        Vector2 hit    = endpoints[numRays/2];
        float   angle  = hit.cpy().sub(origin).angleDeg();
        try {
            Texture sprayTex = directory.getEntry("spray_fade", Texture.class);
            Texture sprayLaunch = directory.getEntry("spray_launch", Texture.class);
            sprayTex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            sprayLaunch.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            Animation<TextureRegion> fadeAnim   = createAnimation(sprayTex,  14, 0.29f);
            Animation<TextureRegion> launchAnim   = createAnimation(sprayLaunch,  3, 0.07f);
            Spray paintFan = new Spray(polyVerts, units,sprayTex,angle,fadeAnim,launchAnim);
            addObject(paintFan);
        } catch (Exception ignored) {
            // swallowing Box2D-asserts, triangulator AIOOBE, etc.
        }
    }



    /**
     * Creates a grid of goals from a given center
     * @param center the where the goals should created out of
     * @param units the scaled physics units
     * @param settings the Json settings
     */
    public void createGoal(Vector2 center,int gridSize,float width, float units, JsonValue settings,int id){
        for(int row = 0; row < gridSize; row++){
            for(int col = 0; col < gridSize; col++){
                float x = center.x + row * (width * 2);
                float y = center.y + col * (width * 2);
                Goal tile = createTile(x, y, width, units, settings,id);
                if(id == 1){
                    goalList.add(tile);
                }else if(id == 2){
                    goal2List.add(tile);
                }else if(id == 3){
                    goal3List.add(tile);
                }

                addObject(tile);
                index+=1;
            }
        }
    }



    /**
     * Creates a single goal tile from a given x y
     * @param x The x value
     * @param y The y value
     * @param boxRad the radius of the box
     * @param units the scaled physics units
     * @param settings the Json settings
     * @return the created Goal Tile
     */
    public Goal createTile(float x, float y,float boxRad, float units, JsonValue settings,int id){
        float x1 = x + boxRad;
        float y1 = y - boxRad;
        float x2 = x + boxRad;
        float y2 = y + boxRad;
        float x3 = x - boxRad;
        float y3 = y + boxRad;
        float x4 = x - boxRad;
        float y4 = y - boxRad;
        goalPoints[0] = x1;
        goalPoints[1] = y1;
        goalPoints[2] = x2;
        goalPoints[3] = y2;
        goalPoints[4] = x3;
        goalPoints[5] = y3;
        goalPoints[6] = x4;
        goalPoints[7] = y4;
        return new Goal(goalPoints, units, settings,id);
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
            Object o = fixture.getBody().getUserData();

            if(o instanceof Spray || o instanceof Bomb){
                return 0;
            }
            if(o instanceof Grate || o instanceof Door || o instanceof Laser){
                return -1;
            }
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

        if ((userDataA instanceof Chameleon && userDataB instanceof Grate) ||
            (userDataA instanceof Grate && userDataB instanceof Chameleon)) {
            grateContactCount++;
            if (sprayContactCount > 0 || bombContactCount > 0) {
                Chameleon player = userDataA instanceof Chameleon ? (Chameleon) userDataA : (Chameleon) userDataB;
                player.setHidden(false);
            }
        }


        if ((userDataA instanceof Chameleon && userDataB instanceof Door) ||
            (userDataA instanceof Door && userDataB instanceof Chameleon)) {
            grateContactCount++;
            if (sprayContactCount > 0 || bombContactCount > 0) {
                Chameleon player = userDataA instanceof Chameleon ? (Chameleon) userDataA : (Chameleon) userDataB;
                player.setHidden(false);
            }
        }

        // Handle spray contacts
        if ((userDataA instanceof Chameleon && userDataB instanceof Spray) ||
            (userDataA instanceof Spray && userDataB instanceof Chameleon)) {
            sprayContactCount++;
            Chameleon player = userDataA instanceof Chameleon ? (Chameleon) userDataA : (Chameleon) userDataB;
            if (grateContactCount == 0) {
                player.setHidden(true);
            }
//            System.out.println("Player entered spray; count = " + sprayContactCount);
        }

        if ((userDataA instanceof Goal && userDataB instanceof Spray) ||
            (userDataA instanceof Spray && userDataB instanceof Goal)) {
            Goal goal = userDataA instanceof Goal ? (Goal) userDataA : (Goal) userDataB;
            if (!goal.isComplete()){
                goal.setFull();
            }

        }
        // Handle bomb and goal collisons
        if ((userDataA instanceof Goal && userDataB instanceof Bomb) ||
            (userDataA instanceof Bomb && userDataB instanceof Goal)) {
            Goal goal = userDataA instanceof Goal ? (Goal) userDataA : (Goal) userDataB;
            Bomb bomb = userDataA instanceof Bomb ? (Bomb) userDataA : (Bomb) userDataB;
            if(!goal.isComplete()){
                goal.setFull();
            }
        }

        // Handle bomb contacts (unchanged or similar counter logic if needed)
        if ((userDataA instanceof Chameleon && userDataB instanceof Bomb) ||
            (userDataA instanceof Bomb && userDataB instanceof Chameleon)) {
            bombContactCount++;
            Chameleon player = userDataA instanceof Chameleon ? (Chameleon) userDataA : (Chameleon) userDataB;
            if (grateContactCount == 0) {
                player.setHidden(true);
            }
        }

        // Check enemy collisions, etc.
        if ((userDataA instanceof Chameleon && userDataB instanceof Enemy) ||
            (userDataA instanceof Enemy && userDataB instanceof Chameleon)) {
            Enemy enemy = (userDataA instanceof Enemy) ? (Enemy) userDataA : (Enemy) userDataB;
            playerCollidedWithEnemy = enemy.getType() != Enemy.Type.CAMERA1 && enemy.getType() != Enemy.Type.CAMERA2;
        }

        Object a = contact.getFixtureA().getBody().getUserData();
        Object b = contact.getFixtureB().getBody().getUserData();
        if ((a instanceof Chameleon && b instanceof Laser) ||
            (a instanceof Laser    && b instanceof Chameleon)) {
            Laser laser = (a instanceof Laser) ? (Laser)a : (Laser)b;
            if (laser.isActive()) {
                playerHitByLaser = true;
            }
        }

        // Check for win condition
        if ((userDataA instanceof Chameleon && userDataB instanceof Door) ||
            (userDataA instanceof Door && userDataB instanceof Chameleon)) {
            if(goalsFull()){
                playerWithDoor = true;
                Door door = userDataA instanceof Door ? (Door) userDataA : (Door) userDataB;
                Chameleon player = userDataA instanceof Chameleon ? (Chameleon) userDataA : (Chameleon) userDataB;
                door.setChameleon(player);
            }
        }
    }

    @Override
    public void endContact(Contact contact) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        Object userDataA = fixtureA.getBody().getUserData();
        Object userDataB = fixtureB.getBody().getUserData();

        if ((userDataA instanceof Chameleon && userDataB instanceof Grate) ||
            (userDataA instanceof Grate && userDataB instanceof Chameleon)) {
            grateContactCount--;
            if (grateContactCount < 0) grateContactCount = 0;
            Chameleon player = userDataA instanceof Chameleon ? (Chameleon) userDataA : (Chameleon) userDataB;
            if (grateContactCount == 0 && (sprayContactCount > 0 || bombContactCount > 0)) {
                player.setHidden(true);
            }
        }

        if ((userDataA instanceof Chameleon && userDataB instanceof Door) ||
            (userDataA instanceof Door && userDataB instanceof Chameleon)) {
            grateContactCount--;
            if (grateContactCount < 0) grateContactCount = 0;
            Chameleon player = userDataA instanceof Chameleon ? (Chameleon) userDataA : (Chameleon) userDataB;
            if (grateContactCount == 0 && (sprayContactCount > 0 || bombContactCount > 0)) {
                player.setHidden(true);
            }
        }

        // Handle bomb contacts ending
        if ((userDataA instanceof Chameleon && userDataB instanceof Bomb) ||
            (userDataA instanceof Bomb && userDataB instanceof Chameleon)) {
            bombContactCount--;
            if (bombContactCount <= 0 && sprayContactCount <= 0) {
                bombContactCount = 0;
                Chameleon player = userDataA instanceof Chameleon ? (Chameleon) userDataA : (Chameleon) userDataB;
                player.setHidden(false);
            }
        }

        // Handle spray contacts ending
        if ((userDataA instanceof Chameleon && userDataB instanceof Spray) ||
            (userDataA instanceof Spray && userDataB instanceof Chameleon)) {
            sprayContactCount--;
            if (sprayContactCount <= 0) {
                sprayContactCount = 0; // Ensure counter doesn't go negative
                Chameleon player = userDataA instanceof Chameleon ? (Chameleon) userDataA : (Chameleon) userDataB;
                player.setHidden(false);
//                System.out.println("Player is visible again (spray ended)!");
            } else {
//                System.out.println("Remaining spray contacts: " + sprayContactCount);
            }
        }
    }

    public boolean didPlayerHitByLaser() {
        return playerHitByLaser;
    }

    public void resetLaserFlag() {
        playerHitByLaser = false;
    }


    @Override public void preSolve(Contact contact, Manifold oldManifold) {
        Object a = contact.getFixtureA().getBody().getUserData();
        Object b = contact.getFixtureB().getBody().getUserData();
        if ((a instanceof Spray && b instanceof Chameleon) ||
            (a instanceof Chameleon && b instanceof Spray)) {
            contact.setEnabled(false);
        }
    }
    @Override public void postSolve(Contact contact, ContactImpulse impulse) {}

    public boolean didPlayerCollideWithEnemy() {
        return playerCollidedWithEnemy;
    }

    public boolean didWin() {return playerWithDoor;}

    public void resetCollisionFlags() {
        playerCollidedWithEnemy = false;
    }

    /**
     * This function determines if ALL goal regions are full
     * @return true if all full false if not
     */
    public boolean goalsFull(){
        int total = goalList.size() + goal2List.size() + goal3List.size();
        if (total == 0) {
            return true;
        }
        int numFilled = 0;
        for(Goal goal : goalList){
            if(goal != null && goal.isComplete()){
                numFilled += goalList.size();
                break;
            }
            if(goal != null && goal.isFull()){
                numFilled +=1;
            }
        }
        for(Goal goal : goal2List){
            if(goal != null && goal.isComplete()){
                numFilled += goal2List.size();
                break;
            }
            if(goal != null && goal.isFull()){
                numFilled +=1;
            }
        }
        for(Goal goal : goal3List) {
            if(goal != null && goal.isComplete()){
                numFilled += goal3List.size();
                break;
            }
            if (goal != null && goal.isFull()) {
                numFilled += 1;
            }
        }
        return ((float)numFilled / (goalList.size() + goal2List.size() + goal3List.size())) > 0.95;
    }

    /**
     * This function returns if goal 1 is full
     * @return true if goal1 is full false if not
     */
    public boolean goals1Full(){
        int numFilled = 0;
        if(goalList.isEmpty()){
            return true;
        }
        for(Goal goal : goalList){
            if(goal != null && goal.isFull()){
                numFilled +=1;
            }
        }
        return (float)numFilled / goalList.size() > 0.8;
    }

    /**
     * This function returns if goal 2 is full
     * @return true if full false if not
     */
    public boolean goals2Full(){
        int numFilled = 0;
        if(goal2List.isEmpty()){
            return true;
        }
        for(Goal goal : goal2List){
            if(goal != null && goal.isFull()){
                numFilled +=1;
            }
        }
        return (float)numFilled / goal2List.size() > 0.8;
    }

    /**
     * This function returns if goal 3 is full
     * @return true if full false if not
     */
    public boolean goals3Full(){
        int numFilled = 0;
        if(goal3List.isEmpty()){
            return true;
        }
        for(Goal goal : goal3List){
            if(goal != null && goal.isFull()){
                numFilled +=1;
            }
        }
        return (float)numFilled / goal3List.size() > 0.8;
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
