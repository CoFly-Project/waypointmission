package com.convcao.waypointmission;

import android.os.AsyncTask;
import android.util.Log;

import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.sdkmanager.DJISDKManager;

public class StopWaypointNavigation extends AsyncTask<Void, Void, Void> {

    private WaypointMissionOperator instance;
    private WaypointMissionStatus status;

    private final int attempts = 20;

    protected static final String TAG = "WaypointNavigationClass";


    protected enum WaypointMissionStatus {STOPPED, FAIL_TO_STOP, READY}

    public StopWaypointNavigation() {
        status = WaypointMissionStatus.READY;
    }

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            if (DJISDKManager.getInstance().getMissionControl() != null) {
                instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
            }
        }
        return instance;
    }

    @Override
    protected Void doInBackground(Void...n){
        status = WaypointMissionStatus.READY;
        Log.i(TAG, "Stop previous mission");
        int current_attempt = 1;
        while (status != WaypointMissionStatus.STOPPED && current_attempt<=attempts) {
            stopExecution();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                android.util.Log.d("Waypoint Mission", ex.toString());
            }
            current_attempt++;
        }
        Log.i(TAG, (current_attempt-1)+" attempts were needed for this operation");
        return null;
    }

    private void stopExecution() {

        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                //setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
                if (error == null) {
                    status = WaypointMissionStatus.STOPPED;
                    Log.i(TAG, "(1/1) Mission stopped successfully!");
                }else{
                    status = WaypointMissionStatus.FAIL_TO_STOP;
                }
            }
        });
    }

}
