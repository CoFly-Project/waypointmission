package com.convcao.waypointmission;

import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class MissionStartMessage {

    //since Unix epoch
    private Long timestamp;

    private UUID missionId;

    private String destinationSystem;

    private String sourceSystem;

    //m/s
    private float speed;

    //seconds
    private float timeout;

    //m
    private float cornerRadius;

    private float gimbalPitch;

    private List<Waypoint> waypoints;
}
