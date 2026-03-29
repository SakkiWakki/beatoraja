package bms.player.beatoraja.input;

// Abstraction over joystick button/axis state for testability.
public interface JoystickState {
	String getName();
	boolean getButton(int index);
	float getAxis(int index);
}
