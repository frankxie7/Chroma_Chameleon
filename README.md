# ChromaChameleon

# Todo
1. Implementation of enemies & gameplay prototype AI.
2. Map rebuild.
3. Fix map scaling.
4. Raycast for paint release. Handle intersection with walls.
5. Raycast for enemy vision. Handle intersection with walls and player.
7. Paint tank
8. Win & lose notification
9. Goal region implementation

# Potential New Features:
1. Add paint fade to show when it disappears.
2. Add in new ways to gain/lose paint or another resource.
3. Add and experiment with a new AI enemy that can interact with paint (hide/clean it up).
4. Add a progress bar for filling in goal areas.
5. Add a percentage to tell how much paint is left, increase paint bar size.
6. Integrate avaliable art assets

## Current Classes

### ChromaRoot
- **Role:** The entry point of the game.
- **Responsibilities:**  
  - Initializes the asset manager and loads assets.
  - Sets up the loading screen (via LoadingMode) and transitions between game modes.
  - Manages high-level screen changes (e.g., switching from loading to gameplay).

### LoadingMode
- **Role:** Provides an asynchronous loading screen.
- **Responsibilities:**  
  - Loads essential assets for the game while displaying a progress bar and play button.
  - Ensures that the game remains responsive during asset loading.
  - Transitions to the gameplay mode once all assets are loaded.

### GameplayController
- **Role:** Coordinates the high-level game state during gameplay.
- **Responsibilities:**  
  - Processes player input (using the InputController) and updates game state.
  - Delegates physics simulation to the PhysicsController.
  - Renders the game world and UI messages (win/lose notifications).
  - Manages game flow, such as resetting levels and handling state transitions.

### Level
- **Role:** Constructs the game environment from JSON configuration.
- **Responsibilities:**  
  - Instantiates key game objects such as the goal door, the player (Chameleon), walls, and platforms.
  - Abstracts level construction so that GameplayController can simply add these objects to the physics simulation.
  - Provides getter methods for each major game element.

### PhysicsController
- **Role:** Manages the Box2D physics simulation.
- **Responsibilities:**  
  - Creates and steps a Box2D World with the specified gravity.
  - Adds, updates, and removes physics objects (ObstacleSprites) from the simulation.
  - Implements the `ContactListener` interface for global collision handling.
  - Disposes of physics objects and cleans up the world when necessary.

### Chameleon
- **Role:** Represents the player-controlled character.
- **Responsibilities:**  
  - Encapsulates the physics properties (via a dynamic Box2D body) and visual representation of the chameleon.
  - Processes input to apply forces for movement.
  - Handles state changes such as shooting and damping of motion.

### Door
- **Role:** Represents the win condition (goal) in the game.
- **Responsibilities:**  
  - Acts as a sensor to detect when the player has reached the goal.
  - Uses a static Box2D body to define its physical presence and collision behavior.
  - Provides a textured mesh that visually represents the door in the game world.

### Terrain
- **Role:** Represents static level elements such as walls and platforms.
- **Responsibilities:**  
  - Creates a polygon-based physics body from a set of points.
  - Uses a tiled mesh to apply textures on non-rectangular shapes.
  - Acts as a static object for collision detection in the game environment.

### InputController
- **Role:** Processes and buffers input from both keyboard and gamepad.
- **Responsibilities:**  
  - Captures user input, converts it into directional movement and action commands.
  - Provides methods to retrieve current movement values and button states.
  - Syncs input state each frame so that GameplayController can apply forces to game objects.
