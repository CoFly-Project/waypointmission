package com.convcao.waypointmission;

import android.os.AsyncTask;
import android.util.Log;


import dji.common.error.DJIError;
import dji.common.mission.activetrack.ActiveTrackMission;
import dji.common.util.CommonCallbacks;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.activetrack.ActiveTrackOperator;

public class StartActiveTrackMission extends AsyncTask<ActiveTrackMission, Void, Void> {

    private ActiveTrackOperator activeTrackOperator;
    protected static final String TAG = "ActiveTrack";

    public ActiveTrackOperator getActiveTrackOperator(){
        if (activeTrackOperator == null) {
            //MissionControl.getInstance().getWaypointMissionOperator().destroy();
            if (MissionControl.getInstance().getWaypointMissionOperator() != null) {
                Log.i(TAG, "The Mission Control Operator was null!");
                activeTrackOperator = MissionControl.getInstance().getActiveTrackOperator();
            }
        }
        return activeTrackOperator;
    }


    @Override
    protected Void doInBackground(ActiveTrackMission... trackMissions) {

        ActiveTrackMission trackMission = trackMissions[0];

        getActiveTrackOperator().startTracking(trackMission, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                Log.i(TAG, "Start Tracking: " + (djiError == null
                        ? "Success"
                        : djiError.getDescription()));
            }
        });
        return null;
    }

}
