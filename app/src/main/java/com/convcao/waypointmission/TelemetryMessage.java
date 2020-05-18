package com.convcao.waypointmission;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TelemetryMessage {

    //since Unix epoch
    private Long timestamp;

    private int missionId;

    private String destinationSystem;

    private String sourceSystem;

    private double latitude;

    private double longitude;

    private double altitude;

    private int heading;

}
