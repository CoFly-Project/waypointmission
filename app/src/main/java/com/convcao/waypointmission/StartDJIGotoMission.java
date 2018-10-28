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


    protected enum WaypointMissionStatus {ACTIVE, READY, FINISHED, INACTIVE, STOPPED, FAIL_TO_STOP, FAIL_TO_UPLOAD, FAIL_TO_START}


    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction;
    private WaypointMissionHeadingMode mHeadingMode;
    private FlightAssistant FA;
    private float speed;
    private final int attempts = 20;
    private WaypointMissionStatus status;

    protected static final String TAG = "StartDJIGotoMission";

    public StartDJIGotoMission(float speed) {
        mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
        mHeadingMode = WaypointMissionHeadingMode.AUTO; //TODO fix me in the future
        FA = new FlightAssistant();
        status = WaypointMissionStatus.INACTIVE;
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

        while(status!=WaypointMissionStatus.ACTIVE) {
            stopWaypointMission();
            Goto(WPS[0], WPS[1], speed);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Log.d(TAG, ex.toString());
            }
        }
        return null;
    }




    public void Goto(Waypoint WPc, Waypoint WPe, float speed) {
        Log.i(TAG, "GOTO --> " + WPc.coordinate.toString());
        FA.setCollisionAvoidanceEnabled(true, null);
        FA.setActiveObstacleAvoidanceEnabled(true, null);

        WaypointMission.Builder waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                .headingMode(mHeadingMode)
                .autoFlightSpeed(speed)
                .maxFlightSpeed(speed)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL).addWaypoint(WPc).addWaypoint(WPe);
        //.addWaypoint(fakeWP)
        //.waypointCount(2);

        DJIError error = DJIError.COMMON_UNKNOWN;
        status = WaypointMissionStatus.FAIL_TO_UPLOAD;
        int current_attempt = 1;
        while (error != null && current_attempt <= attempts) {
            error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            current_attempt++;
        }
        Log.i(TAG, (current_attempt - 1) + " attempts were needed for this operation");
        Log.i(TAG, "(1/3) Mission loaded successfully!");

        if (error == null) {
            uploadWayPointMission();
        }else{
            Log.i(TAG, error.getDescription());
        }
    }

    private void uploadWayPointMission() {
        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    //setResultToToast("Mission upload successfully!");
                    Log.i(TAG, "(2/3) Mission uploaded successfully!");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        android.util.Log.d("Waypoint Mission", ex.toString());
                    }
                    status = WaypointMissionStatus.FAIL_TO_START;
                    int current_attempt = 1;
                    while (status == WaypointMissionStatus.FAIL_TO_START && current_attempt <= attempts) {
                        startWaypointMission();
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ex) {
                            android.util.Log.d("Waypoint Mission", ex.toString());
                        }
                        current_attempt++;
                    }
                    Log.i(TAG, (current_attempt - 1) + " attempts were needed for this operation");
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
                    status = WaypointMissionStatus.ACTIVE;
                    Log.i(TAG, "(3/3) Mission started successfully!");
                } else {
                    Log.i(TAG, error.getDescription());
                    status = WaypointMissionStatus.FAIL_TO_START;
                }
            }
        });
    }


    public void stopWaypointMission() {
        if (status != WaypointMissionStatus.STOPPED) {
            Log.i(TAG, "Stop previous mission");
            int current_attempt = 1;
            while (status != WaypointMissionStatus.STOPPED && current_attempt <= attempts) {
                stopExecution();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    android.util.Log.d("Waypoint Mission", ex.toString());
                }
                current_attempt++;
            }
            Log.i(TAG, (current_attempt - 1) + " attempts were needed for this operation");
        }
    }

    private void stopExecution() {

        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                //setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
                if (error == null) {
                    status = WaypointMissionStatus.STOPPED;
                    Log.i(TAG, "(1/1) Mission stopped successfully!");
                } else {
                    status = WaypointMissionStatus.FAIL_TO_STOP;
                }
            }
        });
    }


}
