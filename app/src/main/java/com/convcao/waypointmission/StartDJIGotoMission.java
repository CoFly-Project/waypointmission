package com.convcao.waypointmission;


import android.os.AsyncTask;
import android.util.Log;

import dji.common.error.DJIError;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.sdkmanager.DJISDKManager;

public class StartDJIGotoMission extends AsyncTask<Waypoint, Void, Void> {

    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction;
    private WaypointMissionHeadingMode mHeadingMode;
    private FlightAssistant FA;
    private WaypointNavigation.WaypointMissionStatus status;
    private float speed;

    protected static final String TAG = "StartDJIGotoMission";

    public StartDJIGotoMission(float speed) {
        mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
        mHeadingMode = WaypointMissionHeadingMode.AUTO; //TODO fix me in the future
        FA = new FlightAssistant();
        status = WaypointNavigation.WaypointMissionStatus.INACTIVE;
        this.speed = speed;
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
    protected Void doInBackground(Waypoint... WPS) {

        Log.i(TAG, "GOTO --> " + WPS[0].coordinate.toString());
        FA.setCollisionAvoidanceEnabled(true, null);
        FA.setActiveObstacleAvoidanceEnabled(true, null);

        WaypointMission.Builder waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                .headingMode(mHeadingMode)
                .autoFlightSpeed(speed)
                .maxFlightSpeed(speed)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL).addWaypoint(WPS[0]).addWaypoint(WPS[1]);
        //.addWaypoint(fakeWP)
        //.waypointCount(2);

        DJIError error = DJIError.COMMON_UNKNOWN;
        while (error != null ) {
            error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return null;
    }


    private void uploadWayPointMission() {
        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    //setResultToToast("Mission upload successfully!");
                    Log.i(TAG, "(2/3) Mission uploaded successfully!");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        android.util.Log.d("Waypoint Mission", ex.toString());
                    }
                    status = WaypointNavigation.WaypointMissionStatus.FAIL_TO_START;
                    while (status == WaypointNavigation.WaypointMissionStatus.FAIL_TO_START) {
                        startWaypointMission();
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                            android.util.Log.d("Waypoint Mission", ex.toString());
                        }
                    }
                } else {
                    //setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(null);
                }
            }
        });
    }

    private void startWaypointMission() {
        //addListener();
        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                //setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
                if (error == null) {
                    status = WaypointNavigation.WaypointMissionStatus.ACTIVE;
                    Log.i(TAG, "(3/3) Mission started successfully!");
                } else {
                    status = WaypointNavigation.WaypointMissionStatus.FAIL_TO_START;
                }
            }
        });
    }

    protected void onCancelled(){
        Log.i(TAG, "Stop previous mission");
        while (status != WaypointNavigation.WaypointMissionStatus.STOPPED) {
            stopExecution();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                android.util.Log.d("Waypoint Mission", ex.toString());
            }
        }
    }

    private void stopExecution() {

        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                //setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
                if (error == null) {
                    status = WaypointNavigation.WaypointMissionStatus.STOPPED;
                    Log.i(TAG, "(1/1) Mission stopped successfully!");
                }
            }
        });
    }

}
