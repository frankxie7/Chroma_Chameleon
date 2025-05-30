/*
 * InputController.java
 *
 * This class buffers in input from the devices and converts it into its
 * semantic meaning. If your game had an option that allows the player to
 * remap the control keys, you would store this information in this class.
 * That way, the main GameEngine does not have to keep track of the current
 * key mapping.
 *
 * Based on the original PhysicsDemo Lab by Don Holden, 2007
 *
 * Author:  Walker M. White
 * Version: 2/8/2025
 */
package chroma.controller;

import chroma.model.Level;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import edu.cornell.gdiac.util.Controllers;
import edu.cornell.gdiac.util.XBoxController;

/**
 * Class for reading player input.
 *
 * This supports both a keyboard and X-Box controller. In previous solutions,
 * we only detected the X-Box controller on start-up. This class allows us to
 * hot-swap in a controller on the fly.
 */
public class InputController {
    // Sensitivity for moving crosshair with gameplay
    private static final float GP_ACCELERATE = 1.0f;
    private static final float GP_MAX_SPEED  = 10.0f;
    private static final float GP_THRESHOLD  = 0.01f;

    /** The singleton instance of the input controller */
    private static InputController theController = null;

    /**
     * Returns the singleton instance of the input controller
     *
     * @return the singleton instance of the input controller
     */
    public static InputController getInstance() {
        if (theController == null) {
            theController = new InputController();
        }
        return theController;
    }

    // Fields to manage buttons
    /** Whether the reset button was pressed. */
    private boolean resetPressed;
    private boolean resetPrevious;
    /** Whether the button to advanced worlds was pressed. */
    private boolean nextPressed;
    private boolean nextPrevious;
    /** Whether the button to step back worlds was pressed. */
    private boolean prevPressed;
    private boolean prevPrevious;
    /** Whether the button to main menu was pressed. */
    private boolean menuPressed;
    private boolean menuPrevious;
    /** Whether the button to pause was pressed. */
    private boolean pausePressed;
    private boolean pausePrevious;
    /** Whether the primary action button was pressed. */
    private boolean primePressed;
    private boolean primePrevious;
    /** Whether the secondary action button was pressed. */
    private boolean secondPressed;
    private boolean secondPrevious;

    /** Whether the debug toggle was pressed. */
    private boolean debugPressed;
    private boolean debugPrevious;
    /** Whether the exit button was pressed. */
    private boolean exitPressed;
    private boolean exitPrevious;
    /** Whether the left shift button was pressed */
    private boolean shiftPressed;

    /** How much did we move horizontally? */
    private float horizontal;
    /** How much did we move vertically? */
    private float vertical;
    /** The crosshair position (for raddoll) */
    private Vector2 crosshair;
    /** The crosshair cache (for using as a return value) */
    private Vector2 crosscache;
    /** For the gamepad crosshair control */
    private float momentum;

    /** The mouse position*/
    private Vector2 mousePos;
    /** The mousePos cache*/
    private Vector2 mousecache;

    /** New Bomb: Whether the skill‑cast button was pressed. */
    private boolean skillPressed;
    private boolean skillPrevious;

    /** Whether the mouse left button was pressed. */
    private boolean leftPressed;
    private boolean leftPrevious;
    /** Whether the mouse right button was pressed. */
    private boolean rightPressed;
    private boolean rightPrevious;



    /** An X-Box controller (if it is connected) */
    XBoxController xbox;

    /**
     * Returns the amount of sideways movement.
     *
     * -1 = left, 1 = right, 0 = still
     *
     * @return the amount of sideways movement.
     */
    public float getHorizontal() {
        return horizontal;
    }

    /**
     * Returns the amount of vertical movement.
     *
     * -1 = down, 1 = up, 0 = still
     *
     * @return the amount of vertical movement.
     */
    public float getVertical() {
        return vertical;
    }

    /**
     * Returns the current position of the crosshairs on the screen.
     *
     * This value does not return the actual reference to the crosshairs
     * position. That way this method can be called multiple times without any
     * fear that the position has been corrupted. However, it does return the
     * same object each time. So if you modify the object, the object will be
     * reset in a subsequent call to this getter.
     *
     * @return the current position of the crosshairs on the screen.
     */
    public Vector2 getCrossHair() {
        return crosscache.set(crosshair);
    }

    public Vector2 getMousePos() {
        return mousecache.set(mousePos);
    }

    /**
     * Returns true if the primary action button was pressed.
     *
     * This is a one-press button. It only returns true at the moment it was
     * pressed, and returns false at any frame afterwards.
     *
     * @return true if the primary action button was pressed.
     */
    public boolean didPrimary() {
        return primePressed && !primePrevious;
    }

    /**
     * Returns true if the secondary action button was pressed.
     *
     * This is a one-press button. It only returns true at the moment it was
     * pressed, and returns false at any frame afterwards.
     *
     * @return true if the secondary action button was pressed.
     */
    public boolean didSecondary() {
        return secondPressed && !secondPrevious;
    }



    /**
     * Returns true if the reset button was pressed.
     *
     * @return true if the reset button was pressed.
     */
    public boolean didReset() {
        return resetPressed && !resetPrevious;
    }

    /**
     * Returns true if the player wants to go to the next level.
     *
     * @return true if the player wants to go to the next level.
     */
    public boolean didAdvance() {
        return nextPressed && !nextPrevious;
    }

    /**
     * Returns true if the player wants to go to the previous level.
     *
     * @return true if the player wants to go to the previous level.
     */
    public boolean didRetreat() {
        return prevPressed && !prevPrevious;
    }

    /**
     * Returns true if the player wants to go to the main menu.
     *
     * @return true if the player wants to go to the main menu.
     */
    public boolean didMenu() {
        return menuPressed && !menuPrevious;
    }

    /**
     * Returns true if the player wants to pause the level.
     *
     * @return true if the player wants to pause the level.
     */
    public boolean didPause() {
        return pausePressed && !pausePrevious;
    }

    /**
     * Returns true if the player wants to go toggle the debug mode.
     *
     * @return true if the player wants to go toggle the debug mode.
     */
    public boolean didDebug() {
        return debugPressed && !debugPrevious;
    }

    /**
     * Returns true if the exit button was pressed.
     *
     * @return true if the exit button was pressed.
     */
    public boolean didExit() {
        return exitPressed && !exitPrevious;
    }

    /**
     * Returns true if the left shift button was pressed.
     * */
    public boolean didAim() {
        return shiftPressed;
    }

    /**
     * Returns true if the skill‑cast key was pressed this frame.
     */
    public boolean didSkill() {
        return skillPressed && !skillPrevious;
    }
    /** true only on the frame the mouse LEFT button is pressed */
    public boolean didLeftClick() {
        return leftPressed && !leftPrevious;
    }

    /** true only on the frame the mouse RIGHT button is pressed */
    public boolean didRightClick() {
        return rightPressed && !rightPrevious;
    }
    /** true while the skill‑cast key (E) is being held down */
    public boolean isSkillHeld() {
        return skillPressed;          // sustained
    }



    /**
     * Creates a new input controller
     *
     * The input controller attempts to connect to the X-Box controller at
     * device 0, if it exists. Otherwise, it falls back to the keyboard
     * control.
     */
    public InputController() {
        // If we have a game-pad for id, then use it.
        Array<XBoxController> controllers = Controllers.get().getXBoxControllers();
        if (controllers.size > 0) {
            xbox = controllers.get( 0 );
        } else {
            xbox = null;
        }
        crosshair = new Vector2();
        crosscache = new Vector2();
        mousePos = new Vector2();
        mousecache = new Vector2();
    }

    /**
     * Syncs the keyboard to the current animation frame.
     *
     * The method provides both the input bounds and the drawing scale. It needs
     * the drawing scale to convert screen coordinates to world coordinates.
     * The bounds are for the crosshair. They cannot go outside of this zone.
     *
     * @param bounds The input bounds for the crosshair.
     * @param scale  The drawing scale
     */
    public void sync(Rectangle bounds, Vector2 scale) {
        // Copy state from last animation frame
        // Helps us ignore buttons that are held down
        primePrevious  = primePressed;
        secondPrevious = secondPressed;
        resetPrevious  = resetPressed;
        debugPrevious  = debugPressed;
        exitPrevious = exitPressed;
        nextPrevious = nextPressed;
        prevPrevious = prevPressed;
        menuPrevious = menuPressed;
        pausePrevious = pausePressed;
        skillPrevious = skillPressed;
        leftPrevious  = leftPressed;
        rightPrevious = rightPressed;



        // Check to see if a GamePad is connected
        if (xbox != null && xbox.isConnected()) {
            readGamepad(bounds, scale);
            readKeyboard(bounds, scale, true); // Read as a back-up
        } else {
            readKeyboard(bounds, scale, false);
        }
    }

    /**
     * Reads input from an X-Box controller connected to this computer.
     *
     * The method provides both the input bounds and the drawing scale. It needs
     * the drawing scale to convert screen coordinates to world coordinates. The
     * bounds are for the crosshair. They cannot go outside of this zone.
     *
     * @param bounds The input bounds for the crosshair.
     * @param scale  The drawing scale
     */
    private void readGamepad(Rectangle bounds, Vector2 scale) {
        resetPressed = xbox.getStart();
        exitPressed  = xbox.getBack();
        nextPressed  = xbox.getRBumper();
        prevPressed  = xbox.getLBumper();
        primePressed = xbox.getA();
        debugPressed  = xbox.getY();

        // Increase animation frame, but only if trying to move
        horizontal = xbox.getLeftX();
        vertical   = xbox.getLeftY();
        secondPressed = xbox.getRightTrigger() > 0.6f;


        crosscache.set(xbox.getLeftX(), xbox.getLeftY());
        if (crosscache.len2() > GP_THRESHOLD) {
            momentum += GP_ACCELERATE;
            momentum = Math.min(momentum, GP_MAX_SPEED);
            crosscache.scl(momentum);
            crosscache.scl(1/scale.x,1/scale.y);
            crosshair.add(crosscache);
        } else {
            momentum = 0;
        }
        clampPosition(bounds);
    }

    /**
     * Reads input from the keyboard.
     *
     * This controller reads from the keyboard regardless of whether or not an
     * X-Box controller is connected. However, if a controller is connected,
     * this method gives priority to the X-Box controller.
     *
     * @param secondary true if the keyboard should give priority to a gamepad
     */
    private void readKeyboard(Rectangle bounds, Vector2 scale, boolean secondary) {
        // Give priority to gamepad results
        resetPressed = (secondary && resetPressed) || (Gdx.input.isKeyPressed(Input.Keys.R));
        debugPressed = (secondary && debugPressed) || (Gdx.input.isKeyPressed(Input.Keys.F));
        primePressed = (secondary && primePressed) || (Gdx.input.isKeyPressed(Input.Keys.UP));
        secondPressed = (secondary && secondPressed) || (Gdx.input.isKeyPressed(Input.Keys.SPACE));
//        prevPressed = (secondary && prevPressed) || (Gdx.input.isKeyPressed(Input.Keys.P));
        nextPressed = (secondary && nextPressed) || (Gdx.input.isKeyPressed(Input.Keys.N));
        menuPressed = (secondary && menuPressed) || (Gdx.input.isKeyPressed(Input.Keys.M));
        pausePressed = (secondary && pausePressed) || (Gdx.input.isKeyPressed(Input.Keys.ESCAPE));
        exitPressed  = (secondary && exitPressed) || (Gdx.input.isKeyPressed(Input.Keys.ESCAPE));
        shiftPressed = (secondary && shiftPressed) || (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT));
        skillPressed = (secondary && skillPressed) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);
        leftPressed  = (secondary && leftPressed)  || Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        rightPressed = (secondary && rightPressed) || Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT);


        // Directional controls
        horizontal = (secondary ? horizontal : 0.0f);
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            horizontal += 1.0f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            horizontal -= 1.0f;
        }

        vertical = (secondary ? vertical : 0.0f);
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            vertical += 1.0f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            vertical -= 1.0f;
        }

        crosshair.set(Gdx.input.getX(), Gdx.input.getY());
        crosshair.scl(1/scale.x,-1/scale.y);
        crosshair.y += bounds.height;

        mousePos.set(Gdx.input.getX(), Gdx.input.getY());
        mousePos.scl(1/scale.x,-1/scale.y);
        mousePos.y += bounds.height;
        clampPosition(bounds);
    }

    /** true while the mouse left button is being held down */
    public boolean isLeftHeld() {
        return Gdx.input.isButtonPressed(Input.Buttons.LEFT);
    }


    /**
     * Clamps the cursor position so that it does not go outside the window
     *
     * While this is not usually a problem with mouse control, this is critical
     * for the gamepad controls.
     */
    private void clampPosition(Rectangle bounds) {
        crosshair.x = Math.max(bounds.x, Math.min(bounds.x+bounds.width, crosshair.x));
        crosshair.y = Math.max(bounds.y, Math.min(bounds.y+bounds.height, crosshair.y));
        mousePos.x = Math.max(bounds.x, Math.min(bounds.x+bounds.width, mousePos.x));
        mousePos.y = Math.max(bounds.y, Math.min(bounds.y+bounds.height, mousePos.y));
    }
}
