package chroma.controller;

import chroma.model.*;
import chroma.model.Enemy.Type;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ai.pfa.Heuristic;
import com.badlogic.gdx.ai.pfa.indexed.IndexedAStarPathFinder;
import com.badlogic.gdx.ai.pfa.indexed.IndexedGraph;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.RayCastCallback;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.ai.pfa.Connection;
import com.badlogic.gdx.ai.pfa.GraphPath;
import com.badlogic.gdx.ai.pfa.DefaultGraphPath;
import com.badlogic.gdx.utils.ObjectMap;
import edu.cornell.gdiac.graphics.SpriteBatch;
import edu.cornell.gdiac.physics2.ObstacleSprite;

import java.util.ArrayList;
import java.util.List;

import static com.badlogic.gdx.math.Vector2.dst;
import static com.badlogic.gdx.math.Vector2.dst2;

public class AIController {
    public enum State {
        WANDER, PATROL, ALERT, CHASE
    }

    private GameplayController gameplay;
    private PhysicsController physics;
    private Enemy enemy;
    private Type type;
    private Chameleon player;
    private List<Collision> collisions; // list of walls for pathfindinga
    private List<Goal> goals;
    private List<GoalCollision> goalCollisions;
    private State state;

    // Camera and detection
    private float fov;
    private float rotationSpeedPatrol = 45f; // Degrees per second
    private float rotationSpeedAlert = 240f;
    //    private float rotationSpeedChase = 240f;
    private boolean rotatingClockwise = true;
    private float detectionRange;

    // GOAL:
    private Vector2 target;

    // ENEMY SPEED:
    private float wanderSpeed = 1f;
    private float chaseSpeed = 1.5f;

    // CHASE:
    private float chaseMaxSpeed = 9f;
    private float outOfSightTimer = 1f;

    // ALERT:
    private float alertMaxSpeed = 6f;
    private float alertLength = 5f;
    private float alertTimer = alertLength;

    // SUSPICIOUS:
    private float suspiciousMaxSpeed = 1f;
    //    private float awarenessLevel = 0f;  // from 0 to 1
    private float detectionTimer = 0f;
    private float detectionThreshold = 0.5f;

    // WANDER:
    private float wanderMaxSpeed = 3.5f;
    private float timeToChangeTarget = 2f;
    private float wanderTimer = 0f;
    private float margin = 1f; // margin inside room boundaries

    // PATROL:
    private boolean patrol;
    private List<float[]> patrolPath;
    private int patrolIndex = 0; // Track the current waypoint index

    private boolean playerDetected = false;

    private NavGraph graph;
    private PathFinder pathFinder;
    private boolean[][] walkableGrid;
    private NavNode[][] nodeGrid;
    private int gridWidth;
    private int gridHeight;
    private GraphPath<NavNode> nodePath;
    private Array<Vector2> path;
    private Vector2 previousEnd;
    private float pathRecalcTimer = 0;
    private final float PATH_RECALC_INTERVAL = 0.3f;

    private float scale;

    // BLUE RED ANIMATION:
    private boolean blueRedPlayingForward = false;
    private boolean blueRedPlayingBackward = false;
    private float blueRedTime = 0f;
    private float blueRedDuration;

    // Camera Lights
    private Texture lightTexture;

    public AIController(Enemy enemy, GameplayController gameplayController, PhysicsController physicsController, Level level, Texture lightTexture) {
        this.gameplay = gameplayController;
        this.physics = physicsController;
        this.enemy = enemy;
        this.patrol = enemy.getPatrol();
        this.patrolPath = enemy.getPatrolPath();
        this.type = enemy.getType();
        this.player = level.getAvatar();
        this.collisions = level.getCollision();
        this.goals = new ArrayList<>();
        this.goals.addAll(physics.getGoalList());
        this.goalCollisions = new ArrayList<>();
        goalCollisions.add(level.getGoalCollisions());
        goalCollisions.add(level.getGoal2Collisions());
        goalCollisions.add(level.getGoal3Collisions());
        goals.addAll(physics.getGoal2List());
        goals.addAll(physics.getGoal3List());
        this.fov = enemy.getFov();
        state = patrol ? State.PATROL : State.WANDER;
        pickNewWanderTarget();

        float worldWidth = gameplay.getWorldWidth();
        float worldHeight = gameplay.getWorldHeight();

        // Scaling for nodes
//        OrthographicCamera camera = gameplay.getCamera();
//        float screenHeight = camera.viewportHeight;
//        this.scale = screenHeight / worldHeight;
        this.scale = gameplay.getUnits();

        blueRedDuration = enemy.getBlueRedAnimation().getAnimationDuration();

        this.lightTexture = lightTexture;

        // Build navigation graph
        graph = new NavGraph();
        buildGraph(worldWidth, worldHeight);
        pathFinder = new PathFinder(graph);  // Now create the pathfinder
        nodePath = new DefaultGraphPath<>();
        path = new Array<>();
        previousEnd = new Vector2();
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

    private void buildGraph(float worldWidth, float worldHeight) {
        gridWidth = (int) worldWidth + 1;
        gridHeight = (int) worldHeight + 1;

        walkableGrid = new boolean[gridWidth][gridHeight];
        nodeGrid = new NavNode[gridWidth][gridHeight];

        // First pass: determine walkability and create nodes
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                Vector2 pos = new Vector2(x, y);
                if (!isBlocked(pos)) {
                    walkableGrid[x][y] = true;
                    NavNode node = new NavNode(x, y);
                    nodeGrid[x][y] = node;
                    graph.addNode(node);
                }
            }
        }

        // Second pass: connect adjacent nodes
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                NavNode node = nodeGrid[x][y];
                if (node != null) {
                    // Connect 8 directions (optional: limit to 4 if preferred)
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = x + dx;
                            int ny = y + dy;

                            if (inBounds(nx, ny)) {
                                NavNode neighbor = nodeGrid[nx][ny];
                                if (neighbor != null && !isLineBlocked(node.position, neighbor.position)) {
                                    node.addConnection(new NavConnection(node, neighbor));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < gridWidth && y < gridHeight;
    }

    public boolean isWalkable(int x, int y) {
        return inBounds(x, y) && walkableGrid[x][y];
    }

    public NavNode getNodeAt(int x, int y) {
        return inBounds(x, y) ? nodeGrid[x][y] : null;
    }

    // Checks if a straight line from start to end is blocked by any wall.
    private boolean isLineBlocked(Vector2 start, Vector2 end) {
        if (start.dst(end) > 0.1) {
            Fixture hitFixture = physics.raycast(start, end);

            // If nothing is hit, return false (line is not blocked)
            if (hitFixture == null) {
                return false;
            }

            Object hitObject = hitFixture.getBody().getUserData();

            // Ignore enemies during visibility/path checks
            return !(hitObject instanceof Enemy || hitObject instanceof Chameleon);
        } else {
            return false;
        }
    }
    private boolean isBlocked(Vector2 position) {
        for (Collision wall : collisions) {
            if (wall.contains(position)) {
                return true;
            }
        }


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
        private final Array<NavNode> nodes = new Array<>();
        private final ObjectMap<GridPoint2, Array<NavNode>> spatialMap = new ObjectMap<>();
        private final float cellSize = scale; // or whatever spacing fits your level

        @Override
        public int getIndex(NavNode node) {
            return nodes.indexOf(node, true);
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
            GridPoint2 cell = new GridPoint2((int)(node.position.x), (int)(node.position.y));

            Array<NavNode> cellNodes = spatialMap.get(cell);
            if (cellNodes == null) {
                cellNodes = new Array<>();
                spatialMap.put(cell, cellNodes);
            }
            cellNodes.add(node);
        }
//
//        private GridPoint2 toCell(Vector2 pos) {
//            return new GridPoint2((int)(pos.x / cellSize), (int)(pos.y / cellSize));
//        }

        public NavNode getNearestWalkableNode(Vector2 point) {
            int baseX = (int) point.x;
            int baseY = (int) point.y;
            GridPoint2 neighbor = new GridPoint2();

            NavNode closest = null;
            float minDist = Float.MAX_VALUE;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    neighbor.set(baseX + dx, baseY + dy);
                    Array<NavNode> candidates = spatialMap.get(neighbor);
                    if (candidates == null) continue;

                    for (NavNode node : candidates) {
                        float dist = node.position.dst2(point);
                        if (dist < minDist) {
                            minDist = dist;
                            closest = node;
                            if (dist == 0) return node; // Early exit
                        }
                    }
                }
            }

            return closest != null ? closest : new NavNode(point.x, point.y);
        }
    }

    public class EuclideanHeuristic implements Heuristic<NavNode> {
        @Override
        public float estimate(NavNode node, NavNode endNode) {
            return node.position.dst2(endNode.position);
        }
    }

    public class PathFinder {
        private final NavGraph graph;
        private final IndexedAStarPathFinder<NavNode> pathFinder;
        private final EuclideanHeuristic heuristic;
        private NavNode lastStartNode;
        private NavNode lastEndNode;

        public PathFinder(NavGraph graph) {
            this.graph = graph;
            this.pathFinder = new IndexedAStarPathFinder<>(graph);
            this.heuristic = new EuclideanHeuristic();
            lastStartNode = null;
            lastEndNode = null;
        }

        public Array<Vector2> findPath(Vector2 start, Vector2 end) {
            lastStartNode = graph.getNearestWalkableNode(start);
            lastEndNode = graph.getNearestWalkableNode(end);

            if (lastStartNode == null || lastEndNode == null) {
                return path;
            }

            nodePath.clear();

            boolean success = pathFinder.searchNodePath(lastStartNode, lastEndNode, heuristic, nodePath);

            path.clear();

            if (success) {
                for (NavNode node : nodePath) {
                    path.add(node.position);
                }
            }
            return path;
        }
    }

    private Vector2 getNextPathPoint(Vector2 start, Vector2 end) {
        if (previousEnd == null || !previousEnd.equals(end)) {
            path = pathFinder.findPath(start, end);
        }

        if (path.size > 1) {
            lastPath = path; // Store for debugging
            lastGoal = end;

//            lastVisible = getFarthestVisiblePoint(start, path);

            return path.get(1);
        }
        return null;
    }

//    private Vector2 getFarthestVisiblePoint(Vector2 start, Array<Vector2> path) {
//        Vector2 lastVisible = start;
//        float bodyRadius = enemy.getHeight() / 2;
//
//        Vector2 currentStart = start.cpy();
//        for (Vector2 point : path) {
//            if (isPathClearForBody(currentStart, point, bodyRadius)) {
//                lastVisible = point;
//                currentStart = point.cpy();
//            } else {
//                break;  // First blocked point — stop here.
//            }
//        }
//        return lastVisible.equals(start) ? null : lastVisible;
//    }

//    private boolean isPathClearForBody(Vector2 start, Vector2 end, float radius) {
//        Vector2 direction = new Vector2(end).sub(start).nor();
//        Vector2 perpendicular = new Vector2(-direction.y, direction.x);
//        Vector2 offset = perpendicular.scl(radius * scale);
//
//        // Perform 3 raycasts: center, left offset, right offset
//        boolean centerClear = !isLineBlocked(start, end);
//        boolean leftClear = !isLineBlocked(start.cpy().add(offset), end.cpy().add(offset));
//        boolean rightClear = !isLineBlocked(start.cpy().sub(offset), end.cpy().sub(offset));
//
//        boolean pathClear = centerClear && leftClear && rightClear;
//
////        System.out.println(pathClear + ": " + start + " to " + end);
//
//        return pathClear;
//    }

    private void moveTowards(Vector2 target, float speed) {
        Vector2 enemyPos = enemy.getPosition();
        Vector2 direction = new Vector2(target).sub(enemyPos);

        if (direction.len() > 0.1f) { // Prevent division by zero
            direction.nor();
        } else {
            // Already close enough or no direction to move
            direction.setZero();
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
//        enemy.setScale(scale);
        Vector2 enemyPos = enemy.getPosition();
        Vector2 playerPos = player.getPosition();
        if (enemyPos == null || playerPos == null) {
            return;
        }
        if (detectionTimer > 0) {
            detectionRange = enemy.getAlertDetectionRange();
        } else {
            detectionRange = enemy.getBaseDetectionRange();
        }

        playerDetected = false;
//        boolean isCamera = type == Type.CAMERA1 || type == Type.CAMERA2;
        float distanceToPlayer = enemyPos.dst(playerPos);
        boolean enemyInRange = distanceToPlayer <= detectionRange;

        if (!player.isHidden() && enemyInRange) {
            float angleLooking = enemy.getRotation();
            float halfFOV = (float) Math.toRadians(fov / 2);
            int numRays = 10;
            float angleStep = (halfFOV * 2) / (numRays - 1);

            for (int i = 0; i < numRays; i++) {
                float rayAngle = angleLooking - halfFOV + (i * angleStep);

                Vector2 direction = new Vector2((float) Math.cos(rayAngle), (float) Math.sin(rayAngle));
                Vector2 rayEnd = enemyPos.cpy().add(direction.scl(detectionRange));

                final Vector2 rayHit = rayEnd.cpy(); // Initialize the ray hit position

                RayCastCallback callback = (fixture, point, normal, fraction) -> {
                    Object userData = fixture.getBody().getUserData();

                    // Skip transparent objects like spray, bomb, or goal
                    if (userData instanceof Grate || userData instanceof Spray || userData instanceof Bomb || userData instanceof Door) {
                        //enemy.getType() is Camera2 return -1 for Collision
                        return -1f;  // Continue the ray without stopping
                    }
                    if (type == Type.CAMERA2 && (userData instanceof Collision || dst2(point.x, point.y, enemyPos.x, enemyPos.y) < 0.5f)) {
                        return -1f; // Skip walls for front-facing camera and avoid reading through walls
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

        // Camera updates separately
        if (type == Type.CAMERA1 || type == Type.CAMERA2) {
            handleCamera(delta);
            return;
        }

        // Detection logic first: always grows/shrinks no matter the state
        if (playerDetected) {
            detectionTimer = Math.min(detectionThreshold + 0.5f, detectionTimer + delta);
            outOfSightTimer = 0f;
        } else if (alertTimer >= alertLength) {
            detectionTimer = Math.max(0, detectionTimer - delta);
        }
        if (type == Type.GUARD) {
//            System.out.println("State: " + state + ", alert: " + alertTimer + ", detection: " + detectionTimer);
        }

        // State switching logic after detection is updated
        if (state == State.CHASE) {
            Vector2 lastSpotted = new Vector2(playerPos);
            player.setLastSeen(lastSpotted);
            chaseState(delta, enemyPos, playerPos);
            if (!playerDetected) {
                outOfSightTimer += delta;
                if (outOfSightTimer > 1.5f) {
                    state = State.ALERT;
                    alertTimer = 0;
                    detectionTimer = detectionThreshold;  // lock it at max during ALERT
                }
            }
        } else if (state == State.ALERT) {
            alertState(delta, enemyPos);
            if (alertTimer >= alertLength) {
//                System.out.println(enemy.getName() + " swap to patrol correct");
                state = patrol ? State.PATROL : State.WANDER;
//                blueRedPlayingBackward = true;
//                blueRedPlayingForward = false;
                blueRedTime = blueRedDuration;
            }
            if (playerDetected && detectionTimer > detectionThreshold) {
                state = State.CHASE;
                enemy.getSpottedSound().play();
            }
//            System.out.println(enemy.getName() + " state now: " + state);
        } else if (state == State.PATROL) {
            patrolState(delta, enemyPos);
            if (detectionTimer > detectionThreshold) {
                state = State.CHASE;
//                blueRedPlayingForward = true;
//                blueRedPlayingBackward = false;
                blueRedTime = 0f;
                enemy.getSpottedSound().play();
            } else if (gameplay.isGlobalChase()) {
                state = State.ALERT;
                alertTimer = 0;
                detectionTimer = detectionThreshold;
            }
        } else {
            wanderState(delta, enemyPos);
            if (detectionTimer > detectionThreshold) {
                state = State.CHASE;
//                blueRedPlayingForward = true;
//                blueRedPlayingBackward = false;
                blueRedTime = 0f;
                enemy.getSpottedSound().play();
            } else if (gameplay.isGlobalChase()) {
                state = State.ALERT;
                alertTimer = 0f;
                detectionTimer = detectionThreshold;
            }
        }
        // Ensure the enemy updates its physics forces properly
        enemy.applyForce();
        enemy.update(delta);

        // Animation
        if (blueRedPlayingForward) {
            blueRedTime += delta;
            if (blueRedTime >= blueRedDuration) {
                blueRedTime = blueRedDuration;
                blueRedPlayingForward = false;
            }
        } else if (blueRedPlayingBackward) {
            blueRedTime -= delta;
            if (blueRedTime <= 0f) {
                blueRedTime = 0f;
                blueRedPlayingBackward = false;
            }
        }
        enemy.setBlueRedTime(blueRedTime);
    }

    private Vector2 waypoint;

    private void chaseState(float delta, Vector2 enemyPos, Vector2 playerPos) {
        alertTimer = 0;
        enemy.setBlue(false);
        enemy.setAlertAnimationFrame(12);
        enemy.setMaxSpeed(chaseMaxSpeed);
        target = playerPos;
        pathRecalcTimer += delta;
        if (pathRecalcTimer >= PATH_RECALC_INTERVAL) {
            pathRecalcTimer = 0;
            waypoint = getNextPathPoint(enemyPos, target);
        }
        if (waypoint != null) {
            moveTowards(waypoint, chaseSpeed);
        } else {
            state = State.ALERT;
        }
    }
    private void alertState(float delta, Vector2 enemyPos) {
        alertTimer += delta;
        enemy.setBlue(false);
        enemy.setAlertAnimationFrame(11);
        enemy.setMaxSpeed(alertMaxSpeed);
        target = player.getLastSeen();
        pathRecalcTimer += delta;
        if (pathRecalcTimer >= PATH_RECALC_INTERVAL) {
            pathRecalcTimer = 0;
            waypoint = getNextPathPoint(enemyPos, target);
//            if (waypoint == null) {
//                System.out.println("waypoint null!");
//            }
        }
        if (waypoint != null) {
            moveTowards(waypoint, chaseSpeed);
        }
    }

    private float lastPatrolX = 0;
    private float lastPatrolY = 0;
    NavNode targetNode;

    private void patrolState(float delta, Vector2 enemyPos) {
        int frame = MathUtils.clamp(
            (int)((detectionTimer / detectionThreshold) * 11f), 0, 11
        );
        if (detectionTimer == 0) {
            frame = -1;
        }
        enemy.setBlue(true);
        enemy.setAlertAnimationFrame(frame);
        enemy.setMaxSpeed(wanderMaxSpeed);
        if (detectionTimer > 0) {
            enemy.setMaxSpeed(suspiciousMaxSpeed);
        }
        // Get patrol target
        float patrolX = patrolPath.get(patrolIndex)[0];
        float patrolY = patrolPath.get(patrolIndex)[1];
        if (patrolX != lastPatrolX || patrolY != lastPatrolY) {
            lastPatrolX = patrolX;
            lastPatrolY = patrolY;
            targetNode = graph.getNearestWalkableNode(new Vector2(patrolX, patrolY));
        }

//        if (targetNode == null) {
//            enemy.setMovement(0);
//            enemy.setVerticalMovement(0);
//            return;
//        }
        target = targetNode.position;
        pathRecalcTimer += delta;
        if (pathRecalcTimer >= PATH_RECALC_INTERVAL) {
            pathRecalcTimer = 0;
            waypoint = getNextPathPoint(enemyPos, target);
        }
        if (waypoint != null) {
            moveTowards(waypoint, wanderSpeed);
        } else {
            // Optionally stop or wait if no path found
            enemy.setMovement(0);
            enemy.setVerticalMovement(0);
        }
        // Check if the enemy has reached the waypoint
        if (enemyPos.dst2(target) < 0.5f) {
            patrolIndex = (patrolIndex + 1) % patrolPath.size(); // Move to next patrol point
        }
    }
    private void wanderState(float delta, Vector2 enemyPos) {
        wanderTimer += delta;
        int frame = MathUtils.clamp(
            (int)((detectionTimer / detectionThreshold) * 11f), 0, 11
        );
        if (detectionTimer == 0) {
            frame = -1;
        }
        enemy.setBlue(true);
        enemy.setAlertAnimationFrame(frame);
        enemy.setMaxSpeed(wanderMaxSpeed);
        if (detectionTimer > 0) {
            enemy.setMaxSpeed(suspiciousMaxSpeed);
        }
        if (wanderTimer >= timeToChangeTarget) { // || enemyPos.dst(target) < 10f
            wanderTimer = 0f;
            pickNewWanderTarget();
        }
        pathRecalcTimer += delta;
        if (pathRecalcTimer >= PATH_RECALC_INTERVAL) {
            pathRecalcTimer = 0;
            waypoint = getNextPathPoint(enemyPos, target);
        }
        if (waypoint != null) {
            moveTowards(waypoint, wanderSpeed);
        } else {
            enemy.setMovement(0);
            enemy.setVerticalMovement(0);
        }
    }

    private void handleCamera(float delta) {
        float rotationSpeed = getRotationSpeed(); // Radians per second
        float minRotation = normalizeRadians(enemy.getMinRotation());
        float maxRotation = normalizeRadians(enemy.getMaxRotation());
        float newRotation = normalizeRadians(enemy.getRotation());

        boolean wrapsAround = minRotation > maxRotation;

        if (state == State.CHASE) {
            enemy.setAlertAnimationFrame(12);

            Vector2 enemyPos = enemy.getPosition();
            Vector2 playerPos = player.getPosition();
            if (enemyPos != null && playerPos != null) {
                Vector2 toPlayer = playerPos.cpy().sub(enemyPos);
                float angleToPlayer = (float) Math.atan2(toPlayer.y, toPlayer.x);
                angleToPlayer = normalizeRadians(angleToPlayer);

                boolean inRange = isWithinBounds(angleToPlayer, minRotation, maxRotation);
                float clampedAngle = inRange ? angleToPlayer : closestBound(angleToPlayer, minRotation, maxRotation);
                newRotation = clampedAngle;
            }
        } else {
            // PATROL MODE — rotate back and forth between min and max
            if (rotatingClockwise) {
                newRotation += rotationSpeed * delta;
                newRotation = normalizeRadians(newRotation);

                if (!isWithinBounds(newRotation, minRotation, maxRotation)) {
                    newRotation = maxRotation;
                    rotatingClockwise = false;
                }
            } else {
                newRotation -= rotationSpeed * delta;
                newRotation = normalizeRadians(newRotation);

                if (!isWithinBounds(newRotation, minRotation, maxRotation)) {
                    newRotation = minRotation;
                    rotatingClockwise = true;
                }
            }
        }

        // Detection bar logic (same as before)
        if (playerDetected) {
            detectionTimer = Math.min(detectionThreshold / 2, detectionTimer + delta);
        } else if (alertTimer >= alertLength) {
            detectionTimer = Math.max(0, detectionTimer - delta);
        }

        // ALERT/CHASE/PATROL state transitions
        if (state == State.CHASE) {
            Vector2 lastSpotted = new Vector2(player.getPosition());
            player.setLastSeen(lastSpotted);
            if (!playerDetected) {
                state = State.ALERT;
                alertTimer = 0f;
            }
        } else if (state == State.ALERT) {
            alertTimer += delta;
            if (gameplay.isGlobalChase()) alertTimer = 0f;

            if (detectionTimer >= detectionThreshold / 2 && playerDetected) {
                state = State.CHASE;
            } else if (detectionTimer == 0) {
                state = State.PATROL;
            }
            enemy.setAlertAnimationFrame(11);
        } else { // PATROL
            if (gameplay.isGlobalChase()) {
                state = State.ALERT;
                alertTimer = 0f;
            } else if (detectionTimer >= detectionThreshold / 2 && playerDetected) {
                state = State.CHASE;
            }
            int frame = MathUtils.clamp((int) ((detectionTimer / (detectionThreshold / 2)) * 11f), 0, 11);
            if (detectionTimer == 0) frame = -1;
            enemy.setAlertAnimationFrame(frame);
        }

        enemy.setRotation(newRotation);
    }
    private float normalizeRadians(float radians) { return (radians % MathUtils.PI2 + MathUtils.PI2) % MathUtils.PI2; }
    private float angleDistance(float a, float b) {
        float diff = Math.abs(a - b) % MathUtils.PI2;
        return diff > MathUtils.PI ? MathUtils.PI2 - diff : diff;
    }
    private float closestBound(float angle, float min, float max) {
        float distToMin = angleDistance(angle, min);
        float distToMax = angleDistance(angle, max);
        return (distToMin < distToMax) ? min : max;
    }
    private boolean isWithinBounds(float angle, float min, float max) {
        if (min <= max) {
            return angle >= min && angle <= max;
        } else {
            return angle >= min || angle <= max;
        }
    }
    private float getRotationSpeed() {
        switch (state) {
            case CHASE:
                return 0;
            case ALERT:
                return (float)Math.toRadians(rotationSpeedAlert);
            case PATROL:
            default:
                return (float)Math.toRadians(rotationSpeedPatrol);
        }
    }

    private ShapeRenderer shapeRenderer = new ShapeRenderer();
    private Array<Vector2> lastPath;
    private Vector2 lastVisible;
    private Vector2 lastGoal;

    public void debugRender(OrthographicCamera camera, SpriteBatch batch) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

//        // Draw the goal in green
//        if (lastGoal != null) {
//            shapeRenderer.setColor(Color.GREEN);
//            shapeRenderer.rect(target.x * scale - 5f, target.y * scale - 5f, 20f, 20f);
//        }
//
//        // 1. Draw all NavNodes first (background layer)
//        shapeRenderer.setColor(Color.GRAY);
//        for (NavNode node : graph.nodes) {
//            shapeRenderer.circle(node.position.x * scale, node.position.y * scale, 10f);
//        }

        // 2. Draw the A* path in yellow
//        if (lastPath != null) {
//            shapeRenderer.setColor(Color.YELLOW);
//            for (Vector2 pathPoint : lastPath) {
//                shapeRenderer.rect(pathPoint.x * scale - 5f, pathPoint.y * scale - 10f, 10f, 10f);
//            }
//        }
//
//        // 3. Draw the last visible target point on top
//        if (lastVisible != null) {
//            shapeRenderer.setColor(Color.BLUE);
//            shapeRenderer.rect(lastVisible.x * scale - 5f, lastVisible.y * scale - 10f, 10f, 10f);
//        }
        shapeRenderer.end();

        // 2. Line shapes block
//        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

//        if (enemy.getType() == Type.GUARD) {
//            Vector2 nextPoint = getNextPathPoint(enemy.getPosition(), target);
//            if (nextPoint != null) {
//                float radius = enemy.getHeight() / 2;
//                drawPathVisibilityDebug(new Vector2(enemy.getPosition().x * scale, enemy.getPosition().y * scale), new Vector2(nextPoint.x * scale, nextPoint.y * scale), radius);
//            }
//        }
//        shapeRenderer.end();

//        if (lastPath != null) drawPath(lastPath);

        drawEnemyVision(camera, batch);
    }

    public void drawPathVisibilityDebug(Vector2 start, Vector2 end, float radius) {
        Vector2 direction = new Vector2(end).sub(start).nor();
        Vector2 perpendicular = new Vector2(-direction.y, direction.x);
        Vector2 offset = new Vector2(perpendicular).scl(radius*scale);

        Vector2 leftStart = start.cpy().add(offset);
        Vector2 rightStart = start.cpy().sub(offset);
        Vector2 leftEnd = end.cpy().add(offset);
        Vector2 rightEnd = end.cpy().sub(offset);

        // Center ray (white)
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.line(start, end);

        // Left offset ray (red)
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.line(leftStart, leftEnd);

        // Right offset ray (blue)
        shapeRenderer.setColor(Color.BLUE);
        shapeRenderer.line(rightStart, rightEnd);
    }
    public void drawEnemyVision(OrthographicCamera camera, SpriteBatch batch) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        Vector2 enemyWorldPos = new Vector2(enemy.getPosition().x, enemy.getPosition().y);
        Vector2 enemyScreenPos = new Vector2(enemyWorldPos.x * scale, enemyWorldPos.y * scale);

        float halfFOV = (float) Math.toRadians(fov / 2);
        int numRays = (int) 20f;  // high = smoother
        float angleStep = (halfFOV * 2) / (numRays - 1);

        float angleToLook = enemy.getRotation();

        shapeRenderer.setColor(new Color(1, 0, 0, 0.2f));

        Array<Vector2> hitPoints = new Array<>();

        for (int i = 0; i < numRays; i++) {
            float rayAngle = angleToLook - halfFOV + i * angleStep;

            Vector2 direction = new Vector2((float) Math.cos(rayAngle), (float) Math.sin(rayAngle));
            Vector2 rayEndWorld = enemyWorldPos.cpy().add(direction.scl(detectionRange));

            Vector2 rayHitWorld = rayEndWorld.cpy();

            RayCastCallback callback = (fixture, point, normal, fraction) -> {
                Object userData = fixture.getBody().getUserData();

                if (userData instanceof Grate || userData instanceof Spray || userData instanceof Bomb || userData instanceof Goal || userData instanceof Chameleon || userData instanceof Enemy || userData instanceof Laser) {
                    return -1f; // Skip transparent
                }
                if (type == Type.CAMERA2 && (userData instanceof Collision || dst2(point.x, point.y, enemyWorldPos.x, enemyWorldPos.y) < 0.5f)) {
                    return -1f; // Skip walls for front-facing camera and avoid reading through walls
                }

                rayHitWorld.set(point);
                return fraction;
            };
            if(enemyWorldPos.dst(rayEndWorld) != 0){
                physics.getWorld().rayCast(callback, enemyWorldPos, rayEndWorld);
            }

            // SCALE back up for screen drawing
            Vector2 screenHit = new Vector2(rayHitWorld.x * scale, rayHitWorld.y * scale);
            hitPoints.add(screenHit);
        }

        // Now draw filled triangles between each hit point
        for (int i = 0; i < hitPoints.size - 1; i++) {
            Vector2 p1 = hitPoints.get(i);
            Vector2 p2 = hitPoints.get(i + 1);

            if (p1.dst(p2) < detectionRange * scale * 1.1f) {  // Only draw if neighboring rays are "connected"
                shapeRenderer.triangle(
                        enemyScreenPos.x, enemyScreenPos.y,
                        p1.x, p1.y,
                        p2.x, p2.y
                );
            }
        }

        // After all hitPoints are calculated and triangles drawn
        shapeRenderer.end();  // End ShapeRenderer before using SpriteBatch
        Gdx.gl.glDisable(GL20.GL_BLEND); // You may keep blending enabled if the texture is transparent

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Draw the texture centered at triangle midpoints
        for (int i = 0; i < hitPoints.size - 1; i++) {
            Vector2 p1 = hitPoints.get(i);
            Vector2 p2 = hitPoints.get(i + 1);

            if (p1.dst(p2) < detectionRange * scale * 1.1f) {
                // Midpoint between p1 and p2 and the enemy origin
                float midX = (enemyScreenPos.x + p1.x + p2.x) / 3f;
                float midY = (enemyScreenPos.y + p1.y + p2.y) / 3f;

                float drawSize = 64f; // adjust based on how large you want the light to appear

                batch.setColor(1f, 1f, 1f, 0.1f); // Optional: apply alpha to texture
                batch.draw(
                    lightTexture,
                    midX - drawSize / 2f,
                    midY - drawSize / 2f,
                    drawSize,
                    drawSize
                );
            }
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    public Enemy getEnemy() { return enemy; }
    public State getState() { return state; }
    public void setState(State value) { state = value; }
    public boolean getPlayerDetected() { return playerDetected; }
//    public NavGraph getGraph() { return graph; }
}
