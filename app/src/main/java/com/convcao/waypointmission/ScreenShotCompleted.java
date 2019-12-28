package com.convcao.waypointmission;

import com.convcao.waypointmission.dto.ScreenShotResource;

public interface ScreenShotCompleted {
    // Define data you like to return from AysncTask
    public void onTaskComplete(ScreenShotResource result);
}
