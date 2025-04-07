package chroma.model;

import chroma.controller.LevelSelector;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.AssetDirectory;
import java.util.ArrayList;
import java.util.List;

/**
 * Level ----- This class is responsible for constructing the game environment from JSON
 * configuration. It instantiates key game objects CURRENTLY including: - The goal door. - The
 * player (Chameleon). - Walls and platforms.
 * <p>
 * Level encapsulates the object creation logic so that higher-level controllers need not manage the
 * details of each object's construction. Getter methods provide access to these objects.
 */
public class Level {

    private Door goalDoor;
    private Chameleon avatar;
    private List<Enemy> enemies;
    private List<Terrain> walls;
    private List<Terrain> platforms;
    private List<Bomb> bombs;
    private List<Spray> sprays;
    private List<WallDepth> depths;

    /**
     * Constructs a new Level by loading the JSON configuration through the provided LevelSelector.
     *
     * @param directory the AssetDirectory used for loading textures and JSON files.
     * @param units the physics units conversion factor.
     * @param selector the LevelSelector that chooses the JSON configuration for this level.
     */
    public Level(AssetDirectory directory, float units, LevelSelector selector) {
        // Load the level JSON configuration via the LevelSelector.
        JsonValue constants = selector.loadCurrentLevel();
        // Create the goal door
        Texture goalTex = directory.getEntry("shared-goal", Texture.class);
        JsonValue goalData = constants.get("goal");
        goalDoor = new Door(units, goalData);
        goalDoor.setTexture(goalTex);
        goalDoor.getObstacle().setName("goal");

        // Create the chameleon (player) using animation
        JsonValue chamData = constants.get("chameleon");
        Texture chameleonSheet = directory.getEntry("chameleonSheet", Texture.class);
        Animation<TextureRegion> chameleonAnim = createAnimation(chameleonSheet, 13, 0.06f);
        avatar = new Chameleon(units, chamData, chameleonAnim);

        enemies = new ArrayList<>();
        JsonValue enemiesData = constants.get("enemies");
        if (enemiesData != null) {
            Texture enemyTex = directory.getEntry("platform-traci", Texture.class);
            JsonValue enemyPositions = enemiesData.get("positions");
            JsonValue enemyNames = enemiesData.get("names");
            JsonValue enemyType = enemiesData.get("types");
            JsonValue enemyPatrol = enemiesData.get("patrols");
            JsonValue enemyPatrolPath = enemiesData.get("patrol_paths");
            JsonValue enemyDetectionRange = enemiesData.get("detection-range");
            JsonValue enemyFOV = enemiesData.get("fov");
            JsonValue enemyStartRotation = enemiesData.get("startRotation");
            JsonValue enemyRotateAngles = enemiesData.get("rotateAngle");
            for (int i = 0; i < enemyPositions.size; i++) {
                float[] coords = enemyPositions.get(i).asFloatArray();
                String name = enemyNames.get(i).asString();
                String type = enemyType.get(i).asString();

                boolean patrol = enemyPatrol.get(i).asBoolean();
                JsonValue patrolPath = enemyPatrolPath.get(i); // Get the JSON array for this enemy
                List<float[]> patrolPathList = new ArrayList<>();
                for (JsonValue point : patrolPath) {
                    patrolPathList.add(point.asFloatArray()); // Convert each sub-array to float[]
                }

                float detectionRange = enemyDetectionRange.get(i).asFloat();
                float fov = enemyFOV.get(i).asFloat();
                float startRotation = enemyStartRotation.get(i).asFloat();
                float rotateAngle = enemyRotateAngles.get(i).asFloat();
                Enemy enemy = new Enemy(coords, name, type, patrol, patrolPathList, detectionRange,
                    fov, startRotation, rotateAngle, units, enemiesData);
                enemy.setTexture(enemyTex);
                enemies.add(enemy);
            }
        }

//        // Create walls
//        walls = new ArrayList<>();
//        JsonValue wallsData = constants.get("walls");
//        if (wallsData != null) {
//            Texture wallTex = directory.getEntry("shared-earth", Texture.class);
//            JsonValue wallPositions = wallsData.get("positions");
//            for (int i = 0; i < wallPositions.size; i++) {
//                float[] coords = wallPositions.get(i).asFloatArray();
//                Terrain wall = new Terrain(coords, units, wallsData);
//                wall.setTexture(wallTex);
//                walls.add(wall);
//            }
//        }
        // Create walls using the tile layer data from the level editor.
        walls = new ArrayList<>();
        JsonValue wallsData = constants.get("top-left");
        if (wallsData != null) {
            // If wallsData contains the "data" array, it indicates that tile layer data is being used.
            if (wallsData.has("data")) {
                Texture wallTex = directory.getEntry("shared-earth", Texture.class);
                // Get the tile layer's width and height (measured in tiles, i.e., physics units).
                int layerWidth = wallsData.getInt("width");
                int layerHeight = wallsData.getInt("height");
                JsonValue data = wallsData.get("data");
                // Iterate over the entire matrix.
                for (int i = 0; i < data.size; i++) {
                    int tileValue = data.getInt(i);
                    // When tileValue equals 23, it means there is a wall at this tile.
                    if (tileValue == 23) {
                        // Calculate the tile coordinates (column tx and row ty) based on the array index.
                        int tx = i % layerWidth;
                        int ty = i / layerWidth;
                        // Tiled uses a top-left origin; convert to physics world coordinates (origin at bottom-left).
                        ty = layerHeight - 1 - ty;
                        // Each tile occupies 1 physics unit; create the rectangular vertices for this tile.
                        float[] coords = new float[]{
                            tx, ty,
                            tx + 1, ty,
                            tx + 1, ty + 1,
                            tx, ty + 1
                        };
                        // Create the wall object; note that the Terrain constructor scales the coordinates by the units factor.
                        Terrain wall = new Terrain(coords, units, wallsData);
                        wall.setTexture(wallTex);
                        walls.add(wall);
                    }
                }
            } else {
                // If there is no "data" field, fall back to the original logic based on "positions".
                Texture wallTex = directory.getEntry("shared-earth", Texture.class);
                JsonValue wallPositions = wallsData.get("positions");
                for (int i = 0; i < wallPositions.size; i++) {
                    float[] coords = wallPositions.get(i).asFloatArray();
                    Terrain wall = new Terrain(coords, units, wallsData);
                    wall.setTexture(wallTex);
                    walls.add(wall);
                }
            }
        }

//        depths = new ArrayList<>();
//        JsonValue depthsdata = constants.get("walls");
//        if (depthsdata != null) {
//            Texture platTex = directory.getEntry("shared-earth", Texture.class);
//            JsonValue platPositions = depthsdata.get("positions");
//            for (int i = 0; i < platPositions.size; i++) {
//                float[] coords = platPositions.get(i).asFloatArray();
//                // Create the primary wall terrain
//                float x1 = coords[0];
//                float y1 = coords[1];
//                float x2 = coords[2];
//                float y2 = coords[3];
//
//                if (x1 > x2) {
//                    float tempX = x1;  float tempY = y1;
//                    x1 = x2;          y1 = y2;
//                    x2 = tempX;       y2 = tempY;
//                }
//
//                // 2) Get the "depth" from JSON or define a default
//                float depth = depthsdata.getFloat("depth", 1.5f);
//
//                // 3) Create new coordinates for the depth rectangle
//                float[] depthCoords = makeDepthRectangle(x1, y1, x2, y2, depth);
////                System.out.println("Depth coordinates: " + java.util.Arrays.toString(depthCoords));
//                // 4) Construct a second object for the vertical portion
//                WallDepth depthWall = new WallDepth(depthCoords, units, depthsdata);
//                depthWall.setTexture(platTex); // or a different texture if you prefer
//
//                // 5) Store or add it to an ArrayList for your depth walls
//                depths.add(depthWall);
//            }
//        }

        bombs = new ArrayList<>();
        sprays = new ArrayList<>();
    }

    /**
     * Creates a rectangle for the "vertical" depth based on a top edge and a depth amount, in CCW
     * order: (top-left) → (bottom-left) → (bottom-right) → (top-right).
     * <p>
     * The top edge is (x1,y1)->(x2,y2), extruded downward by 'depth'.
     */

    public Door getGoalDoor() {
        return goalDoor;
    }

    public Chameleon getAvatar() {
        return avatar;
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }

    public List<Terrain> getWalls() {
        return walls;
    }

    public List<Terrain> getPlatforms() {
        return platforms;
    }

    public List<Bomb> getBombs() {
        return bombs;
    }

    public List<Spray> getSprays() {
        return sprays;
    }

    public List<WallDepth> getWalldepths() {
        return depths;
    }

    private float[] makeDepthRectangle(float x1, float y1, float x2, float y2, float depth) {
        return new float[]{
            x1, y1,            // top-left
            x1, y1 - depth,    // bottom-left
            x2, y2 - depth,    // bottom-right
            x2, y2             // top-right
        };
    }

    public static Animation<TextureRegion> createAnimation(Texture sheet, int frameCount,
        float frameDuration) {
        int totalWidth = sheet.getWidth();
        int totalHeight = sheet.getHeight();
        int frameWidth = totalWidth / frameCount;
        int frameHeight = totalHeight;
        TextureRegion[][] tmp = TextureRegion.split(sheet, frameWidth, frameHeight);
        TextureRegion[] frames = new TextureRegion[frameCount];
        for (int i = 0; i < frameCount; i++) {
            frames[i] = tmp[0][i];
        }

        Animation<TextureRegion> animation = new Animation<>(frameDuration, frames);
        animation.setPlayMode(Animation.PlayMode.LOOP);
        return animation;
    }

}
