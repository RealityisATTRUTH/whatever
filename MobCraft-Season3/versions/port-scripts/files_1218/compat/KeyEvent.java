package com.reality.mobcraft.compat;

/** A key press as 1.21.9 hands it over; 1.21.8 passes the three values separately. */
public record KeyEvent(int key, int scancode, int modifiers) {}
