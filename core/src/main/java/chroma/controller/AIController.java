package chroma.controller;

import chroma.model.*;
import chroma.model.Enemy.Type;
import com.badlogic.gdx.ai.pfa.Heuristic;
import com.badlogic.gdx.ai.pfa.indexed.IndexedAStarPathFinder;
import com.badlogic.gdx.ai.pfa.indexed.IndexedGraph;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.RayCastCallback;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.ai.pfa.Connection;
import com.badlogic.gdx.ai.pfa.GraphPath;
import com.badlogic.gdx.ai.pfa.DefaultGraphPath;

import java.util.ArrayList;
import java.util.List;

public class AIController {
    public enum State {
        WANDER, PATROL, ALERT, CHASE
    }

    private GameplayController gameplay;
    private PhysicsController physics;
    private Enemy enemy;
    private Type type;
    private Chameleon player;
    private List<Terrain> walls; // list of walls for pathfindinga
    private List<Terrain> platforms;
    private Goal[] goals;
    private State state;

    // Base detection range and dynamic detection range.
    private float detectionRange;
    private float fov;
    private float rotationSpeedWander = 45f; // Degrees per second
    private float rotationSpeedAlert = 240f;
    //    private float rotationSpeedChase = 240f;
    private boolean rotatingClockwise = true;

    // GOAL:
    private Vector2 target;

    // ENEMY SPEED:
    private float wanderSpeed = 1f;
    private float chaseSpeed = 1.5f;

    // CHASE:
    private float chaseMaxSpeed = 7.5f;

    // ALERT:
    private float alertMaxSpeed = 6f;
    private float alertLength = 3f;
    private float alertTimer = alertLength;

    // WANDER:
    private float wanderMaxSpeed = 5f;
    private float timeToChangeTarget = 2f;
    private float wanderTimer = 0f;
    private float margin = 1f; // margin inside room boundaries

    // PATROL:
    private boolean patrol;
    private List<float[]> patrolPath;
    private int patrolIndex = 0; // Track the current waypoint index

    private boolean playerDetected = false;
    private boolean wasChasingOrAlert = false;

    private NavGraph graph = new NavGraph();

    private float scale;

    public AIController(Enemy enemy, GameplayController gameplayController, PhysicsController physicsController, Level level) {
        this.gameplay = gameplayController;
        this.physics = physicsController;
        this.enemy = enemy;
        this.patrol = enemy.getPatrol();
        this.patrolPath = enemy.getPatrolPath();
        this.type = enemy.getType();
        this.player = level.getAvatar();
        this.walls = level.getWalls();
        this.goals = physics.getGoalList();
        this.detectionRange = enemy.getDetectionRange();
        this.fov = enemy.getFov();
        state = State.WANDER;
        pickNewWanderTarget();

        float worldWidth = gameplay.getWorldWidth();
        float worldHeight = gameplay.getWorldHeight();

        // Scaling for nodes
//        OrthographicCamera camera = gameplay.getCamera();
//        float screenHeight = camera.viewportHeight;
//        this.scale = screenHeight / worldHeight;
        this.scale = gameplay.getUnits();


        // Build navigation graph
        buildGraph(worldWidth, worldHeight);
    }

    private void pickNewWanderTarget() {
        float minX = margin;
        float maxX = gameplay.getWorldWidth() - margin;
        float minY = margin;
        float maxY = gameplay.getWorldHeight() - margin;
        float x = MathUtils.random(minX, maxX);
        float y = MathUtils.random(minY, maxY);
        target = new Vector2(x, y);
    }

    // Checks if a straight line from start to end is blocked by any wall.
    private boolean isLineBlocked(Vector2 start, Vector2 end) {
        Fixture hitFixture = physics.raycast(start, end);

        // If nothing is hit, return false (line is not blocked)
        if (hitFixture == null) {
            return false;
        }

        return true;
    }

    private boolean isBlocked(Vector2 position) {
        for (Terrain wall : walls) {
            if (wall.contains(position)) {
                return true;
            }
        }
//        for (Terrain platform : platforms) {
//            if (platform.contains(position)) {
//                return true;
//            }
//        }
//        for (Goal goal : goals) {
//            if (goal.contains(position)) {
//                return true;
//            }
//        }
        return false;
    }

    public class NavNode {
        public final Vector2 position;
        private final Array<Connection<NavNode>> connections = new Array<>();

        public NavNode(float x, float y) {
            this.position = new Vector2(x, y);
        }

        public void addConnection(Connection<NavNode> connection) {
            connections.add(connection);
        }

        public Array<Connection<NavNode>> getConnections() {
            return connections;
        }
    }

    public class NavConnection implements Connection<NavNode> {
        private final NavNode fromNode;
        private final NavNode toNode;
        private final float cost;

        public NavConnection(NavNode from, NavNode to) {
            this.fromNode = from;
            this.toNode = to;
            this.cost = from.position.dst(to.position); // Distance as cost
        }

        @Override
        public float getCost() {
            return cost;
        }

        @Override
        public NavNode getFromNode() {
            return fromNode;
        }

        @Override
        public NavNode getToNode() {
            return toNode;
        }
    }

    public class NavGraph implements IndexedGraph<NavNode> {
        private Array<NavNode> nodes = new Array<>();

        @Override
        public int getIndex(NavNode node) {
            return nodes.indexOf(node, true); // Returns a unique index
        }

        @Override
        public int getNodeCount() {
            return nodes.size;
        }

        @Override
        public Array<Connection<NavNode>> getConnections(NavNode fromNode) {
            return fromNode.getConnections();
        }

        public void addNode(NavNode node) {
            nodes.add(node);
        }

        public Vector2 getNearestWalkableNode(Vector2 point) {
            NavNode closest = null;
            float minDist = Float.MAX_VALUE;
            for (NavNode node : graph.nodes) {
                float dist = node.position.dst2(point); // dst2 is faster than dst
                if (dist < minDist) {
                    minDist = dist;
                    closest = node;
                }
            }
            return closest != null ? closest.position : point; // Default to original if no match
        }
    }

    public class EuclideanHeuristic implements Heuristic<NavNode> {
        @Override
        public float estimate(NavNode node, NavNode endNode) {
            return node.position.dst(endNode.position);
        }
    }

    private void buildGraph(float worldWidth, float worldHeight) {
        // Create nodes that are not inside obstacles
        for (float x = 0; x < worldWidth + 1; x++) {
            for (float y = 0; y < worldHeight + 1; y++) {
                Vector2 pos = new Vector2(x * scale, y * scale);
                if (!isBlocked(pos)) {
                    NavNode node = new NavNode(x, y);
                    graph.addNode(node);
                }
            }
        }

        Array<NavNode> nodeCopy = new Array<>(graph.nodes);

        // Connect adjacent nodes if no wall is blocking them
        for (NavNode node : graph.nodes) {
            for (NavNode other : nodeCopy) {
                if (!node.equals(other) && node.position.dst2(other.position) < 2f) {
                    if (!isLineBlocked(node.position, other.position)) {
                        node.addConnection(new NavConnection(node, other));
                    }
                }
            }
        }
    }

    public class PathFinder {
        private final IndexedAStarPathFinder<NavNode> pathFinder;
        private final EuclideanHeuristic heuristic = new EuclideanHeuristic();

        public PathFinder(NavGraph graph) {
            this.pathFinder = new IndexedAStarPathFinder<>(graph);
        }

        public Array<Vector2> findPath(Vector2 start, Vector2 end) {
            NavNode startNode = findClosestNode(start);
            NavNode endNode = findClosestNode(end);

            GraphPath<NavNode> nodePath = new DefaultGraphPath<>();
            boolean success = pathFinder.searchNodePath(startNode, endNode, heuristic, nodePath);

            Array<Vector2> path = new Array<>();
            if (success) {
                for (NavNode node : nodePath) {
                    path.add(node.position);
                }
            }
            return path;
        }

        public NavNode findClosestNode(Vector2 position) {
            NavNode closest = null;
            float minDist = Float.MAX_VALUE;

            for (NavNode node : graph.nodes) {
                float dist = node.position.dst(position);
                if (dist < minDist) {
                    minDist = dist;
                    closest = node;
                }
            }
            return closest;
        }
    }

    private Vector2 getNextPathPoint(Vector2 start, Vector2 end) {
        PathFinder pathFinder = new PathFinder(graph);
        Array<Vector2> path = pathFinder.findPath(start, end);

        if (path.size > 1) {
            lastPath = path; // Store for debugging
            lastGoal = end;
            return path.get(1);
        }
        return null;
    }

    private void moveTowards(Vector2 target, float speed) {
        Vector2 enemyPos = enemy.getPosition();
        Vector2 direction = new Vector2(target).sub(enemyPos);

        if (direction.len() > 0.1f) { // Prevent division by zero
            direction.nor();
        }

        // Set movement variables like the player
        float hmove = direction.x;
        float vmove = direction.y;

        // Apply movement similar to the player
//        System.out.println(hmove * speed + ", " + vmove * speed);
        enemy.setMovement(hmove * speed);
        enemy.setVerticalMovement(vmove * speed);
    }

    public void update(float delta) {
        Vector2 enemyPos = enemy.getPosition();
        Vector2 playerPos = player.getPosition();
        if (enemyPos == null || playerPos == null) {
            return;
        }

        playerDetected = false;
        float distanceToPlayer = enemyPos.dst(playerPos);
        boolean isCamera = type == Type.CAMERA;
        boolean guardInRange = distanceToPlayer <= detectionRange;

        if (!player.isHidden() && (isCamera || guardInRange)) {
            float angleLooking = enemy.getRotation();
            float halfFOV = (float) Math.toRadians(fov / 2);
            int numRays = (int) (fov / 5);
            float angleStep = (halfFOV * 2) / (numRays - 1);

            for (int i = 0; i < numRays; i++) {
                float rayAngle = angleLooking - halfFOV + (i * angleStep);
                Vector2 direction = new Vector2((float) Math.cos(rayAngle), (float) Math.sin(rayAngle));
                float rayLength = isCamera ? 9999f : detectionRange;
                Vector2 rayEnd = enemyPos.cpy().add(direction.scl(rayLength));

                final Vector2 rayHit = rayEnd.cpy(); // Initialize the ray hit position

                RayCastCallback callback = (fixture, point, normal, fraction) -> {
                    Object userData = fixture.getBody().getUserData();

                    // Skip transparent objects like spray, bomb, or goal
                    if (userData instanceof Spray || userData instanceof Bomb || userData instanceof Goal) {
                        return -1f;  // Continue the ray without stopping
                    }

                    // Store the hit position when encountering an obstacle
                    rayHit.set(point);
                    return fraction;  // Stop ray at the first obstacle or player hit
                };

                // Perform the raycast from the enemy position to the rayEnd point
                physics.getWorld().rayCast(callback, enemyPos, rayEnd);

                // After the raycast, check what is at the end of the ray
                // If the ray ends at the player, detect the player
                if (rayHit.epsilonEquals(playerPos, 1f)) {  // Use epsilonEquals for tolerance
                    playerDetected = true;
                    break;  // Stop as soon as the player is detected
                }
            }
        }

        if (type == Type.CAMERA) {
            handleCamera(delta);
            return;
        }

        if (playerDetected || gameplay.isGlobalChase()) {
//            System.out.println("Player detected: " + playerDetected + ", GlobalChase: " + gameplay.isGlobalChase());
            Vector2 lastSpotted = new Vector2(playerPos);
            player.setLastSeen(lastSpotted);
            wasChasingOrAlert = true;
            state = State.CHASE;
        } else if (alertTimer < alertLength) {
            state = State.ALERT;
        } else {
            state = patrol ? State.PATROL : State.WANDER;
        }

        if (state == State.CHASE) {
            chaseState(delta, enemyPos, playerPos);
        } else if (state == State.ALERT) {
            alertState(delta, enemyPos);
        } else if (state == State.PATROL) {
            patrolState(delta, enemyPos);
        } else {
            wanderState(delta, enemyPos);
        }
        // Ensure the enemy updates its physics forces properly
        enemy.applyForce();
        enemy.update(delta);
    }

    private void chaseState(float delta, Vector2 enemyPos, Vector2 playerPos) {
        alertTimer = 0;
        enemy.setMaxSpeed(chaseMaxSpeed);
        target = playerPos;
        if (!isLineBlocked(enemyPos, target)) {
            moveTowards(target, chaseSpeed);
        } else {
            Vector2 waypoint = getNextPathPoint(enemyPos, target);
            if (waypoint != null) {
                moveTowards(waypoint, chaseSpeed);
            } else {
                state = State.ALERT;
            }
        }
    }
    private void alertState(float delta, Vector2 enemyPos) {
        alertTimer += delta;
        enemy.setMaxSpeed(alertMaxSpeed);
        target = player.getLastSeen();
        if (!isLineBlocked(enemyPos, target)) {
            moveTowards(target, chaseSpeed);
        } else {
            Vector2 waypoint = getNextPathPoint(enemyPos, target);
            if (waypoint != null) {
                moveTowards(waypoint, chaseSpeed);
            } else {
                state = patrol ? State.PATROL : State.WANDER;
            }
        }
    }
    private void patrolState(float delta, Vector2 enemyPos) {
        enemy.setMaxSpeed(wanderMaxSpeed);
        target = graph.getNearestWalkableNode(new Vector2(patrolPath.get(patrolIndex)[0], patrolPath.get(patrolIndex)[1]));
        if (!isLineBlocked(enemyPos, target)) {
            moveTowards(target, wanderSpeed);
        } else {
            Vector2 waypoint = getNextPathPoint(enemyPos, target);
            if (waypoint != null) {
                moveTowards(waypoint, wanderSpeed);
            }
        }
        // Check if the enemy has reached the waypoint
        if (enemyPos.dst2(target) < 0.5f) {
            patrolIndex = (patrolIndex + 1) % patrolPath.size(); // Move to next patrol point
        }
    }
    private void wanderState(float delta, Vector2 enemyPos) {
        wanderTimer += delta;
        enemy.setMaxSpeed(wanderMaxSpeed);
        if (wanderTimer >= timeToChangeTarget) { // || enemyPos.dst(target) < 10f
            wanderTimer = 0f;
            pickNewWanderTarget();
        }
        Vector2 waypoint = getNextPathPoint(enemyPos, target);
        if (waypoint != null) {
            moveTowards(waypoint, wanderSpeed);
        } else {
            enemy.setMovement(0);
            enemy.setVerticalMovement(0);
        }
    }
    private void handleCamera(float delta) {
        float rotationSpeed = getRotationSpeed();
        float minRotation = enemy.getMinRotation();
        float maxRotation = enemy.getMaxRotation();
        float newRotation = (enemy.getRotation() + (float)Math.toRadians(360)) % ((float)Math.toRadians(360)); // Keep rotation within [0,360]
        boolean wrapsAround = minRotation > maxRotation; // Does range cross 0°?

        if (rotatingClockwise) {
            newRotation += rotationSpeed * delta;
            if (wrapsAround) {
                // If we are in the wrapped region but exceed maxRotation
                if (newRotation >= maxRotation && newRotation < minRotation) {
                    newRotation = maxRotation;
                    rotatingClockwise = false;
                }
            } else {
                if (newRotation >= maxRotation) {
                    newRotation = maxRotation;
                    rotatingClockwise = false;
                }
            }
        } else { // Rotating counter-clockwise
            newRotation -= rotationSpeed * delta;

            if (wrapsAround) {
                // If we drop below minRotation but are still in the wrapped region
                if (newRotation <= minRotation && newRotation > maxRotation) {
                    newRotation = minRotation;
                    rotatingClockwise = true;
                }
            } else {
                if (newRotation <= minRotation) {
                    newRotation = minRotation;
                    rotatingClockwise = true;
                }
            }
        }
        newRotation = (newRotation + (float)Math.toRadians(360)) % ((float)Math.toRadians(360)); // Keep rotation within [0,360]

        if (state == State.CHASE) {
            Vector2 enemyPos = enemy.getPosition();
            Vector2 playerPos = player.getPosition();

            if (enemyPos != null && playerPos != null) {
                Vector2 toPlayer = playerPos.cpy().sub(enemyPos);
                float angleToPlayer = (float) Math.atan2(toPlayer.y, toPlayer.x); // In radians
                angleToPlayer = (angleToPlayer + MathUtils.PI2) % MathUtils.PI2;   // Normalize to [0, 2π)

                float clampedAngle;
                if (wrapsAround) {
                    boolean inRange = (angleToPlayer >= minRotation) || (angleToPlayer <= maxRotation);
                    clampedAngle = inRange ? angleToPlayer : closestBound(angleToPlayer, minRotation, maxRotation);
                } else {
                    boolean inRange = angleToPlayer >= minRotation && angleToPlayer <= maxRotation;
                    clampedAngle = inRange ? angleToPlayer : MathUtils.clamp(angleToPlayer, minRotation, maxRotation);
                }

                newRotation = clampedAngle;
            }
        }
        enemy.setRotation(newRotation);

        if (playerDetected) {
            state = State.CHASE;
        } else if (alertTimer < alertLength) {
            state = State.ALERT;
        } else {
            state = State.WANDER;
        }

        if (state == State.CHASE) {
            alertTimer = 0f;
        } else if (state == State.ALERT) {
            alertTimer += delta;
        }
    }

    private float closestBound(float angle, float min, float max) {
        float distToMin = angleDistance(angle, min);
        float distToMax = angleDistance(angle, max);
        return (distToMin < distToMax) ? min : max;
    }
    private float angleDistance(float a, float b) {
        float diff = Math.abs(a - b) % MathUtils.PI2;
        return diff > MathUtils.PI ? MathUtils.PI2 - diff : diff;
    }
    private float getRotationSpeed() {
        switch (state) {
            case CHASE:
                return 0;
            case ALERT:
                return (float)Math.toRadians(rotationSpeedAlert);
            case WANDER:
            default:
                return (float)Math.toRadians(rotationSpeedWander);
        }
    }

    private ShapeRenderer shapeRenderer = new ShapeRenderer();
    private Array<Vector2> lastPath;
    private Vector2 lastGoal;

    public void drawCamera(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(new Color(1, 0, 0, 0.3f)); // Transparent red for vision cone
        shapeRenderer.circle(enemy.getPosition().x * scale, enemy.getPosition().y * scale, 10);
        shapeRenderer.end();
    }
    public void debugRender(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

//        // Draw the goal in green
//        if (lastGoal != null) {
//            shapeRenderer.setColor(Color.GREEN);
//            shapeRenderer.rect(target.x * scale - 5f, target.y * scale - 5f, 20f, 20f);
//        }

        // Highlight the A* path in yellow
//        if (lastPath != null) {
//            System.out.println(enemy.getName() + ": " + lastPath);
//            shapeRenderer.setColor(Color.YELLOW);
//            for (Vector2 pathPoint : lastPath) {
//                shapeRenderer.rect(pathPoint.x * scale - 5f, pathPoint.y * scale - 10f, 10f, 10f);
//            }
//        }

//        // Draw all NavNodes (grid)
//        shapeRenderer.setColor(Color.GRAY);
//        for (NavNode node : graph.nodes) {
//            shapeRenderer.circle(node.position.x * scale, node.position.y * scale, 10f);
//        }

        // Draw enemy FOV and rays
        shapeRenderer.setColor(new Color(1, 0, 0, 0.3f)); // Transparent red for vision cone
        drawEnemyVision();

        shapeRenderer.end();
    }
    private void drawEnemyVision() {
        // UNscale for physics calculations
        Vector2 enemyWorldPos = new Vector2(enemy.getPosition().x, enemy.getPosition().y);
        Vector2 enemyScreenPos = new Vector2(enemyWorldPos.x * scale, enemyWorldPos.y * scale);

        float halfFOV = (float) Math.toRadians(fov / 2);
        int numRays = (int) (fov / 5);
        float angleStep = (halfFOV * 2) / (numRays - 1);

        float angleToLook = enemy.getRotation();

        shapeRenderer.setColor(new Color(1, 0, 0, 0.3f));
        for (int i = 0; i < numRays; i++) {
            float rayAngle = angleToLook - halfFOV + i * angleStep;

            Vector2 direction = new Vector2((float) Math.cos(rayAngle), (float) Math.sin(rayAngle));
            Vector2 rayEndWorld = type == Type.CAMERA
                ? enemyWorldPos.cpy().add(direction.scl(9999)) // Very long in world units
                : enemyWorldPos.cpy().add(direction.scl(detectionRange));

            Vector2 rayHitWorld = rayEndWorld.cpy();  // Will update on hit

            RayCastCallback callback = (fixture, point, normal, fraction) -> {
                Object userData = fixture.getBody().getUserData();

                if (userData instanceof Spray || userData instanceof Bomb || userData instanceof Goal) {
                    return -1f; // Skip transparent
                }

                rayHitWorld.set(point);  // Save where we actually hit
                return fraction;
            };

            physics.getWorld().rayCast(callback, enemyWorldPos, rayEndWorld);

            // SCALE back up to draw on screen
            Vector2 screenHit = new Vector2(rayHitWorld.x * scale, rayHitWorld.y * scale);
            shapeRenderer.line(enemyScreenPos, screenHit);
        }
    }

    public Enemy getEnemy() { return enemy; }
    public State getState() { return state; }
    public void setState(State value) { state = value; }
    public boolean getPlayerDetected() { return playerDetected; }
//    public NavGraph getGraph() { return graph; }
}
