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
    private List<BackgroundTile> backgroundTiles;
    private List<BackgroundTile> machineTiles;
    private List<Bomb> bombs;
    private List<Spray> sprays;


    /**
     * Constructs a new Level by loading the JSON configuration through the provided LevelSelector.
     *
     * @param directory the AssetDirectory used for loading textures and JSON files.
     * @param units the physics units conversion factor.
     * @param selector the LevelSelector that chooses the JSON configuration for this level.
     */
    public Level(AssetDirectory directory, float units, LevelSelector selector) {
        // Load the level JSON configuration via the LevelSelector.

        // levels.json
        JsonValue constants = selector.loadCurrentLevel();

        // constant.json
        JsonValue globalConstants = directory.getEntry("platform-constants", JsonValue.class);

        // Create the goal door
        Texture goalTex = directory.getEntry("shared-goal", Texture.class);
        JsonValue goalData = globalConstants.get("goal");
        goalDoor = new Door(units, goalData);
        goalDoor.setTexture(goalTex);
        goalDoor.getObstacle().setName("goal");

        //background
        backgroundTiles = new ArrayList<>();
        JsonValue backgroundData = constants.get("background");
        if (backgroundData.has("data")) {
            Texture wallTex = directory.getEntry("background-tile", Texture.class);
            wallTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            int layerWidth = backgroundData.getInt("width");
            int layerHeight = backgroundData.getInt("height");
            JsonValue data = backgroundData.get("data");
            for (int i = 0; i < data.size; i++) {
                int tileValue = data.getInt(i);
                if (tileValue == 28) {
                    int tx = i % layerWidth;
                    int ty = i / layerWidth;
                    ty = layerHeight - 1 - ty;
                    String type = backgroundData.getString("shape", "square");
                    float[] coords = createCoords(tx, ty, type);
                    BackgroundTile backgroundTile = new BackgroundTile(coords, units, backgroundData);
                    backgroundTile.setTexture(wallTex);
                    backgroundTiles.add(backgroundTile);
                }
            }
        }
        //goal tiles
        machineTiles = new ArrayList<>();
        JsonValue goalTileData = constants.get("g");
        if (goalTileData.has("data")) {
            Texture machineTex = directory.getEntry("goalTileTemp", Texture.class);
            machineTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            int layerWidth = goalTileData.getInt("width");
            int layerHeight = goalTileData.getInt("height");
            JsonValue data = goalTileData.get("data");
            for (int i = 0; i < data.size; i++) {
                int tileValue = data.getInt(i);
                if (tileValue == 45 || tileValue == 46 || tileValue == 57 || tileValue == 58 ||
                    tileValue == 69 || tileValue == 70){
                    int tx = i % layerWidth;
                    int ty = i / layerWidth;
                    ty = layerHeight - 1 - ty;
                    String type = goalTileData.getString("shape", "square");
                    float[] coords = createCoords(tx, ty, type);
                    BackgroundTile goalTile = new BackgroundTile(coords, units, goalTileData);
                    goalTile.setTexture(machineTex);
                    machineTiles.add(goalTile);
                }
            }
        }



        // Create the chameleon (player) using animation
        JsonValue chamData = globalConstants.get("chameleon");
        Texture chameleonSheet = directory.getEntry("chameleonSheet", Texture.class);
        Animation<TextureRegion> chameleonAnim = createAnimation(chameleonSheet, 13, 0.1f);
        avatar = new Chameleon(units, chamData, chameleonAnim);

        enemies = new ArrayList<>();
        JsonValue enemiesData = globalConstants.get("enemies");
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
//         Create walls using the tile layer data from the level editor.
        walls = new ArrayList<>();

        JsonValue wallsDepth = constants.get("depth");
        if ( wallsDepth != null) {
            if (wallsDepth.has("data")) {
                Texture wallTex1 = directory.getEntry("wall-up", Texture.class);
                Texture wallTex2 = directory.getEntry("wall-down", Texture.class);
                wallTex1.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                wallTex2.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                int layerWidth =  wallsDepth.getInt("width");
                int layerHeight =  wallsDepth.getInt("height");
                JsonValue data =  wallsDepth.get("data");
                for (int i = 0; i < data.size; i++) {
                    int tileValue = data.getInt(i);
                    if (tileValue == 6) {
                        int tx = i % layerWidth;
                        int ty = i / layerWidth;
                        ty = layerHeight - 1 - ty;
                        String type =  wallsDepth.getString("shape", "square");
                        float[] coords = createCoords(tx, ty, type);
                        Terrain wall = new Terrain(coords, units,  wallsDepth);
                        wall.setTexture(wallTex1);
                        wall.setDepthColor();
                        walls.add(wall);
                    }
                    if (tileValue == 18) {
                        int tx = i % layerWidth;
                        int ty = i / layerWidth;
                        ty = layerHeight - 1 - ty;
                        String type =  wallsDepth.getString("shape", "square");
                        float[] coords = createCoords(tx, ty, type);
                        Terrain wall = new Terrain(coords, units,  wallsDepth);
                        wall.setTexture(wallTex2);
                        wall.setDepthColor();
                        walls.add(wall);
                    }
                }
            }
        }

        JsonValue wallsData = constants.get("top-left");
        if (wallsData != null) {
            // If wallsData contains the "data" array, it indicates that tile layer data is being used.
            if (wallsData.has("data")) {
                Texture wallTex = directory.getEntry("border-left", Texture.class);
                wallTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
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
                        String type = wallsData.getString("shape", "square");
                        float[] coords = createCoords(tx, ty, type);
                        // Create the wall object; note that the Terrain constructor scales the coordinates by the units factor.
                        Terrain wall = new Terrain(coords, units, wallsData);
                        wall.setTexture(wallTex);
                        walls.add(wall);
                    }
                }
            }
        }

        JsonValue wallsRightData = constants.get("top-right");
        if (wallsRightData != null) {
            if (wallsRightData.has("data")) {
                Texture wallTex = directory.getEntry("border-right", Texture.class);
                wallTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                int layerWidth = wallsRightData.getInt("width");
                int layerHeight = wallsRightData.getInt("height");
                JsonValue data = wallsRightData.get("data");
                for (int i = 0; i < data.size; i++) {
                    int tileValue = data.getInt(i);
                    if (tileValue == 22) {
                        int tx = i % layerWidth;
                        int ty = i / layerWidth;
                        ty = layerHeight - 1 - ty;
                        String type = wallsRightData.getString("shape", "square");
                        float[] coords = createCoords(tx, ty, type);
                        Terrain wall = new Terrain(coords, units, wallsRightData);
                        wall.setTexture(wallTex);
                        walls.add(wall);
                    }
                }
            }
        }

        JsonValue walls3Data = constants.get("top");
        if (walls3Data != null) {
            if (walls3Data.has("data")) {
                Texture wallTex = directory.getEntry("wall-top", Texture.class);
                wallTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                int layerWidth = walls3Data.getInt("width");
                int layerHeight = walls3Data.getInt("height");
                JsonValue data = walls3Data.get("data");
                for (int i = 0; i < data.size; i++) {
                    int tileValue = data.getInt(i);
                    if (tileValue == 38) {
                        int tx = i % layerWidth;
                        int ty = i / layerWidth;
                        ty = layerHeight - 1 - ty;
                        String type = walls3Data.getString("shape", "square");
                        float[] coords = createCoords(tx, ty, type);
                        Terrain wall = new Terrain(coords, units, walls3Data);
                        wall.setTexture(wallTex);
                        walls.add(wall);
                    }
                }
            }
        }

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

    public List<BackgroundTile> getBackgroundTiles() {
        return backgroundTiles;
    }
    public List<BackgroundTile> getMachineTiles() {
        return machineTiles;
    }


    public List<Bomb> getBombs() {
        return bombs;
    }

    public List<Spray> getSprays() {
        return sprays;
    }

//    public List<WallDepth> getWalldepths() {
//        return depths;
//    }


    private float[] createCoords(float tx, float ty, String type) {
        switch(type) {
            case "left":
                return new float[]{
                    tx,     ty,
                    tx+0.5f, ty,
                    tx+0.5f, ty+1,
                    tx,     ty+1
                };
            case "right":
                return new float[]{
                    tx+0.5f, ty,
                    tx+1,    ty,
                    tx+1,    ty+1,
                    tx+0.5f, ty+1
                };
            case "top":
                return new float[]{
                    tx,     ty+0.5f,
                    tx+1,   ty+0.5f,
                    tx+1,   ty+1,
                    tx,     ty+1
                };
            case "bottom":
                return new float[]{
                    tx,     ty,
                    tx+1,   ty,
                    tx+1,   ty+0.5f,
                    tx,     ty+0.5f
                };
            case "square":
            default:
                // 默认正方形碰撞体
                return new float[]{
                    tx,     ty,
                    tx+1,   ty,
                    tx+1,   ty+1,
                    tx,     ty+1
                };
        }
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
