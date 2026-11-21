package com.reality.mobcraft.compat;

/** A mouse press as 1.21.9 hands it over; 1.21.8 passes the three values separately. */
public record MouseButtonEvent(double x, double y, int button) {}
