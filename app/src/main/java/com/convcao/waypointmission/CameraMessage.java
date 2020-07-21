package com.convcao.waypointmission;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CameraMessage {

    //since Unix epoch
    @NonNull
    private Long timestamp;

    private int imageId;

    private String destinationSystem;

    @NonNull
    private String sourceSystem;

    private double latitude;

    private double longitude;

    private double altitude;

    private float velocityX;

    private float velocityY;

    private float velocityZ;

    private int heading;

    private float gimbalPitch;

    private byte[] image;

}
