package com.convcao.waypointmission;

import java.util.List;

import lombok.Data;

@Data
public class MissionStartMessage {

    //since Unix epoch
    private Long timestamp;

    private int missionId;

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
