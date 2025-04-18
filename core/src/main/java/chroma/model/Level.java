package chroma.model;

import chroma.controller.LevelSelector;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.AssetDirectory;

import java.util.*;

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
    private List<BackgroundTile> goalTiles;
    private List<Bomb> bombs;
    private List<Spray> sprays;
    private List<Grate> grates;



    /**
     * gid →  corresponding tile
     */
    private Map<Integer, TextureRegion> tileRegions;

    private static final Set<Integer> GRATE_GIDS = Set.of(659, 662, 664, 697, 702, 737, 849, 851, 854);

    /**
     * Constructs a new Level by loading the JSON configuration through the provided LevelSelector.
     *
     * @param directory the AssetDirectory used for loading textures and JSON files.
     * @param units the physics units conversion factor.
     * @param selector the LevelSelector that chooses the JSON configuration for this level.
     */
    public Level(AssetDirectory directory, float units, LevelSelector selector) {
        walls           = new ArrayList<>();
        backgroundTiles = new ArrayList<>();
        goalTiles    = new ArrayList<>();
        bombs           = new ArrayList<>();
        sprays          = new ArrayList<>();
        enemies         = new ArrayList<>();
        grates = new ArrayList<>();


        // levels.json
        // level files
        JsonValue constants = selector.loadCurrentLevel();

        // constant.json
        JsonValue globalConstants = directory.getEntry("platform-constants", JsonValue.class);

        initTileRegions(directory, 16);

        //background
        JsonValue backgroundData = findLayer(constants, "background");
        if (backgroundData != null && backgroundData.has("data")) {
            backgroundTiles = new ArrayList<>();

//            Texture backgroundTex = directory.getEntry("background-tile", Texture.class);
//            backgroundTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

            int layerWidth = backgroundData.getInt("width");
            int layerHeight = backgroundData.getInt("height");
            JsonValue data = backgroundData.get("data");
            for (int i = 0; i < data.size; i++) {
                int gid = data.getInt(i);
                if (gid == 0) continue;                                 // skip empty

                // lookup the sub-texture for this gid
                TextureRegion region = tileRegions.get(gid);
                if (region == null) continue;                         // no tile defined

                // compute tile grid position
                int tx = i % layerWidth;
                int ty = i / layerWidth;
                ty = layerHeight - 1 - ty;                            // flip Y origin

                if (GRATE_GIDS.contains(gid)) {
                    Grate grate = new Grate(region, units, tx, ty);
                    grates.add(grate);
                } else {
                    // create BackgroundTile with the region
                    BackgroundTile tile = new BackgroundTile(region, units);
                    tile.setPosition(tx, ty);
                    backgroundTiles.add(tile);
                }
            }
        }

        //background
        JsonValue goalTileData = findLayer(constants, "goal");
        if (goalTileData != null && goalTileData.has("data")) {

            goalTiles = new ArrayList<>();
//            Texture backgroundTex = directory.getEntry("background-tile", Texture.class);
//            backgroundTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

            int layerWidth = goalTileData.getInt("width");
            int layerHeight = goalTileData.getInt("height");
            JsonValue data = goalTileData.get("data");
            for (int i = 0; i < data.size; i++) {
                int gid = data.getInt(i);
                if (gid == 0) continue;                                 // skip empty

                // lookup the sub-texture for this gid
                TextureRegion region = tileRegions.get(gid);
                if (region == null) continue;                         // no tile defined

                // compute tile grid position
                int tx = i % layerWidth;
                int ty = i / layerWidth;
                ty = layerHeight - 1 - ty;                            // flip Y origin

                // create BackgroundTile with the region
                BackgroundTile tile = new BackgroundTile(region, units);
                tile.setPosition(tx, ty);
                goalTiles.add(tile);
            }
        }
        // Parse the "walls" tile layer and build a list of Terrain tiles
        JsonValue wallsData = findLayer(constants, "walls");
        if (wallsData != null && wallsData.has("data")) {
            walls = new ArrayList<>();

            int layerWidth  = wallsData.getInt("width");
            int layerHeight = wallsData.getInt("height");
            JsonValue data  = wallsData.get("data");

            for (int i = 0; i < data.size; i++) {
                int gid = data.getInt(i);
                if (gid == 0) continue;                           // skip empty tiles

                // compute tile coordinates in grid
                int tx = i % layerWidth;
                int ty = i / layerWidth;
                ty = layerHeight - 1 - ty;                        // flip Y origin

                // lookup the sub-texture for this gid
                TextureRegion region = tileRegions.get(gid);
                if (region == null) continue;                     // no matching region
                int tileValue = data.getInt(i);
                // create a 1×1 tile-based Terrain at (tx,ty)
                if (tileValue != 0) {
                    float[] coords = createCoords(tx, ty);
                    Terrain wall = new Terrain(region,coords, units);
                    walls.add(wall);
//                    wall.setTexture(region.getTexture());
                }
            }
        }



        // Create the goal door
        Texture goalTex = directory.getEntry("shared-goal", Texture.class);
        JsonValue goalData = globalConstants.get("goal");
        goalDoor = new Door(units, goalData);
        goalDoor.setTexture(goalTex);
        goalDoor.getObstacle().setName("goal");

        // Create the chameleon (player) using animation
        JsonValue chamData = globalConstants.get("chameleon");
        Texture chameleonSheet = directory.getEntry("chameleonSheet", Texture.class);
        Animation<TextureRegion> chameleonAnim = createAnimation(chameleonSheet, 13, 0.1f);
        avatar = new Chameleon(units, chamData, chameleonAnim);

        // Create enemies
        enemies = new ArrayList<>();
        JsonValue enemiesData = globalConstants.get("enemies");
        if (enemiesData != null) {
            Texture enemyTex = directory.getEntry("enemy", Texture.class);
            Texture enemyAlertSheet = directory.getEntry("enemyAlertSheet", Texture.class);
            Animation<TextureRegion> enemyAlertAnim = createAnimation(enemyAlertSheet, 13, 0.1f);
            JsonValue enemyPositions = enemiesData.get("positions");
            JsonValue enemyType = enemiesData.get("types");
            JsonValue enemyPatrol = enemiesData.get("patrols");
            JsonValue enemyPatrolPath = enemiesData.get("patrol_paths");
            JsonValue enemyStartRotation = enemiesData.get("startRotation");
            JsonValue enemyRotateAngles = enemiesData.get("rotateAngle");
            for (int i = 0; i < enemyPositions.size; i++) {
                float[] coords = enemyPositions.get(i).asFloatArray();
                String type = enemyType.get(i).asString();

                boolean patrol = enemyPatrol.get(i).asBoolean();
                JsonValue patrolPath = enemyPatrolPath.get(i); // Get the JSON array for this enemy
                List<float[]> patrolPathList = new ArrayList<>();
                for (JsonValue point : patrolPath) {
                    patrolPathList.add(point.asFloatArray()); // Convert each sub-array to float[]
                }
                float startRotation = enemyStartRotation.get(i).asFloat();
                float rotateAngle = enemyRotateAngles.get(i).asFloat();
                Enemy enemy = new Enemy(coords, type, patrol, patrolPathList, startRotation, rotateAngle, units, enemiesData, enemyAlertAnim);
                enemy.setTexture(enemyTex);
                enemies.add(enemy);
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
    public List<BackgroundTile> getGoalTiles() {
        return goalTiles;
    }


    public List<Bomb> getBombs() {
        return bombs;
    }

    public List<Spray> getSprays() {
        return sprays;
    }

    public List<Grate> getGrates() {
        return grates;
    }


//    public List<WallDepth> getWalldepths() {
//        return depths;
//    }

    /**
     * Split the tileset PNG into regions of size tileSize×tileSize
     * and build gid→TextureRegion map.
     */
    private void initTileRegions(AssetDirectory dir, int tileSize) {
        // load the full tileset image
        Texture tileset = dir.getEntry("tileset", Texture.class);

        // split into small regions
        TextureRegion[][] grid = TextureRegion.split(tileset, tileSize, tileSize);

        tileRegions = new HashMap<>();
        int gid = 1;  // assume this is your first (and only) tileset, so first gid=1
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                tileRegions.put(gid++, grid[row][col]);
            }
        }
    }




    private JsonValue findLayer(JsonValue mapRoot, String layerName) {
        JsonValue layers = mapRoot.get("layers");
        if (layers == null) {
            return null;
        }
        for (JsonValue layer : layers) {
            if (layerName.equals(layer.getString("name", ""))) {
                return layer;
            }
        }
        return null;
    }
    private float[] createCoords(int tx, int ty) {
        return new float[]{
            tx, ty,
            tx + 1, ty,
            tx + 1, ty + 1,
            tx, ty + 1
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
