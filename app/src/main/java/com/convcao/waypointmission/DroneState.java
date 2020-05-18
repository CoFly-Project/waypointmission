package com.convcao.waypointmission;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DroneState {

    double latitude;
    double longitude;
    double altitude;
    int heading;
    float gimbalPitch;
    byte[] image;

    private static DroneState instance = null;

    private DroneState(){
    }

    public static DroneState getInstance() {

        if(instance == null) {
            instance = new DroneState();
            instance.setLatitude(38.023349);
            instance.setLongitude(23.744271);
            instance.setAltitude(2.0f);
            instance.setGimbalPitch(-90);
            instance.setHeading(-1);
        }
        return instance;
    }
}
