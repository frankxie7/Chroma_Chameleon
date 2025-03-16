package edu.cornell.cis3152.shipdemo;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.AssetDirectory;
import java.util.ArrayList;
import java.util.List;

public class Level {
    private Door goalDoor;
    private Chameleon avatar;
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

    public List<Terrain> getWalls() {
        return walls;
    }

    public List<Terrain> getPlatforms() {
        return platforms;
    }
}
