package chroma.model;

import chroma.controller.LevelSelector;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.AssetDirectory;

import java.awt.*;
import java.util.*;
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
    private List<BackgroundTile> wallsNoCover;
    private List<BackgroundTile> wallsCover;
    private List<BackgroundTile> wallsTop;
    private List<BackgroundTile> backgroundTiles;
    private List<BackgroundTile> goalTiles;
    private List<BackgroundTile> goal2Tiles;
    private List<BackgroundTile> goal3Tiles;
    private List<Bomb> bombs;
    private List<Spray> sprays;
    private List<Grate> grates;
    private List<Laser> lasers;
    private List<Collision> collision;
    private String[] levelfiles;
    private List<GoalCollision> goalCollisions;
//    private int mapWidthInTiles;
//    private int mapHeightInTiles;
//    public static final int TILE_WIDTH = 16;
//    public static final int TILE_HEIGHT = 16;
//    Set<Point> bombableTiles = new HashSet<>();

    /**
     * gid →  corresponding tile
     */
    private Map<Integer, TextureRegion> tileRegions;

    private static final Set<Integer> GRATE_GIDS = Set.of(
        659, 660, 661, 662, 663, 664,
        697, 698, 699, 700, 701, 702,
        735, 736, 737, 738, 739, 740,
        773, 774, 775, 776, 777, 778,
        811, 812, 813, 814, 815, 816,
        849, 850, 851, 852, 853, 854
    );
    /**
     * Constructs a new Level by loading the JSON configuration through the provided LevelSelector.
     *
     * @param directory the AssetDirectory used for loading textures and JSON files.
     * @param units the physics units conversion factor.
     * @param selector the LevelSelector that chooses the JSON configuration for this level.
     */
    public Level(AssetDirectory directory, float units, LevelSelector selector) {
        wallsNoCover = new ArrayList<>();
        wallsCover = new ArrayList<>();
        wallsTop = new ArrayList<>();
        backgroundTiles = new ArrayList<>();
        goalTiles = new ArrayList<>();
        goal2Tiles = new ArrayList<>();
        goal3Tiles = new ArrayList<>();
        bombs           = new ArrayList<>();
        sprays          = new ArrayList<>();
        enemies         = new ArrayList<>();
        collision = new ArrayList<>();
        goalCollisions = new ArrayList<>();
        grates = new ArrayList<>();



        // levels.json
        // level files
        JsonValue constants = selector.loadCurrentLevel();

        // constant.json
        JsonValue globalConstants = directory.getEntry("platform-constants", JsonValue.class);

        initTileRegions(directory, 16);

        levelfiles = selector.getLevelFiles();

        //background
        JsonValue backgroundData = findLayer(constants, "background");
        if (backgroundData != null && backgroundData.has("data")) {
//            mapWidthInTiles = backgroundData.getInt("width");
//            mapHeightInTiles = backgroundData.getInt("height");
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
//                bombableTiles.add(new Point(tx, ty));
            }
        }




        //background
        JsonValue goalTileData = findLayer(constants, "goal1");
        if (goalTileData != null && goalTileData.has("data")) {

            goalTiles = new ArrayList<>();
//            Texture backgroundTex = directory.getEntry("background-tile", Texture.class);
//            backgroundTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

            int layerWidth = goalTileData.getInt("width");
            int layerHeight = goalTileData.getInt("height");
            JsonValue data = goalTileData.get("data");
            for (int i = 0; i < data.size; i++) {
                int gid = data.getInt(i);
                if (gid == 0) continue; // skip empty


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
                float[] coords = createCoords(tx, ty);
                goalTiles.add(tile);
                GoalCollision goalthing = new GoalCollision(coords, units,new Vector2(tx,ty));
                goalCollisions.add(goalthing);

//                bombableTiles.add(new Point(tx, ty));
            }
        }
        JsonValue goalTileData2 = findLayer(constants, "goal2");
        if (goalTileData2 != null && goalTileData2.has("data")) {

            goal2Tiles = new ArrayList<>();
//            Texture backgroundTex = directory.getEntry("background-tile", Texture.class);
//            backgroundTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

            int layerWidth = goalTileData2.getInt("width");
            int layerHeight = goalTileData2.getInt("height");
            JsonValue data = goalTileData2.get("data");
            for (int i = 0; i < data.size; i++) {
                int gid = data.getInt(i);
                if (gid == 0) continue; // skip empty


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
                float[] coords = createCoords(tx, ty);
                goal2Tiles.add(tile);
                GoalCollision goalthing = new GoalCollision(coords, units,new Vector2(tx,ty));
                goalCollisions.add(goalthing);
//                bombableTiles.add(new Point(tx, ty));
            }
        }
        JsonValue goalTileData3 = findLayer(constants, "goal3");
        if (goalTileData3 != null && goalTileData3.has("data")) {
            goal3Tiles = new ArrayList<>();
//            Texture backgroundTex = directory.getEntry("background-tile", Texture.class);
//            backgroundTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

            int layerWidth = goalTileData3.getInt("width");
            int layerHeight = goalTileData3.getInt("height");
            JsonValue data = goalTileData3.get("data");
            for (int i = 0; i < data.size; i++) {
                int gid = data.getInt(i);
                if (gid == 0) continue; // skip empty


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
                float[] coords = createCoords(tx, ty);
                goal3Tiles.add(tile);
                GoalCollision goalthing = new GoalCollision(coords, units,new Vector2(tx,ty));
                goalCollisions.add(goalthing);
//                bombableTiles.add(new Point(tx, ty));
            }
        }
        // Parse the "walls" tile layer and build a list of Terrain tiles
        JsonValue wallsData1 = findLayer(constants, "walls-no-cover");
        if (wallsData1 != null && wallsData1.has("data")) {
            wallsNoCover = new ArrayList<>();

            int layerWidth  = wallsData1.getInt("width");
            int layerHeight = wallsData1.getInt("height");
            JsonValue data  = wallsData1.get("data");

            for (int i = 0; i < data.size; i++) {
                int gid = data.getInt(i);
                if (gid == 0) continue;                           // skip empty tiles

                // compute tile coordinates in grid
                int tx = i % layerWidth;
                int ty = i / layerWidth;
                ty = layerHeight - 1 - ty;                        // flip Y origin
//                bombableTiles.remove(new Point(tx, ty));
                // lookup the sub-texture for this gid
                TextureRegion region = tileRegions.get(gid);
                if (region == null) continue;                     // no matching region
                int tileValue = data.getInt(i);
                // create a 1×1 tile-based Terrain at (tx,ty)
                if (tileValue != 0) {
                    float[] coords = createCoords(tx, ty);
                    BackgroundTile wall = new BackgroundTile(region,units);
                    wall.setPosition(tx, ty);
                    wallsNoCover.add(wall);
//                    wall.setTexture(region.getTexture());
                }
            }
        }
        JsonValue wallsData2 = findLayer(constants, "walls-cover");
        if (wallsData2 != null && wallsData2.has("data")) {
            wallsCover = new ArrayList<>();

            int layerWidth  = wallsData2.getInt("width");
            int layerHeight = wallsData2.getInt("height");
            JsonValue data  = wallsData2.get("data");

            for (int i = 0; i < data.size; i++) {
                int gid = data.getInt(i);
                if (gid == 0) continue;                           // skip empty tiles

                // compute tile coordinates in grid
                int tx = i % layerWidth;
                int ty = i / layerWidth;
                ty = layerHeight - 1 - ty;                        // flip Y origin
//                bombableTiles.remove(new Point(tx, ty));
                // lookup the sub-texture for this gid
                TextureRegion region = tileRegions.get(gid);
                if (region == null) continue;                     // no matching region
                int tileValue = data.getInt(i);
                // create a 1×1 tile-based Terrain at (tx,ty)
                if (tileValue != 0) {
                    float[] coords = createCoords(tx, ty);
                    BackgroundTile wall = new BackgroundTile(region,units);
                    wall.setPosition(tx, ty);
                    wallsCover.add(wall);
//                    wall.setTexture(region.getTexture());
                }
            }
        }
        // Parse the "walls" tile layer and build a list of Terrain tiles
        JsonValue wallsTopData = findLayer(constants, "walls-top");
        if (wallsTopData != null && wallsTopData.has("data")) {
//            walls = new ArrayList<>();

            int layerWidth  = wallsTopData.getInt("width");
            int layerHeight = wallsTopData.getInt("height");
            JsonValue data  = wallsTopData.get("data");

            for (int i = 0; i < data.size; i++) {
                int gid = data.getInt(i);
                if (gid == 0) continue;                           // skip empty tiles

                // compute tile coordinates in grid
                int tx = i % layerWidth;
                int ty = i / layerWidth;
                ty = layerHeight - 1 - ty;// flip Y origin
//                bombableTiles.remove(new Point(tx, ty));

                // lookup the sub-texture for this gid
                TextureRegion region = tileRegions.get(gid);
                if (region == null) continue;                     // no matching region
                int tileValue = data.getInt(i);
                // create a 1×1 tile-based Terrain at (tx,ty)
                if (tileValue != 0) {
                    BackgroundTile wall = new BackgroundTile(region,units);
                    wall.setPosition(tx, ty);
                    wallsTop.add(wall);
                }
            }
        }
        //This is the new collision layer it differs from Terrain and Background
        //As it is an actual physics object
        JsonValue collisionData = findLayer(constants, "collision");
        if (collisionData != null && collisionData.has("data")) {
//            walls = new ArrayList<>();

            int layerWidth  = collisionData.getInt("width");
            int layerHeight = collisionData.getInt("height");
            JsonValue data  = collisionData.get("data");

            for (int i = 0; i < data.size; i++) {
                int gid = data.getInt(i);
                if (gid == 0) continue;                           // skip empty tiles

                // compute tile coordinates in grid
                int tx = i % layerWidth;
                int ty = i / layerWidth;
                ty = layerHeight - 1 - ty;                        // flip Y origin
//                bombableTiles.add(new Point(tx, ty));

                // lookup the sub-texture for this gid
                TextureRegion region = tileRegions.get(gid);
                if (region == null) continue;                     // no matching region
                int tileValue = data.getInt(i);
                // create a 1×1 tile-based Collision at (tx,ty)
                if (tileValue != 0) {
                    float[] coords = createCoords(tx, ty);
                    Collision block = new Collision(coords, units,new Vector2(tx,ty));
                    collision.add(block);
                }
            }
        }



        // ---------- Door ----------
        JsonValue doorLayer = findLayer(constants, "door");
        if (doorLayer != null && doorLayer.has("data")) {
            JsonValue doorData = doorLayer.get("data");
            int layerWidth  = doorLayer.getInt("width");
            int layerHeight = doorLayer.getInt("height");

            List<Vector2> doorTiles = new ArrayList<>();
            for (int i = 0; i < doorData.size; i++) {
                if (doorData.getInt(i) == 0) continue;
                int tx = i % layerWidth;
                int ty = layerHeight - 1 - (i / layerWidth);
                doorTiles.add(new Vector2(tx, ty));
//                bombableTiles.add(new Point(tx, ty));
            }

            if (!doorTiles.isEmpty()) {
                int minX = doorTiles.stream().mapToInt(v -> (int)v.x).min().getAsInt();
                int minY = doorTiles.stream().mapToInt(v -> (int)v.y).min().getAsInt();

                Vector2 doorCenter = new Vector2(minX + 2f, minY + 2f);

                Texture ventSheet = directory.getEntry("vent", Texture.class);
                Texture chameleonFallSheet = directory.getEntry("ventFall", Texture.class);
                Animation<TextureRegion> chameleonFallAnim = createAnimation(chameleonFallSheet, 12, 0.08f);

                TextureRegion[] frames = Level.createAnimation(ventSheet, 22, 0.1f).getKeyFrames();

                Animation<TextureRegion> closedAnim = new Animation<>(1f, frames[0]);
                closedAnim.setPlayMode(Animation.PlayMode.LOOP);

                Animation<TextureRegion> openAnim = new Animation<>(0.075f, frames);
                openAnim.setPlayMode(Animation.PlayMode.NORMAL);

                Music openSound = directory.getEntry("door_open", Music.class);

                goalDoor = new Door(units, closedAnim, openAnim, doorCenter, chameleonFallAnim, openSound);
                goalDoor.getObstacle().setName("door");
            }
        }

        // Create the chameleon (player) using animation
        JsonValue globalCham = globalConstants.get("chameleon");
        JsonValue levelCham = constants.get("chameleon");
        Texture chameleonSheet = directory.getEntry("chameleonSheet", Texture.class);
        Texture chameleonUpWalkSheet = directory.getEntry("chameleonUpWalk", Texture.class);
        Texture chameleonDownWalkSheet = directory.getEntry("chameleonDownWalk", Texture.class);
        Texture bombSheet = directory.getEntry("chameleonSkillSheet", Texture.class);
        Animation<TextureRegion> chameleonAnim = createAnimation(chameleonSheet, 13, 0.07f);
        Animation<TextureRegion> chameleonUpWalkAnim = createAnimation(chameleonUpWalkSheet, 15, 0.07f);
        Animation<TextureRegion> chameleonDownWalkAnim = createAnimation(chameleonDownWalkSheet, 15, 0.07f);
        Animation<TextureRegion> bombAnim = createAnimation(bombSheet, 27, 0.07f);
        Texture chameleonIdleSheet = directory.getEntry("chameleonIdleSheet", Texture.class);
        Animation<TextureRegion> idleAnim =
            createAnimation(chameleonIdleSheet, 2, 0.25f);
        Music walkSound = directory.getEntry("chameleon_walk", Music.class);
        avatar = new Chameleon(units, globalCham,levelCham, chameleonAnim, chameleonUpWalkAnim, chameleonDownWalkAnim, walkSound,idleAnim);
        avatar.setBombAnimation(bombAnim);

        // Create enemies
        enemies = new ArrayList<>();
        String name = levelfiles[selector.getCurrentLevel() - 1];
        JsonValue enemiesData = globalConstants.get("enemies").get(name);
        if (enemiesData != null) {
//            Texture enemyTex = directory.getEntry("enemy", Texture.class);
            Texture enemyAlertSheet = directory.getEntry("enemyAlertSheet", Texture.class);
            Animation<TextureRegion> enemyAlertAnim = createAnimation(enemyAlertSheet, 13, 0.2f);
            Texture enemyBlueRedSheet = directory.getEntry("enemyBlueRedSheet", Texture.class);
            Animation<TextureRegion> enemyBlueRedAnim = createAnimation(enemyBlueRedSheet, 8, 0.2f);
            // BLUE
            Texture enemySideSheetBlue = directory.getEntry("enemySideSheetBlue", Texture.class);
            Animation<TextureRegion> enemySideAnimBlue = createAnimation(enemySideSheetBlue, 8, 0.3f);
            Texture enemyFrontSheetBlue = directory.getEntry("enemyFrontSheetBlue", Texture.class);
            Animation<TextureRegion> enemyFrontAnimBlue = createAnimation(enemyFrontSheetBlue, 12, 0.2f);
            Texture enemyBackSheetBlue = directory.getEntry("enemyBackSheetBlue", Texture.class);
            Animation<TextureRegion> enemyBackAnimBlue = createAnimation(enemyBackSheetBlue, 12, 0.2f);
            // RED
            Texture enemySideSheetRed = directory.getEntry("enemySideSheetRed", Texture.class);
            Animation<TextureRegion> enemySideAnimRed = createAnimation(enemySideSheetRed, 8, 0.15f);
            Texture enemyFrontSheetRed = directory.getEntry("enemyFrontSheetRed", Texture.class);
            Animation<TextureRegion> enemyFrontAnimRed = createAnimation(enemyFrontSheetRed, 12, 0.1f);
            Texture enemyBackSheetRed = directory.getEntry("enemyBackSheetRed", Texture.class);
            Animation<TextureRegion> enemyBackAnimRed = createAnimation(enemyBackSheetRed, 12, 0.1f);

            // Store all animations together
            ColorAnimations blueSet = new ColorAnimations(enemyFrontAnimBlue, enemySideAnimBlue, enemyBackAnimBlue);
            ColorAnimations redSet  = new ColorAnimations(enemyFrontAnimRed, enemySideAnimRed, enemyBackAnimRed);
            EnemyAnimations anims = new EnemyAnimations(
                enemyAlertAnim,
                enemyBlueRedAnim,
                blueSet,
                redSet
            );

            JsonValue enemiesRoot = globalConstants.get("enemies");
            JsonValue globalEnemy = enemiesRoot.get("global");
            JsonValue levelData   = enemiesRoot.get(name);

            JsonValue enemyPositions    = levelData.get("positions");
            JsonValue enemyTypes        = levelData.get("types");
            JsonValue enemyPatrols      = levelData.get("patrols");
            JsonValue enemyPatrolPaths  = levelData.get("patrol_paths");
            JsonValue enemyStartRot     = levelData.get("startRotation");
            JsonValue enemyRotateAngles = levelData.get("rotateAngle");

            Music playerSpottedSound = directory.getEntry("enemy_spotted", Music.class);

            for (int i = 0; i < enemyPositions.size; i++) {
                float[] coords          = enemyPositions.get(i).asFloatArray();
                String type             = enemyTypes.get(i).asString();
                boolean patrol          = enemyPatrols.get(i).asBoolean();

                List<float[]> patrolPathList = new ArrayList<>();
                for (JsonValue point : enemyPatrolPaths.get(i)) {
                    patrolPathList.add(point.asFloatArray());
                }

                float startRotation = enemyStartRot.get(i).asFloat();
                float rotateAngle   = enemyRotateAngles.get(i).asFloat();

                Enemy enemy = new Enemy(
                    coords, type, patrol, patrolPathList,
                    startRotation, rotateAngle,
                    units,
                    globalEnemy,
                    levelData,
                    anims,
                    playerSpottedSound
                );
                enemies.add(enemy);
            }

        }
        lasers        = new ArrayList<>();
        // ---------- Lasers (1×1 tiles) ----------
        JsonValue laserLayer = findLayer(constants, "laser");
        if (laserLayer != null && laserLayer.has("data")) {
            int w = laserLayer.getInt("width");
            int h = laserLayer.getInt("height");
            JsonValue data = laserLayer.get("data");
            for (int i = 0; i < data.size; i++) {
                int gid = data.getInt(i);
                if (gid == 0) { continue; }
                TextureRegion region = tileRegions.get(gid);
                if (region == null) { continue; }

                // compute grid pos (flip Y)
                int tx = i % w;
                int ty = h - 1 - (i / w);
                // center of tile in world coords
                Vector2 center = new Vector2(tx + 0.5f, ty + 0.5f);

                Laser laser = new Laser(units, region, center);
                lasers.add(laser);
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

    public List<GoalCollision> getGoalCollisions(){
        return goalCollisions;
    }

    public Chameleon getAvatar() {
        return avatar;
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }

    public List<BackgroundTile> getWallsNoCover() {
        return wallsNoCover;
    }
    public List<BackgroundTile> getWallsCover() {
        return wallsCover;
    }
    public List<BackgroundTile> getWallsTop() {
        return wallsTop;
    }
    public List<Laser> getLasers() {
        return lasers;
    }

    public List<Collision> getCollision(){return collision;}

    public List<BackgroundTile> getBackgroundTiles() {
        return backgroundTiles;
    }
    public List<BackgroundTile> getGoalTiles() {
        return goalTiles;
    }
    public List<BackgroundTile> getGoal2Tiles() {
        return goal2Tiles;
    }
    public List<BackgroundTile> getGoal3Tiles() {
        return goal3Tiles;
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

        tileset.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        tileset.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
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

//    public int getWidth() {
//        return mapWidthInTiles * TILE_WIDTH;
//    }
//
//    public int getHeight() {
//        return mapHeightInTiles * TILE_HEIGHT;
//    }


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

    /** Data structures to hold all enemy animations */
    public class ColorAnimations {
        public final Animation<TextureRegion> frontAnim;
        public final Animation<TextureRegion> sideAnim;
        public final Animation<TextureRegion> backAnim;

        public ColorAnimations(Animation<TextureRegion> front, Animation<TextureRegion> side, Animation<TextureRegion> back) {
            this.frontAnim = front;
            this.sideAnim = side;
            this.backAnim = back;
        }
    }

    public class EnemyAnimations {
        public final Animation<TextureRegion> alertAnim;
        public final Animation<TextureRegion> blueRedAnim;
        public final ColorAnimations blueAnimations;
        public final ColorAnimations redAnimations;

        public EnemyAnimations(Animation<TextureRegion> alertAnim,
                               Animation<TextureRegion> blueRedAnim,
                               ColorAnimations blueAnimations,
                               ColorAnimations redAnimations) {
            this.alertAnim = alertAnim;
            this.blueRedAnim = blueRedAnim;
            this.blueAnimations = blueAnimations;
            this.redAnimations = redAnimations;
        }
    }

//    public boolean isTileBombable(int x, int y) {
//        return bombableTiles.contains(new Point(x, y));
//    }
//
//    public Set<Point> getBombableTiles() {
//        return bombableTiles;
//    }

}
