package com.badlogic.gdx.controllers;

// Backwards-compatible shim for Lua skins that reference this class.
public interface Controller {
	String getName();
	boolean getButton(int buttonIndex);
	float getAxis(int axisIndex);
}
