package chroma.model;

import chroma.controller.LevelSelector;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.AssetDirectory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LevelNew {


    private Door goalDoor;
    private Chameleon avatar;
    private List<Enemy> enemies;
    private List<Terrain> walls;
    private List<BackgroundTile> backgroundTiles;
    private List<BackgroundTile> machineTiles;
    private List<Bomb> bombs;
    private List<Spray> sprays;
    /**
     * gid →  corresponding tile
     */
    private Map<Integer, TextureRegion> tileRegions;


    /**
     * Constructs a new Level by loading the JSON configuration through the provided LevelSelector.
     *
     * @param directory the AssetDirectory used for loading textures and JSON files.
     * @param units     the physics units conversion factor.
     * @param selector  the LevelSelector that chooses the JSON configuration for this level.
     */
    public LevelNew(AssetDirectory directory, float units, LevelSelector selector) {

        // levels.json
        JsonValue constants = selector.loadCurrentLevel();

        initTileRegions(constants, directory);
        // constant.json
        JsonValue globalConstants = directory.getEntry("platform-constants", JsonValue.class);

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

                // create BackgroundTile with the region
                BackgroundTile tile = new BackgroundTile(region, units);
                tile.setPosition(tx, ty);
                backgroundTiles.add(tile);

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

        bombs = new ArrayList<>();
        sprays = new ArrayList<>();
    }

    /**
     * Split the tileset image into regions and build the gid→TextureRegion map.
     */
    private void initTileRegions(JsonValue mapJson, AssetDirectory directory) {
        JsonValue ts = mapJson.get("tilesets").get(0);
        int firstGid = ts.getInt("firstgid");
        String source = ts.getString("source");              // e.g. "tileset16.tsx"
        JsonValue tsx = directory.getEntry(removeExt(source), JsonValue.class);

        int tileW = tsx.getInt("tilewidth");
        int tileH = tsx.getInt("tileheight");
        String imgSrc = tsx.getString("image");              // e.g. "tileset16.png"
        Texture tex = directory.getEntry(removeExt(imgSrc), Texture.class);

        TextureRegion[][] grid = TextureRegion.split(tex, tileW, tileH);
        tileRegions = new HashMap<>();
        int gid = firstGid;
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                tileRegions.put(gid++, grid[row][col]);
            }
        }
    }

    /**
     * Strip file extension from "name.tsx" or "name.png" → "name"
     */
    private String removeExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
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
