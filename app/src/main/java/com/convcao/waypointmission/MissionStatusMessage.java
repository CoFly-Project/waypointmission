package com.convcao.waypointmission;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class MissionStatusMessage {

    //since Unix epoch
    @NonNull
    private Long timestamp;

    private UUID missionId;

    private String destinationSystem;

    @NonNull
    private String sourceSystem;

    private MissionStatus missionStatus;
}
