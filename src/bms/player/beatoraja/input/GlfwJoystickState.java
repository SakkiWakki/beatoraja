package bms.player.beatoraja.input;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import org.lwjgl.glfw.GLFW;

// Thread-safe GLFW joystick snapshot. refresh() on GL thread, read from any thread.
public class GlfwJoystickState implements JoystickState {

	private final int jid;
	private final String name;
	private volatile boolean[] buttons = new boolean[0];
	private volatile float[] axes = new float[0];
	private volatile boolean connected = true;

	public GlfwJoystickState(int jid, String name) {
		this.jid = jid;
		this.name = name;
	}

	// Must be called from the GL/main thread only.
	public void refresh() {
		if (!GLFW.glfwJoystickPresent(jid)) {
			connected = false;
			return;
		}
		connected = true;

		ByteBuffer btnBuf = GLFW.glfwGetJoystickButtons(jid);
		if (btnBuf != null) {
			boolean[] b = new boolean[btnBuf.remaining()];
			for (int i = 0; i < b.length; i++) {
				b[i] = btnBuf.get(i) == GLFW.GLFW_PRESS;
			}
			buttons = b;
		}

		FloatBuffer axBuf = GLFW.glfwGetJoystickAxes(jid);
		if (axBuf != null) {
			float[] a = new float[axBuf.remaining()];
			for (int i = 0; i < a.length; i++) {
				a[i] = axBuf.get(i);
			}
			axes = a;
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean getButton(int index) {
		boolean[] b = buttons;
		return index >= 0 && index < b.length && b[index];
	}

	@Override
	public float getAxis(int index) {
		float[] a = axes;
		return index >= 0 && index < a.length ? a[index] : 0;
	}

	public boolean isConnected() {
		return connected;
	}

	public int getJid() {
		return jid;
	}
}
