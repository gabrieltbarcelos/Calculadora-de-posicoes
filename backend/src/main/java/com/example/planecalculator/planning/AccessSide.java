package com.example.planecalculator.planning;

public enum AccessSide {
    RIGHT,
    LEFT,
    AUTO;

    public int sign() {
        return this == LEFT ? -1 : 1;
    }
}
