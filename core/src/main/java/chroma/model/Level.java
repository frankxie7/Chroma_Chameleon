package chroma.model;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.AssetDirectory;
import java.util.ArrayList;
import java.util.List;
/**
 * Level
 * -----
 * This class is responsible for constructing the game environment from JSON configuration.
 * It instantiates key game objects CURRENTLY including:
 *   - The goal door.
 *   - The player (Chameleon).
 *   - Walls and platforms.
 *
 * Level encapsulates the object creation logic so that higher-level controllers need not manage
 * the details of each object's construction. Getter methods provide access to these objects.
 */
public class Level {
    private Door goalDoor;
    private Chameleon avatar;
    private List<Enemy> enemies;
    private List<Terrain> walls;
    private List<Terrain> platforms;

    public Level(AssetDirectory directory, float units, JsonValue constants) {
        // Create the goal door
        Texture goalTex = directory.getEntry("shared-goal", Texture.class);
        JsonValue goalData = constants.get("goal");
        goalDoor = new Door(units, goalData);
        goalDoor.setTexture(goalTex);
        goalDoor.getObstacle().setName("goal");

        // Create the chameleon (player)
        Texture avatarTex = directory.getEntry("platform-chameleon", Texture.class);
        JsonValue chamData = constants.get("chameleon");
        avatar = new Chameleon(units, chamData);
        avatar.setTexture(avatarTex);
        avatar.createSensor();

        enemies = new ArrayList<>();
        JsonValue enemiesData = constants.get("enemies");
        if (enemiesData != null) {
            Texture enemyTex = directory.getEntry("platform-traci", Texture.class);
            JsonValue enemyPositions = enemiesData.get("positions");
            JsonValue enemyNames = enemiesData.get("names");
            for (int i = 0; i < enemyPositions.size; i++) {
                float[] coords = enemyPositions.get(i).asFloatArray();
                String name = enemyNames.get(i).asString();
                Enemy enemy = new Enemy(coords, name, units, enemiesData);
                enemy.setTexture(enemyTex);
                enemies.add(enemy);
            }
        }

        // Create walls
        walls = new ArrayList<>();
        JsonValue wallsData = constants.get("walls");
        if (wallsData != null) {
            Texture wallTex = directory.getEntry("shared-earth", Texture.class);
            JsonValue wallPositions = wallsData.get("positions");
            for (int i = 0; i < wallPositions.size; i++) {
                float[] coords = wallPositions.get(i).asFloatArray();
                Terrain wall = new Terrain(coords, units, wallsData);
                wall.setTexture(wallTex);
                walls.add(wall);
            }
        }

        // Create platforms
        platforms = new ArrayList<>();
        JsonValue platsData = constants.get("platforms");
        if (platsData != null) {
            Texture platTex = directory.getEntry("shared-earth", Texture.class);
            JsonValue platPositions = platsData.get("positions");
            for (int i = 0; i < platPositions.size; i++) {
                float[] coords = platPositions.get(i).asFloatArray();
                Terrain platform = new Terrain(coords, units, platsData);
                platform.setTexture(platTex);
                platforms.add(platform);
            }
        }
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

    public List<Terrain> getPlatforms() {
        return platforms;
    }
}
