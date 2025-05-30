package chroma.controller;

import com.badlogic.gdx.utils.JsonValue;
import edu.cornell.gdiac.assets.AssetDirectory;

/**
 * LevelSelector is responsible for selecting and loading the JSON file for the current level.
 * Each level is defined in its own JSON file which determines the initialization positions of various objects.
 */
public class LevelSelector {
    // Array of JSON file names for each level.
    private String[] levelFiles;
    // The current level index (starting from 0).
    private int currentLevelIndex;
    // AssetDirectory for loading the JSON files.
    private AssetDirectory directory;

    /**
     * Constructs a new LevelSelector.
     *
     * @param directory the asset directory to load level JSON files.
     */
    public LevelSelector(AssetDirectory directory) {
        this.directory = directory;
        // Define the level JSON files.
        levelFiles = new String[] {
            "level_1","level_3_spray","level_2","level_3_bomb","level_3","alex_level6",
            "alex_level", "alex_level8", "level_grate","level_5", "level_4", "level_12",
            "level_11", "level_8", "level_13", "level_14", "cooper_level", "level_6"
        };
        currentLevelIndex = 0;
    }

    /**
     * Returns the current level number (starting from 1).
     *
     * @return the current level number.
     */
    public int getCurrentLevel() {
        return currentLevelIndex + 1;
    }

    /**
     * Loads and returns the JsonValue for the current level based on the selected JSON file.
     *
     * @return the JsonValue containing level configuration.
     */
    public JsonValue loadCurrentLevel() {
        String levelFile = levelFiles[currentLevelIndex];
        return directory.getEntry(levelFile, JsonValue.class);
    }

    /**
     * Sets the current level number (starting from 1) and loads that level.
     * If levelNumber is out of range, it will be clamped to the available levels.
     *
     * @param levelNumber the level number to load.
     */
    public void setCurrentLevel(int levelNumber) {
        if (levelNumber < 1) {
            currentLevelIndex = 0;
        } else if (levelNumber > levelFiles.length) {
            currentLevelIndex = levelFiles.length - 1;
        } else {
            currentLevelIndex = levelNumber - 1;
        }
    }

    /**
     * Advances to the next level if available.
     *
     * @return true if advanced to the next level; false if there are no more levels.
     */
    public boolean nextLevel() {
        if (currentLevelIndex < levelFiles.length - 1) {
            currentLevelIndex++;
            return true;
        }
        return false;
    }

    /**
     * Returns to the previous level if available.
     *
     * @return true if returned to the previous level; false if there are no more levels.
     */
    public boolean prevLevel() {
        if (currentLevelIndex > 0) {
            currentLevelIndex --;
            return true;
        }
        return false;
    }
    /**
     * Resets the level selector to the first level.
     */
    public void reset() {
        currentLevelIndex = 0;
    }

    public String[] getLevelFiles(){return levelFiles;}
}
