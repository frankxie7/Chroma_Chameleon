package chroma.controller;

import chroma.model.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.AssetDirectory;
import edu.cornell.gdiac.physics2.ObstacleSprite;
import edu.cornell.gdiac.util.PooledList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

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
    private int numRays = 12;
    //Length of the rays
    private float rayLength = 5f;
    //Endpoints of the rays
    private Vector2[] endpoints;
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
    public List<Goal> getGoal3List(){ return goal2List; }

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
        if (goalDoor != null && !goalDoor.isOpen() && goalsFull()) {
            goalDoor.open();
        }
    }

    /**
     * RayCastHit s a simple class containing an object hit within a raycast and the fraction
     */
    static class RayCastHit {
        public Object object;
        public float fraction;

        public RayCastHit(Object object, float fraction) {
            this.object = object;
            this.fraction = fraction;
        }
    }

    /**
     * Shoots rays from the chameleon outward in a fan
     * @param obstacle the Chameleon
     * @param angle the angle to shoot the rays
     */
    public void shootRays(Chameleon obstacle, float angle) {
        float angleStep = (float)(Math.PI / 2.0) / numRays;
        //hits.clear();
        for (int i = 0; i < numRays; i++) {
            float angleOffset = (i - numRays / 2.0f) * angleStep;
            float currentAngle = angle + angleOffset;
            float customRadius = computeRadiusForAngle(angleOffset);
            Vector2 direction = new Vector2((float)Math.cos(currentAngle), (float)Math.sin(currentAngle)).nor();
            if (obstacle.getPosition() == null) {
                return;
            }
            Vector2 position = obstacle.getPosition().cpy();
            //Depening on orientation change the position of our raycast
            if(obstacle.isFacingRight()) {
                position.x = position.x + 0.9f;
            }
            if(obstacle.isFaceUp()){
                position.y = position.y + 0.9f;
            }
            if(obstacle.isFaceLeft()){
                position.x = position.x - 0.9f;
            }
            if(obstacle.isFaceDown()){
                position.y = position.y - 0.9f;
            }
            Vector2 endPoint = new Vector2(position).add(direction.scl(customRadius));

            //We are using a priority queue here for efficiency (min stack could be better sigh)
            RayCastCallback callback = new RayCastCallback() {
                /**
                 * Override of reportRayFixture
                 * @param fixture the fixture we hit
                 * @param point the point at which we hit
                 * @param normal normal vector
                 * @param fraction the fraction the ray was able to make it to end
                 * @return fraction if we want to store the hit, -1 to ignore
                 */
                @Override
                public float reportRayFixture(Fixture fixture, Vector2 point, Vector2 normal, float fraction) {
                    Object userData = fixture.getBody().getUserData();
                    //If we are Goal or Collision add to list otherwise ignore
                    //Note we are not adding full goals to the list
                    if(userData instanceof Collision){
                        //hits.add(new RayCastHit(userData,fraction));
                        endPoint.set(point);
                        return fraction;
                    }else{
                        return -1;
                    }
                }
            };
            world.rayCast(callback, position, endPoint);
            //hits.sort((h1,h2) -> Float.compare(h1.fraction, h2.fraction));
//            for(RayCastHit o : hits){
//                if(o.object instanceof Collision){
//                    //Once we hit our Collision object get out
//                    break;
//                }
//                if(o.object instanceof Goal){
//                    //If we are a Goal set ourselves to full
//                    Goal g = (Goal) o.object;
//                    g.setFull();
//                }
//            }
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
    /**
     * Adds the Spray objects created by the raycasting code
     * @param avatar the Chameleon
     * @param units the scaled physics units
     */
    public void addPaint(Chameleon avatar, float units) {
        for(int i = 0; i< endpoints.length - 1;i++)
            if (avatar.getPosition() != null
                && endpoints[i] != null) {
                Vector2 v1 = avatar.getPosition().cpy();
                if(avatar.isFacingRight()){
                    v1.x = v1.x + 0.9f;
                }
                if(avatar.isFaceLeft()){
                    v1.x = v1.x - 0.9f;
                }
                if(avatar.isFaceUp()){
                    v1.y = v1.y + 0.9f;
                }
                if(avatar.isFaceDown()){
                    v1.y = v1.y - 0.9f;
                }
                //Replace with for loop over endpoints array
                //Replace points array with new data type
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
                try{
                    Spray paintTriangle = new Spray(points, units);
                    addObject(paintTriangle);
                }
                catch(IllegalArgumentException ignored){}
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
                }
                if(id == 2){
                    goal2List.add(tile);
                }
                if(id == 3){
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
            if(o instanceof Grate || o instanceof Door){
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
            goal.setFull();
        }
        // Handle bomb and goal collisons
        if ((userDataA instanceof Goal && userDataB instanceof Bomb) ||
            (userDataA instanceof Bomb && userDataB instanceof Goal)) {
            Goal goal = userDataA instanceof Goal ? (Goal) userDataA : (Goal) userDataB;
            Bomb bomb = userDataA instanceof Bomb ? (Bomb) userDataA : (Bomb) userDataB;
            goal.setFull();
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
            playerCollidedWithEnemy = enemy.getType() != Enemy.Type.CAMERA;
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

                player.setFalling(true);
                door.playChameleonFallAnimation();
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
            if(goal != null && goal.isFull()){
                numFilled +=1;
            }
        }
        for(Goal goal : goal2List){
            if(goal != null && goal.isFull()){
                numFilled +=1;
            }
        }
        for(Goal goal : goal3List) {
            if (goal != null && goal.isFull()) {
                numFilled += 1;
            }
        }
        return ((float)numFilled / (goalList.size() + goal2List.size() + goal3List.size())) > 0.90;
    }

    /**
     * This function returns if goal 1 is full
     * @return true if goal1 is full false if not
     */
    public boolean goals1Full(){
        int numFilled = 0;
        for(Goal goal : goalList){
            if(goal != null && goal.isFull()){
                numFilled +=1;
            }
        }
        return (float)numFilled / goalList.size() > 0.90;
    }

    /**
     * This function returns if goal 2 is full
     * @return true if full false if not
     */
    public boolean goals2Full(){
        int numFilled = 0;
        for(Goal goal : goal2List){
            if(goal != null && goal.isFull()){
                numFilled +=1;
            }
        }
        return (float)numFilled / goal2List.size() > 0.90;
    }

    /**
     * This function returns if goal 3 is full
     * @return true if full false if not
     */
    public boolean goals3Full(){
        int numFilled = 0;
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
