package com.convcao.waypointmission.dto;


public class ScreenShotResource {

    private double cameraLat;
    private double cameraLon;
    private float cameraAlt;
    private int cameraRotation;
    private float cameraVelocityX;
    private float cameraVelocityY;
    private float cameraVelocityZ;
    private byte[] imageJPEG;

    public ScreenShotResource(double cameraLat, double cameraLon, float cameraAlt, int cameraRotation, float cameraVelocityX, float cameraVelocityY, float cameraVelocityZ, byte[] imageJPEG) {
        this.cameraLat = cameraLat;
        this.cameraLon = cameraLon;
        this.cameraAlt = cameraAlt;
        this.cameraRotation = cameraRotation;
        this.cameraVelocityX = cameraVelocityX;
        this.cameraVelocityY = cameraVelocityY;
        this.cameraVelocityZ = cameraVelocityZ;
        this.imageJPEG = imageJPEG;
    }

    public double getCameraLat() {
        return cameraLat;
    }

    public double getCameraLon() {
        return cameraLon;
    }

    public float getCameraAlt() {
        return cameraAlt;
    }

    public int getCameraRotation() {
        return cameraRotation;
    }

    public float getCameraVelocityX() {
        return cameraVelocityX;
    }

    public float getCameraVelocityY() {
        return cameraVelocityY;
    }

    public float getCameraVelocityZ() {
        return cameraVelocityZ;
    }

    public byte[] getImageJPEG() {
        return imageJPEG;
    }
}
