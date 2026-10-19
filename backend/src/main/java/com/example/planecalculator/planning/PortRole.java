package com.example.planecalculator.planning;

public enum PortRole {
    CAMERA,
    WORKING_1,
    WORKING_2,
    WORKING_3,
    ASSISTANT;

    public boolean robotic() {
        return this != ASSISTANT;
    }
}
