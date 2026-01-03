package com.firekid.screentracker;

public class TrackingManager {

    private static final float SMOOTHING_FACTOR = 0.3f;
    private static final int MIN_PAN_THRESHOLD = 10;

    private int targetMidpointX = -1;
    private int targetMidpointY = -1;

    private float currentOffsetX = 0;
    private float currentOffsetY = 0;

    private boolean hasMidpoint = false;

    public void setMidpoint(int x, int y) {
        this.targetMidpointX = x;
        this.targetMidpointY = y;
        this.hasMidpoint = true;
    }

    public boolean hasMidpoint() {
        return hasMidpoint;
    }

    public int[] getMidpoint() {
        if (!hasMidpoint) {
            return null;
        }
        return new int[]{targetMidpointX, targetMidpointY};
    }

    public int[] calculatePanOffset(int objectX, int objectY) {
        if (targetMidpointX < 0 || targetMidpointY < 0) {
            return new int[]{0, 0};
        }

        float targetOffsetX = targetMidpointX - objectX;
        float targetOffsetY = targetMidpointY - objectY;

        currentOffsetX += (targetOffsetX - currentOffsetX) * SMOOTHING_FACTOR;
        currentOffsetY += (targetOffsetY - currentOffsetY) * SMOOTHING_FACTOR;

        int finalOffsetX = 0;
        int finalOffsetY = 0;

        if (Math.abs(currentOffsetX) > MIN_PAN_THRESHOLD) {
            finalOffsetX = (int) currentOffsetX;
        }

        if (Math.abs(currentOffsetY) > MIN_PAN_THRESHOLD) {
            finalOffsetY = (int) currentOffsetY;
        }

        return new int[]{finalOffsetX, finalOffsetY};
    }

    public void reset() {
        currentOffsetX = 0;
        currentOffsetY = 0;
        hasMidpoint = false;
    }
}
