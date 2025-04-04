package chroma.controller;

import chroma.model.Chameleon;
import chroma.model.Enemy;
import chroma.model.Level;
import chroma.model.Terrain;
import com.badlogic.gdx.ai.pfa.Heuristic;
import com.badlogic.gdx.ai.pfa.indexed.IndexedAStarPathFinder;
import com.badlogic.gdx.ai.pfa.indexed.IndexedGraph;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.ai.pfa.Connection;
import com.badlogic.gdx.ai.pfa.GraphPath;
import com.badlogic.gdx.ai.pfa.DefaultGraphPath;


import java.util.List;

public class AIController {
    public enum State {
        WANDER, PATROL, ALERT, CHASE
    }

    private GameplayController gameplay;
    private PhysicsController physics;
    private Level level;
    private Enemy enemy;
    private Chameleon player;
    private List<Terrain> walls; // list of walls for pathfindinga
    private List<Terrain> platforms;
    private State state;
//    private float detectionRange = 200f; // enemy detection range

    // Base detection range and dynamic detection range.
    private float detectionRange;
    private float fov;
//    private float detectionIncrease = 1f; // increase amount after losing player
//    private float maxDetectionRange = 8f;

    private float wanderSpeed = 2f;
    private float chaseSpeed = 4f;

    // For Wander behavior:
    private Vector2 target;
    private float timeToChangeTarget = 2f;
    private float wanderTimer = 0f;
    private float margin = 1f; // margin inside room boundaries

    private boolean playerDetected = false;

    private NavGraph graph = new NavGraph();

    private float scale;

    public AIController(Enemy enemy, GameplayController gameplayController, PhysicsController physicsController, Level level) {
        this.enemy = enemy;
        this.gameplay = gameplayController;
        this.physics = physicsController;
        this.level = level;
        this.player = level.getAvatar();
        this.walls = level.getWalls();
        this.platforms = level.getPlatforms();
        this.detectionRange = enemy.getDetectionRange();
        this.fov = enemy.getFov();
        state = State.WANDER;
        pickNewWanderTarget();

        float worldWidth = gameplay.getWorldWidth();
        float worldHeight = gameplay.getWorldHeight();

        // Scaling for nodes
        OrthographicCamera camera = gameplay.getCamera();
        float screenHeight = camera.viewportHeight;
        this.scale = screenHeight / worldHeight;

        // Build navigation graph
        buildGraph(worldWidth, worldHeight);
    }

    public Enemy getEnemy() {
        return enemy;
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
        return physics.raycast(start, end) != null;
    }

    private boolean isBlocked(Vector2 position) {
        for (Terrain wall : walls) {
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
                if (!node.equals(other) && node.position.dst2(other.position) == 1f) {
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
        Vector2 enemyPos = enemy.getObstacle().getPosition();
        Vector2 direction = new Vector2(target).sub(enemyPos).nor();

        // Set movement variables like the player
        float hmove = direction.x;
        float vmove = direction.y;

        // Apply movement similar to the player
        enemy.setMovement(hmove * enemy.getForce());
        enemy.setVerticalMovement(vmove * enemy.getForce());
    }

    // Updated update method: 'playerVisible' is true if the player is visible.
    public void update(float delta) {
        Vector2 enemyPos = enemy.getObstacle().getPosition();
        Vector2 playerPos = player.getPosition();
        if (enemyPos == null || playerPos == null) {
            return;
        }
        playerDetected = false;
        if (!player.isHidden()) {
            float distanceToPlayer = enemyPos.dst(playerPos);
            if (distanceToPlayer <= detectionRange) {
                float angleToPlayer = playerPos.cpy().sub(enemyPos).angleRad();
                float halfFOV = (float) Math.toRadians(fov/2);

                // Cast multiple rays in the FOV
                int numRays = 10;
                float angleStep = (halfFOV * 2) / (numRays - 1);
                for (int i = 0; i < numRays; i++) {
                    float rayAngle = angleToPlayer - halfFOV + (i * angleStep);
                    Vector2 direction = new Vector2((float) Math.cos(rayAngle), (float) Math.sin(rayAngle));
                    Vector2 endPoint = enemyPos.cpy().add(direction.scl(detectionRange));

                    Fixture hit = physics.raycast(enemyPos, endPoint);
                    if (hit != null && hit.getBody().getUserData() == player) {
                        playerDetected = true;
                        break;
                    }
                }
            }
        }

        if (playerDetected) {
            state = State.CHASE;
        } else if (state == State.CHASE) {
            state = State.WANDER;
        }

        if (state == State.CHASE) {
            if (!isLineBlocked(enemyPos, playerPos)) {
                moveTowards(playerPos, chaseSpeed);
            } else {
                Vector2 waypoint = getNextPathPoint(enemyPos, playerPos);
                if (waypoint != null) {
                    moveTowards(waypoint, chaseSpeed);
                } else {
                    state = State.WANDER;
                }
            }
        } else {
            wanderTimer += delta;
            if (wanderTimer >= timeToChangeTarget || enemyPos.dst(target) < 10f) {
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
        // Ensure the enemy updates its physics forces properly
        enemy.applyForce();
        enemy.update(delta);
    }

    private ShapeRenderer shapeRenderer = new ShapeRenderer();

    private Array<Vector2> lastPath = null;
    private Vector2 lastGoal = null;

    public void debugRender(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Uncomment to see ai debugging for pathfinding

//        // Draw all NavNodes (grid)
//        shapeRenderer.setColor(Color.GRAY);
//        for (NavNode node : graph.nodes) {
//            shapeRenderer.circle(node.position.x * scale, node.position.y * scale, 10f);
//        }
//
//        // Highlight the A* path in yellow
//        if (lastPath != null) {
//            shapeRenderer.setColor(Color.YELLOW);
//            for (Vector2 pathPoint : lastPath) {
//                shapeRenderer.rect(pathPoint.x * scale - 5f, pathPoint.y * scale - 5f, 10f, 10f);
//            }
//        }
//
//        // Draw the goal in green
//        if (lastGoal != null) {
//            shapeRenderer.setColor(Color.GREEN);
//            shapeRenderer.rect(lastGoal.x * scale - 10f, lastGoal.y * scale - 10f, 20f, 20f);
//        }

        // Draw enemy FOV and rays
        shapeRenderer.setColor(new Color(1, 0, 0, 0.3f)); // Transparent red for vision cone
        drawEnemyVision();

        shapeRenderer.end();
    }

    private void drawEnemyVision() {
        Vector2 enemyPos = new Vector2(enemy.getObstacle().getPosition().x*scale, enemy.getObstacle().getPosition().y*scale);
        float halfFOV = (float) Math.toRadians(fov/2);
        int numRays = 30;
        float angleStep = (halfFOV * 2) / (numRays - 1);
        float visionRange = detectionRange * scale;

        float angleToLook = enemy.getFacingAngle();

        // Draw enemy vision cone
        shapeRenderer.triangle(enemyPos.x, enemyPos.y,
            enemyPos.x + visionRange * (float) Math.cos(angleToLook - halfFOV),
            enemyPos.y + visionRange * (float) Math.sin(angleToLook - halfFOV),
            enemyPos.x + visionRange * (float) Math.cos(angleToLook + halfFOV),
            enemyPos.y + visionRange * (float) Math.sin(angleToLook + halfFOV));

        // Draw enemy casted rays
        shapeRenderer.setColor(Color.RED);
        for (int i = 0; i < numRays; i++) {
            float rayAngle = angleToLook - halfFOV + (i * angleStep);
            Vector2 direction = new Vector2((float) Math.cos(rayAngle), (float) Math.sin(rayAngle));
            Vector2 endPoint = enemyPos.cpy().add(direction.scl(visionRange));

            Fixture hit = physics.raycast(enemyPos, endPoint);
            if (hit != null) {
                endPoint = new Vector2(hit.getBody().getPosition().x, hit.getBody().getPosition().y); // Stop at the first hit
            }

            shapeRenderer.line(enemyPos, endPoint);
        }
    }
}
