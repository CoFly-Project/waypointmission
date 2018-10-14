package com.convcao.waypointmission;


import android.os.AsyncTask;
import android.support.annotation.Nullable;

import dji.common.error.DJIError;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.sdkmanager.DJISDKManager;

public class WaypointNavigation { //extends AsyncTask<Waypoint,Void, Void>

    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction;
    private WaypointMissionHeadingMode mHeadingMode;
    private FlightAssistant FA;
    private WaypointMissionStatus status;


    protected enum WaypointMissionStatus {ACTIVE, READY, FINISHED, INACTIVE, STOPPED, FAIL_TO_UPLOAD, FAIL_TO_START}

    public WaypointNavigation() {
        mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
        mHeadingMode = WaypointMissionHeadingMode.AUTO; //TODO fix me in the future
        FA = new FlightAssistant();
        status = WaypointMissionStatus.INACTIVE;
    }

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            if (DJISDKManager.getInstance().getMissionControl() != null) {
                instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
            }
        }
        return instance;
    }


    public void Goto(Waypoint WPc, Waypoint WPe, float speed) {
        status = WaypointMissionStatus.READY;

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
        while (error != null) {
            error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    //setResultToToast("Mission upload successfully!");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        android.util.Log.d("Waypoint Mission", ex.toString());
                    }
                    //getWaypointMissionOperator().addListener(eventNotificationListener);
                    getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError error) {
                            //setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
                            if (error != null) {
                                status = WaypointMissionStatus.FAIL_TO_START;
                            }
                        }
                    });
                } else {
                    //setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(null);
                }
            }
        });

    }

    public void stopWaypointMission() {
        //getWaypointMissionOperator().removeListener(eventNotificationListener);
        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                //setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });
        //if (markerWP!=null) {
        //    markerWP.remove();
        //}
        status = WaypointMissionStatus.STOPPED;
    }

    public WaypointMissionStatus getStatus() {
        return status;
    }

    /*

    private WaypointMissionOperatorListener eventNotificationListener = new WaypointMissionOperatorListener() {
        @Override
        public void onDownloadUpdate(WaypointMissionDownloadEvent downloadEvent) {

        }

        @Override
        public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) {

        }

        @Override
        public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {

        }

        @Override
        public void onExecutionStart() {

        }

        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            //setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
            //if (markerWP != null) {
            //    markerWP.remove();
            //}
            status = WaypointMissionStatus.FINISHED;
        }

    };

    @Override
    protected Void doInBackground(Waypoint...WPS){
        Goto(WPS[0], WPS[1], 10.0f);
        return null;
    }
    */

}
