package com.badlogic.gdx.controllers;

import com.badlogic.gdx.utils.Array;

import org.lwjgl.glfw.GLFW;

import bms.player.beatoraja.input.GlfwJoystickState;

// Backwards-compatible shim for Lua skins that call Controllers.getControllers().
public class Controllers {

	private static final Array<ControllerAdapter> controllers = new Array<>();
	private static boolean initialized = false;

	public static Array<ControllerAdapter> getControllers() {
		if (!initialized) {
			refresh();
			initialized = true;
		}
		return controllers;
	}

	public static void refresh() {
		controllers.clear();
		for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
			if (!GLFW.glfwJoystickPresent(jid)) continue;
			String name = GLFW.glfwGetJoystickName(jid);
			if (name == null) continue;
			GlfwJoystickState state = new GlfwJoystickState(jid, name);
			state.refresh();
			controllers.add(new ControllerAdapter(state));
		}
	}

	// Wraps JoystickState to implement the Controller interface Lua skins expect.
	public static class ControllerAdapter implements Controller {
		private final GlfwJoystickState state;

		public ControllerAdapter(GlfwJoystickState state) {
			this.state = state;
		}

		@Override public String getName() { return state.getName(); }
		@Override public boolean getButton(int buttonIndex) { return state.getButton(buttonIndex); }
		@Override public float getAxis(int axisIndex) { return state.getAxis(axisIndex); }
	}
}
