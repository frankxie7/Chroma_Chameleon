# ChromaChameleon

# Level‑Building Quick Reference

## Tileset Update

- The tileset now uses **16 px × 16 px** tiles.  
- To add a new map:  
  1. **Drag** the file exported from **Tiled** into the project.  
  2. **Update** the path in `asset.json`.

  > `LevelSelector` reads this path. Until `LevelSelector` is finished, you’ll need to change the path manually each time you switch maps.

---

## Accessing Tiled Layers

```java
JsonValue goalTileData = findLayer(constants, "goal");
```

- The `"goal"` string **must exactly match** the layer name in Tiled (e.g., in `test_16_2.json`).

---

## Tile Classes

| Class | Role |
|-------|------|
| `Terrain.java` | A tile **with** physics properties |
| `BackgroundTile.java` | A purely **visual** tile (no physics) |

---

## Level JSON Structure

```java
JsonValue constants       = selector.loadCurrentLevel();
JsonValue globalConstants = directory.getEntry("platform-constants", JsonValue.class);
```

- Place **only data that changes per level** (enemy positions, patrol paths, paint limits, etc.) in the level‑specific JSON file.  
- **Do not** spawn a `Terrain` tile and an enemy at the same coordinates—this crashes the game.

---

## TODOs for Next Wednesday

- **Beilin** — implement `LevelSelector`; add menu & level‑selector art assets  
- **Cooper** — set up enemies via JSON  
- **Frank** — implement **Grate** and make it editable in Tiled  
- **Alex** — improve machine coloring  
- **Brian** — balance & refine the **bomb** mechanic; integrate the **spray** asset  

## TODO for This Saturday
- **Everyone** — update your architecture‑spec section per feedback and current code; design new levels  

---
