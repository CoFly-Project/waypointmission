package com.convcao.waypointmission;


import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.util.CommonCallbacks;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class StartDJIMission extends AsyncTask<ArrayList<Waypoint>, Void, Void> {


    protected enum WaypointMissionStatus {
        ACTIVE, READY, FINISHED, INACTIVE, STOPPED, LOADED, UPLOADED,
        FAIL_TO_STOP, FAIL_TO_LOAD, FAIL_TO_UPLOAD, FAIL_TO_START
    }

    private DispatchMessage dispatchMessage;

    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction;
    private WaypointMissionHeadingMode mHeadingMode;
    private WaypointMissionFlightPathMode missionFlightPathMode;
    private FlightAssistant FA;
    private float speed, timeout;
    protected boolean locked = false;
    private final int MAX_ATTEMPTS = 35;
    private WaypointMissionStatus status;
    private Handler handler;


    private Map<String, Schema> schemas;
    private String droneCanonicalName;
    private String server_ip;
    private int server_port;
    private int connection_time_out;
    //private Waypoint WP1, WP2;

    //private AtomicBoolean waypointNotReached=new AtomicBoolean(true);
    //private AtomicBoolean stopped = new AtomicBoolean(false);
    private boolean imageTaken = false;

    protected static final String TAG = "StartDJIMission";
    protected static final String TAGtime = "StartDJIWPTrackTime";

    public StartDJIMission(float timeout, float speed, WaypointMissionHeadingMode mHeadingMode,
                           WaypointMissionFlightPathMode missionFlightPathMode, Map<String, Schema> schemas,
                           String droneCanonicalName, String server_ip, int server_port, int connection_time_out) {
        this.mHeadingMode = mHeadingMode;
        this.mFinishedAction = WaypointMissionFinishedAction.NO_ACTION; //TODO fix me in the future
        this.missionFlightPathMode = missionFlightPathMode;
        this.FA = new FlightAssistant();
        this.status = WaypointMissionStatus.INACTIVE;
        this.speed = speed;
        this.timeout = timeout;
        this.handler = new Handler();
        this.schemas = schemas;
        this.droneCanonicalName = droneCanonicalName;
        this.server_ip = server_ip;
        this.server_port = server_port;
        this.connection_time_out = connection_time_out;
    }

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            //MissionControl.getInstance().getWaypointMissionOperator().destroy();
            if (MissionControl.getInstance().getWaypointMissionOperator() != null) {
                Log.i(TAG, "The Mission Control Operator was null!");
                instance = MissionControl.getInstance().getWaypointMissionOperator();
                //DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
            }
        }
        return instance;
    }

    @Override
    protected Void doInBackground(ArrayList<Waypoint>... WPSarray) {

        ArrayList<Waypoint> WPS = WPSarray[0];

        for (int i = 1; i < WPS.size(); i++) {
            Log.i(TAG, "Distance WP"+(i-1)+"-WP"+i+": "+ Dist.geo(new double[]{
                    WPS.get(i-1).coordinate.getLatitude(), WPS.get(i-1).coordinate.getLongitude()},
                    new double[]{WPS.get(i).coordinate.getLatitude(), WPS.get(i).coordinate.getLongitude()}));
        }

        Log.i(TAG, "Heading mode: "+mHeadingMode.toString());
        Log.i(TAG, "Speed: "+speed);
        Log.i(TAG, "Timeout: "+timeout);
        Log.i(TAG, "Flight Path Mode: "+missionFlightPathMode.toString());

        //this.WP1 = WPS[0];
        //this.WP2 = WPS[1];
        //Log.i(TAG, "Fake Waypoint Heading " + WP1.heading + ", Real Waypoint Heading: " + WP2.heading);
        int attempt = 1;
        long startedTime = System.currentTimeMillis();
        //Log.i(TAGtime, "Goto: " + WP2.coordinate.getLatitude() + ", " + WP2.coordinate.getLongitude()+", "
        //        +WP2.altitude + " received at " + new Date(startedTime));
        while ((System.currentTimeMillis() - startedTime) < timeout * 1000L &&
                status != WaypointMissionStatus.ACTIVE) {
            stopWaypointMission();
            while (locked) { //Wait to stop the mission
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    Log.d(TAG, ex.toString());
                }
            }

            if (status == WaypointMissionStatus.STOPPED) { //If it stopped successfully
                mainOperation(WPS, speed); //Goto(WP1, WP2, speed);

                while (locked) { //Wait to start the mission
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ex) {
                        Log.d(TAG, ex.toString());
                    }
                }
            }
            attempt++;
            if (attempt >= 3) {
                Log.i(TAG, "Destroy Mission Operator and start over");
                DJISDKManager.getInstance().getMissionControl().destroyWaypointMissionOperator();
                instance = MissionControl.getInstance().getWaypointMissionOperator();
                attempt = 1;
            }
        }

        Log.i(TAG, "Vgikame apo ti while");
        if (status != WaypointMissionStatus.ACTIVE) {
            //StopWaypointNavigation stopWP = new StopWaypointNavigation();
            //stopWP.execute();
            stopWaypointMission();
        }

        return null;
    }

    public void mainOperation(ArrayList<Waypoint> WPs, float speed) { //public void Goto(Waypoint WPc, Waypoint WPe, float speed) {
        locked = true;

        FlightController mFlightController = ((Aircraft) DJIApplication.getProductInstance()).getFlightController();
        FlightControllerState droneState = mFlightController.getState();

        addListener();
        /*
        Log.i(TAG, "Current Position --> [" + droneState.getAircraftLocation().getLatitude()+","
                +droneState.getAircraftLocation().getLongitude()+","+droneState.getAircraftLocation().getAltitude()+"]");
        Log.i(TAG, "Fake Waypoint --> [" +WPc.coordinate.getLatitude()+","
                +WPc.coordinate.getLongitude() +","+WPc.altitude+"]");
        Log.i(TAG, "Actual Waypoint --> [" +WPe.coordinate.getLatitude()+","
                +WPe.coordinate.getLongitude() +","+WPe.altitude+"]");

        double dist = CalculateDistanceLatLon(droneState.getAircraftLocation().getLatitude(),
                WPc.coordinate.getLatitude(), droneState.getAircraftLocation().getLongitude(),
                WPc.coordinate.getLongitude(), droneState.getAircraftLocation().getAltitude(), WPc.altitude);
        Log.i(TAG, "Distance between drone's current position and the first(fake) WP --> " + dist + " meters");

        dist = CalculateDistanceLatLon(droneState.getAircraftLocation().getLatitude(),
                WPe.coordinate.getLatitude(), droneState.getAircraftLocation().getLongitude(),
                WPe.coordinate.getLongitude(), droneState.getAircraftLocation().getAltitude(), WPe.altitude);
        Log.i(TAG, "Distance between drone's current position and the actual WP --> " + dist + " meters");

        dist = CalculateDistanceLatLon(WPc.coordinate.getLatitude(), WPe.coordinate.getLatitude(),
                WPc.coordinate.getLongitude(), WPe.coordinate.getLongitude(), WPc.altitude, WPe.altitude);
        Log.i(TAG, "Distance between WPs --> " + dist + " meters");
        */

        FA.setCollisionAvoidanceEnabled(true, null);
        FA.setActiveObstacleAvoidanceEnabled(true, null);

        WaypointMission.Builder waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                .headingMode(mHeadingMode)
                .autoFlightSpeed(speed);
                //.maxFlightSpeed(speed)

        if (speed<2.0) {
            waypointMissionBuilder.maxFlightSpeed(2.0f);
        }else{
            waypointMissionBuilder.maxFlightSpeed(speed);
        }
        waypointMissionBuilder.flightPathMode(missionFlightPathMode);

        Log.i(TAG,"Current Flight Mode: " + waypointMissionBuilder.getFlightPathMode().toString());

        if (!waypointMissionBuilder.isExitMissionOnRCSignalLostEnabled()){
            waypointMissionBuilder.setExitMissionOnRCSignalLostEnabled(true);
        }

        for (Waypoint wp: WPs) {
            waypointMissionBuilder.addWaypoint(wp);
        }

        try {
            if (!waypointMissionBuilder.isGimbalPitchRotationEnabled()) {
                waypointMissionBuilder.setGimbalPitchRotationEnabled(true);
            }
        } catch (Exception e) {
            Log.i(TAG, "ERROR in enabling gimbal pitch rotation:" + e.toString());
        }

        DJIError skata = waypointMissionBuilder.checkParameters();
        if (skata != null) {
            Log.i(TAG, skata.getDescription());
        }

        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        int attempts = 1;
        while (error != null && attempts <= MAX_ATTEMPTS) {
            Log.i(TAG, "Error with the parameters of the mission: " + error.toString());
            error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            attempts++;
        }

        if (error == null) {
            Log.i(TAG, "(1/3) Mission loaded successfully! No. attempts: " + attempts);
            attempts = 1;
            status = WaypointMissionStatus.FAIL_TO_UPLOAD;
            while (status.equals(WaypointMissionStatus.FAIL_TO_UPLOAD) && attempts <= MAX_ATTEMPTS) {
                if (WaypointMissionState.READY_TO_RETRY_UPLOAD.equals(getWaypointMissionOperator().getCurrentState
                        ()) || WaypointMissionState.READY_TO_UPLOAD.equals(getWaypointMissionOperator().
                        getCurrentState())) {
                    getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError == null) {
                                status = WaypointMissionStatus.UPLOADED;
                                Log.i(TAG, "(2/3) Mission uploaded successfully!");
                            } else {
                                status = WaypointMissionStatus.FAIL_TO_UPLOAD;
                                Log.i(TAG, "Attempting to UPLOAD the following error occurred:\n"+djiError.toString());
                            }
                        }
                    });
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                attempts++;
            }
            Log.i(TAG, "No. attempts: " + (attempts - 1));

            if (status.equals(WaypointMissionStatus.UPLOADED)) {
                attempts = 1;
                status = WaypointMissionStatus.FAIL_TO_START;
                while (status.equals(WaypointMissionStatus.FAIL_TO_START) && attempts <= MAX_ATTEMPTS) {
                    if (WaypointMissionState.READY_TO_EXECUTE.equals(getWaypointMissionOperator().getCurrentState())) {
                        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                if (djiError == null) {
                                    //addListener();
                                    status = WaypointMissionStatus.ACTIVE;
                                    Log.i(TAG, "(3/3) Mission started successfully!");
                                } else {
                                    Log.i(TAG, "Attempting to START the following error occurred:\n"+djiError.getDescription());
                                    status = WaypointMissionStatus.FAIL_TO_START;
                                }
                            }
                        });
                    }
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    attempts++;
                }
                Log.i(TAG, "No. attempts: " + (attempts - 1));
            }
        } else {
            status = WaypointMissionStatus.INACTIVE;
        }
        locked = false;

    }

    public void stopWaypointMission() {
        if (status != WaypointMissionStatus.STOPPED) {
            locked = true;
            Log.i(TAG, "Stop previous mission");
            removeListener();
            int current_attempt = 1;
            while (status != WaypointMissionStatus.STOPPED && current_attempt <= MAX_ATTEMPTS) {
                stopExecution();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    android.util.Log.d("Waypoint Mission", ex.toString());
                }
                current_attempt++;
            }
            Log.i(TAG, (current_attempt - 1) + " attempts were needed for this operation");
            locked = false;
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



    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().addListener(eventNotificationListener);
        }
    }

    private void removeListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().removeListener(eventNotificationListener);
        }
    }

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
            sendStatus(ExperimentEnum.STARTED);
        }


        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            //setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
            //if (markerWP != null) {
            //    markerWP.remove();
            //}
            Log.i(TAG,"I am done!");
            sendStatus(ExperimentEnum.COMPLETED);
            //sendStatus(ExperimentEnum.COMPLETED);
        }

    };


    private void sendStatus(ExperimentEnum status) {
        GenericRecord statusSchema = new GenericData.Record(schemas.get("status"));
        statusSchema.put("destinationSystem", "Drone Server");
        statusSchema.put("sourceSystem", droneCanonicalName);
        statusSchema.put("time", System.currentTimeMillis());
        statusSchema.put("status", status);

        dispatchMessage = new DispatchMessage(schemas.get("status"), server_ip,
                server_port, connection_time_out);
        dispatchMessage.execute(statusSchema);
    }



    private double CalculateDistanceLatLon(double lat1, double lat2, double lon1,
                                           double lon2, double el1, double el2) {

        final int R = 6371; // Radius of the earth

        Double latDistance = Math.toRadians(lat2 - lat1);
        Double lonDistance = Math.toRadians(lon2 - lon1);
        Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        double height = el1 - el2;

        distance = Math.pow(distance, 2) + Math.pow(height, 2);

        return Math.sqrt(distance);
    }

}
