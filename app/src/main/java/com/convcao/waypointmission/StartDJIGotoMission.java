package com.convcao.waypointmission;


import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.util.CommonCallbacks;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class StartDJIGotoMission extends AsyncTask<Waypoint, Void, Void> {


    protected enum WaypointMissionStatus {
        ACTIVE, READY, FINISHED, INACTIVE, STOPPED, LOADED, UPLOADED,
        FAIL_TO_STOP, FAIL_TO_LOAD, FAIL_TO_UPLOAD, FAIL_TO_START
    }


    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction;
    private WaypointMissionHeadingMode mHeadingMode;
    private FlightAssistant FA;
    private float speed;
    protected boolean locked = false;
    private final int MAX_ATTEMPTS = 20;
    private WaypointMissionStatus status;
    private Handler handler;
    private Waypoint WP1, WP2;

    private boolean imageTaken = false;

    protected static final String TAG = "StartDJIGotoMission";

    public StartDJIGotoMission(float speed) {
        mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
        mHeadingMode = WaypointMissionHeadingMode.AUTO; //TODO fix me in the future
        FA = new FlightAssistant();
        status = WaypointMissionStatus.INACTIVE;
        this.speed = speed;
        this.handler = new Handler();
    }

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            if (MissionControl.getInstance().getWaypointMissionOperator() != null) {
                Log.i(TAG, "The Mission Control Operator was null!");
                instance = MissionControl.getInstance().getWaypointMissionOperator();
                //DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
            }
        }
        return instance;
    }

    @Override
    protected Void doInBackground(Waypoint... WPS) {
        this.WP1 = WPS[0];
        this.WP2 = WPS[1];
        int attempt=1;
        while (attempt<=2) {
            stopWaypointMission();
            while (locked) { //Wait to stop the mission
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    Log.d(TAG, ex.toString());
                }
            }

            if (status == WaypointMissionStatus.STOPPED) { //If it stopped successfully
                Goto(WP1, WP2, speed);

                while (locked) { //Wait to start the mission
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ex) {
                        Log.d(TAG, ex.toString());
                    }
                }
            }
            attempt++;
        }

        /*
        captureAction();
        imageTaken = false;
        while (!imageTaken) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Log.d(TAG, ex.toString());
            }
        }
        */

        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (status != WaypointMissionStatus.ACTIVE){
            DJISDKManager.getInstance().getMissionControl().destroyWaypointMissionOperator();
            StartDJIGotoMission adapterNew = new StartDJIGotoMission(speed);
            adapterNew.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, WP1, WP2);
        }
    }


    public void Goto(Waypoint WPc, Waypoint WPe, float speed) {
        locked = true;

        FlightController mFlightController = ((Aircraft) DJIApplication.getProductInstance()).getFlightController();
        FlightControllerState droneState = mFlightController.getState();

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


        FA.setCollisionAvoidanceEnabled(true, null);
        FA.setActiveObstacleAvoidanceEnabled(true, null);

        WaypointMission.Builder waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                .headingMode(mHeadingMode)
                .autoFlightSpeed(speed)
                .maxFlightSpeed(speed)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL).addWaypoint(WPc).addWaypoint(WPe);
        //.addWaypoint(fakeWP)
        //.waypointCount(2);


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
            Log.i(TAG, "(1/3) Mission loaded successfully! No. attempts: " + attempts );
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
                                Log.i(TAG, djiError.toString());
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
                                    status = WaypointMissionStatus.ACTIVE;
                                    Log.i(TAG, "(3/3) Mission started successfully!");
                                } else {
                                    Log.i(TAG, djiError.getDescription());
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
        }else{
            status = WaypointMissionStatus.INACTIVE;
        }
        locked = false;

        /*
        DJIError error = DJIError.COMMON_UNKNOWN;
        status = WaypointMissionStatus.FAIL_TO_LOAD;
        int current_attempt = 1;
        while (error != null && current_attempt <= MAX_ATTEMPTS) {
            error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            current_attempt++;
        }
        Log.i(TAG, (current_attempt - 1) + " attempts were needed for this operation");

        if (error == null) {
            Log.i(TAG, "(1/3) Mission loaded successfully!");
            uploadWayPointMission();
        }else{
            Log.i(TAG, error.getDescription());
            locked = false;
        }
        */

    }

    // Method for taking photo
    private void captureAction() {

        final Camera camera = DJIApplication.getCameraInstance();
        if (camera != null) {
            // Set the camera capture mode as Single mode
            SettingsDefinitions.ShootPhotoMode photoMode = SettingsDefinitions.ShootPhotoMode.SINGLE;
            camera.setShootPhotoMode(photoMode, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (null == djiError) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                camera.startShootPhoto(new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                        if (djiError == null) {
                                            Log.i(TAG, "take photo: success");
                                        } else {
                                            Log.i(TAG, djiError.getDescription());
                                        }
                                        imageTaken = true;
                                    }
                                });
                            }
                        }, 2000);
                    }
                }
            });
        }
    }

    public void stopWaypointMission() {
        if (status != WaypointMissionStatus.STOPPED) {
            locked = true;
            Log.i(TAG, "Stop previous mission");
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


    /*
    private void uploadWayPointMission() {
        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    //setResultToToast("Mission upload successfully!");
                    status = WaypointMissionStatus.UPLOADED;
                    Log.i(TAG, "(2/3) Mission uploaded successfully!");
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex) {
                        android.util.Log.d("Waypoint Mission", ex.toString());
                    }
                    status = WaypointMissionStatus.FAIL_TO_START;
                    int current_attempt = 1;
                    while (status == WaypointMissionStatus.FAIL_TO_START && current_attempt <= MAX_ATTEMPTS) {
                        startWaypointMission();
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ex) {
                            android.util.Log.d("Waypoint Mission", ex.toString());
                        }
                        current_attempt++;
                    }
                    Log.i(TAG, (current_attempt - 1) + " attempts were needed for this operation");
                    locked = false;
                } else {
                    //setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    status = WaypointMissionStatus.FAIL_TO_UPLOAD;
                    Log.i(TAG, "Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError error) {
                            if (error != null) {
                                locked = false;
                            }
                        }
                    });
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
                    //Log.i(TAG, error.getDescription());
                    status = WaypointMissionStatus.FAIL_TO_START;
                }
            }
        });
    }
    */

}
