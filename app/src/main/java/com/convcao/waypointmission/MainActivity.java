package com.convcao.waypointmission;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;

import dji.common.product.Model;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;
import dji.common.error.DJIError;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;


public class MainActivity extends FragmentActivity implements TextureView.SurfaceTextureListener,
        View.OnClickListener, OnMapReadyCallback {

    Handler handler = new Handler();
    protected static final String TAG = "WaypointMissionActivity";
    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;
    // Codec for video live view
    protected DJICodecManager mCodecManager = null;
    protected TextureView mVideoSurface = null;

    private GoogleMap gMap;

    private FlightAssistant FA = new FlightAssistant();

    private Switch switchB;
    private ImageButton locate;
    private Button gotoc, stop;

    public boolean inOperation = false;

    private double droneLocationLat = 181, droneLocationLng = 181, minimumArmHeight;
    private float droneLocationAlt;
    private float droneRotation = 0f;

    //private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker markerWP = null;
    private Marker droneMarker = null;
    private LatLngBounds bounds = null;
    LatLngBounds.Builder builder;

    //private float altitude = 100.0f;
    private float mSpeed = 10.0f;

    private Waypoint WP;
    private LatLng point2D;
    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;


    private SchemaLoader schemaLoader;
    private DispatchMessage dispatchMessage;
    private Schema schemaGoto;

    private long publishPeriod;
    private long lastPublishLocationOn;
    private int mapPadding;

    private String server_ip;
    private int server_port;
    private int android_port;
    private String droneCanonicalName;

    private WaypointNavigation WPAdapter;

    private boolean not_started, not_stopped;

    private StartDJIGotoMission adapter;

    @Override
    protected void onResume() {
        super.onResume();
        initFlightController();
        initPreviewer();
    }

    @Override
    protected void onPause() {
        uninitPreviewer();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceiver);
        //removeListener();
        uninitPreviewer();
        super.onDestroy();
    }

    /**
     * @Description : RETURN Button RESPONSE FUNCTION
     */
    public void onReturn(View view) {
        Log.d(TAG, "onReturn");
        this.finish();
    }

    private void setResultToToast(final String string) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI() {
        ScreenAlwaysOn_MaxBrightness();

        // init mVideoSurface
        mVideoSurface = (TextureView) findViewById(R.id.video_previewer_surface);
        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }

        locate = (ImageButton) findViewById(R.id.locate);
        gotoc = (Button) findViewById(R.id.gotoc);
        switchB = (Switch) findViewById(R.id.arm);
        stop = (Button) findViewById(R.id.stop);

        locate.setOnClickListener(this);
        gotoc.setOnClickListener(this);
        stop.setOnClickListener(this);


        gotoc.setEnabled(false);
        switchB.setEnabled(false);
        stop.setEnabled(false);

        switchB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    gotoc.setEnabled(true);
                    stop.setEnabled(true);
                } else {
                    gotoc.setEnabled(false);
                    stop.setEnabled(false);
                }
            }
        });

    }


    // This function only take effect in real physical android device,
    // it can not take effect in android emulator.
    private void ScreenAlwaysOn_MaxBrightness() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1f;
        getWindow().setAttributes(lp);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_main);

        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        initUI();

        //First Person View
        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {
            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if (mCodecManager != null) {
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }
            }
        };
        //end of First Person View

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        Properties props = new Properties();
        InputStream input = null;
        try {

            input = getAssets().open("properties/Parameters.properties");

            // load a properties file
            props.load(input);

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        builder = new LatLngBounds.Builder();

        server_ip = props.getProperty("server_ip");
        server_port = Integer.parseInt(props.getProperty("server_port"));
        android_port = Integer.parseInt(props.getProperty("port"));
        droneCanonicalName = props.getProperty("canonical_name");
        minimumArmHeight = Double.parseDouble(props.getProperty("minimum_height_to_arm"));
        mapPadding = Integer.parseInt(props.getProperty("map_padding"));
        publishPeriod = Long.parseLong(props.getProperty("publish_location_period"));

        Thread gotoThread = new Thread(new GoToListener(Integer.parseInt(props.getProperty("port"))));
        gotoThread.start();

        schemaLoader = new SchemaLoader();
        schemaLoader.load();
        schemaGoto = schemaLoader.getSchema("goto");
        lastPublishLocationOn = System.currentTimeMillis();

        WPAdapter = new WaypointNavigation();
        adapter = new StartDJIGotoMission(mSpeed);

    }


    //First Person View
    private void initPreviewer() {
        BaseProduct product = DJIApplication.getProductInstance();
        if (product == null || !product.isConnected()) {
            setResultToToast(getString(R.string.disconnected));
        } else {
            if (null != mVideoSurface) {
                mVideoSurface.setSurfaceTextureListener(this);
            }
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(mReceivedVideoDataCallBack);
            }
        }
    }

    private void uninitPreviewer() {
        Camera camera = DJIApplication.getCameraInstance();
        if (camera != null) {
            // Reset the callback
            VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(null);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG, "onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    //end of First Person View


    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange() {
        initFlightController();
        initPreviewer();
        //loginAccount();
    }

    private void initFlightController() {

        BaseProduct product = DJIApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
                FA.setCollisionAvoidanceEnabled(true, null);
                FA.setActiveObstacleAvoidanceEnabled(true, null);
            }
        }

        if (mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {

                @Override
                public void onUpdate(FlightControllerState djiFlightControllerCurrentState) {
                    droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                    droneLocationAlt = djiFlightControllerCurrentState.getAircraftLocation().getAltitude();
                    droneRotation = djiFlightControllerCurrentState.getAircraftHeadDirection();
                    updateDroneLocation();

                    long currentTime = System.currentTimeMillis();
                    if (switchB.isChecked() && (currentTime - lastPublishLocationOn) >= publishPeriod) {
                        lastPublishLocationOn = currentTime;
                        publishLocation(droneLocationLat, droneLocationLng, droneLocationAlt, currentTime);
                    }
                }
            });
        }
    }


    //Send GenericRecord location to the server
    private void publishLocation(double locationLat, double locationLon, float alt, long time) {
        GenericRecord location = schemaLoader.createGenericRecord("location");
        location.put("sourceSystem", droneCanonicalName);
        location.put("time", time);
        location.put("latitude", locationLat);
        location.put("longitude", locationLon);
        location.put("altitude", alt);

        dispatchMessage = new DispatchMessage(schemaLoader.getSchema("location"), server_ip, server_port);
        dispatchMessage.execute(location);
    }


    /*
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

        }


        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            //setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
            //if (markerWP != null) {
            //    markerWP.remove();
            //}
            inOperation = false;
        }

    };*/

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            if (DJISDKManager.getInstance().getMissionControl() != null) {
                instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
            }
        }
        return instance;
    }


    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation() {

        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.rotation(droneRotation);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneLocationAlt >= minimumArmHeight) {
                    switchB.setEnabled(true);
                } else {
                    switchB.setChecked(false);
                    switchB.setEnabled(false);
                }

                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                    droneMarker = gMap.addMarker(markerOptions);
                    if (inOperation)
                        viewPointFitBounds(mapPadding);
                    else
                        viewPointUpdate();
                }
            }
        });
    }

    private void markWaypoint(LatLng point) {
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        markerWP = gMap.addMarker(markerOptions);
        //mMarkers.put(mMarkers.size(), marker);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.locate: {
                updateDroneLocation();
                viewPointUpdate(18.0f); // Locate the drone's place
                break;
            }
            case R.id.gotoc: {
                showSettingDialog();
                break;
            }
            case R.id.stop: {
                //adapter.stopWaypointMission();
                //stopWaypointMission();
                //adapter.cancel(true);
                WPAdapter.stopWaypointMission();
                break;
            }
            default:
                break;
        }
    }

    private void viewPointFitBounds(int padding) {
        //padding: offset from edges of the map in pixels
        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
        gMap.moveCamera(cu);
    }

    private void viewPointUpdate() {
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        CameraUpdate cu = CameraUpdateFactory.newLatLng(pos);
        gMap.moveCamera(cu);
    }

    private void viewPointUpdate(float zoomlevel) {
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        gMap.moveCamera(cu);
    }


    private void showSettingDialog() {
        LinearLayout wayPointSettings = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        final TextView wpLatitude_TV = (TextView) wayPointSettings.findViewById(R.id.latitude);
        final TextView wpLongitude_TV = (TextView) wayPointSettings.findViewById(R.id.longitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed) {
                    mSpeed = 3.0f;
                } else if (checkedId == R.id.MidSpeed) {
                    mSpeed = 5.0f;
                } else if (checkedId == R.id.HighSpeed) {
                    mSpeed = 10.0f;
                }
            }

        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select finish action");
                if (checkedId == R.id.finishNone) {
                    mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
                } else if (checkedId == R.id.finishGoHome) {
                    mFinishedAction = WaypointMissionFinishedAction.GO_HOME;
                } else if (checkedId == R.id.finishAutoLanding) {
                    mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                } else if (checkedId == R.id.finishToFirst) {
                    mFinishedAction = WaypointMissionFinishedAction.GO_FIRST_WAYPOINT;
                }
            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select heading");
                if (checkedId == R.id.headingNext) {
                    mHeadingMode = WaypointMissionHeadingMode.AUTO;
                } else if (checkedId == R.id.headingInitDirec) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_INITIAL_DIRECTION;
                } else if (checkedId == R.id.headingRC) {
                    mHeadingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER;
                } else if (checkedId == R.id.headingWP) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                }
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Start", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                        String altitudeString = wpAltitude_TV.getText().toString();

                        double latitude = Double.parseDouble(wpLatitude_TV.getText().toString());
                        double longitude = Double.parseDouble(wpLongitude_TV.getText().toString());
                        int altitude = Integer.parseInt(nulltoIntegerDefalt(altitudeString));

                        //stopWaypointMission();

                        //adapter.cancel(true);
                        WPAdapter.stopWaypointMission();
                        PrepareMap(latitude, longitude);
                        //adapter.stopWaypointMission();
                        //adapter.Goto(new Waypoint(droneLocationLat, droneLocationLng, droneLocationAlt),
                        //        new Waypoint(latitude, longitude, altitude), mSpeed);

                        //adapter = new StartDJIGotoMission(mSpeed);
                        //adapter.execute(new Waypoint(droneLocationLat, droneLocationLng, droneLocationAlt),
                        //        new Waypoint(latitude, longitude, altitude));
                        WPAdapter.Goto(new Waypoint(droneLocationLat, droneLocationLng, droneLocationAlt),
                                        new Waypoint(latitude, longitude, altitude), mSpeed);
                        //Goto(latitude, longitude, altitude);
                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    String nulltoIntegerDefalt(String value) {
        if (!isIntValue(value)) value = "0";
        return value;
    }

    boolean isIntValue(String val) {
        try {
            val = val.replace(" ", "");
            Integer.parseInt(val);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private void PrepareMap(double lat, double lon) {
        if (markerWP != null) {
            markerWP.remove();
        }

        point2D = new LatLng(lat, lon);
        markWaypoint(point2D);

        bounds = null;
        //LatLngBounds.Builder builder = new LatLngBounds.Builder();
        //Include current possition
        builder.include(new LatLng(droneLocationLat, droneLocationLng));
        //Include the desired waypoint location
        builder.include(point2D);
        bounds = builder.build();
    }


    private void Goto(double lat, double lon, float alt) {
        setResultToToast("GOTO: [" + lat + ", " + lon + "] with altitude: " + alt);
        inOperation = true;
        WP = new Waypoint(lat, lon, alt);

        Log.e(TAG, "Point (2D) :" + point2D.toString());
        Log.e(TAG, "altitude " + alt);
        Log.e(TAG, "speed " + mSpeed);
        Log.e(TAG, "mFinishedAction " + mFinishedAction);
        Log.e(TAG, "mHeadingMode " + mHeadingMode);

        FA.setCollisionAvoidanceEnabled(true, null);
        FA.setActiveObstacleAvoidanceEnabled(true, null);

        Waypoint fakeWP = new Waypoint(droneLocationLat, droneLocationLng, droneLocationAlt);
        waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                .headingMode(mHeadingMode)
                .autoFlightSpeed(mSpeed)
                .maxFlightSpeed(mSpeed)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL).addWaypoint(fakeWP).addWaypoint(WP);
        //.addWaypoint(fakeWP)
        //.waypointCount(2);
        DJIError error = DJIError.COMMON_UNKNOWN;
        while (error != null) {
            error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (error == null) {
            //setResultToToast("Waypoint loaded successfully");
            uploadWayPointMission();
        } else {
            //setResultToToast("loadMission failed with:" + error.getDescription());
            inOperation = false;
        }
    }


    private void uploadWayPointMission() {
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
                    not_started = true;
                    while (not_started) {
                        startWaypointMission();
                        try {
                            Thread.sleep(500);
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
                if (error != null) {
                    inOperation = false;
                }else{
                    not_started = false;
                }
            }
        });
    }

    private void stopWaypointMission() {
        if (inOperation) {

            not_stopped = true;
            while(not_stopped){
                stopExecution();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    android.util.Log.d("Waypoint Mission", ex.toString());
                }
            }

            if (markerWP != null) {
                markerWP.remove();
            }
        }
        inOperation = false;
    }


    private void stopExecution(){
        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                //setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
                if (error==null){
                    not_stopped = false;
                }
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
        }

        //LatLng xanthi = new LatLng(41.1348801, 24.8880005);
        //gMap.addMarker(new MarkerOptions().position(xanthi).title("Marker in Xanthi"));
        //gMap.moveCamera(CameraUpdateFactory.newLatLng(xanthi));

        gMap.getUiSettings().setZoomControlsEnabled(true);
        gMap.getUiSettings().setCompassEnabled(true);
        gMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);

        viewPointUpdate(18.0f);
        //gMap.getUiSettings().setMyLocationButtonEnabled(true);
    }

    class GoToListener implements Runnable {
        Socket s;
        int portAndroid;
        ServerSocket ss;
        String message;


        GoToListener(int portAndroid) {
            this.portAndroid = portAndroid;
        }

        @Override
        public void run() {
            try {
                ss = new ServerSocket(portAndroid);
                while (true) {
                    s = ss.accept();
                    DatumReader datumReader = new GenericDatumReader(schemaGoto);
                    GenericRecord gotoRecord = new GenericData.Record(schemaGoto);
                    InputStream inputStream = s.getInputStream();
                    DecoderFactory decoderFactory = new DecoderFactory();
                    BinaryDecoder binaryDecoder = decoderFactory.binaryDecoder(inputStream, null);
                    datumReader.read(gotoRecord, binaryDecoder);

                    //setResultToToast(gotoRecord.toString());

                    if (switchB.isChecked()) { //Check if the drone is armed

                        double gotoLat = (double) gotoRecord.get("latitude");
                        double gotoLon = (double) gotoRecord.get("longitude");
                        float gotoAlt = (float) gotoRecord.get("altitude");


                        new Handler(Looper.getMainLooper()).post(new Runnable() {

                            @Override
                            public void run() {
                                // this will run in the main thread



                                //adapter.cancel(true);
                                PrepareMap(gotoLat, gotoLon);
                                //adapter.stopWaypointMission();
                                //adapter.Goto(new Waypoint(droneLocationLat, droneLocationLng, droneLocationAlt),
                                //        new Waypoint(latitude, longitude, altitude), mSpeed);

                                //adapter = new StartDJIGotoMission(mSpeed);
                                //adapter.execute(new Waypoint(droneLocationLat, droneLocationLng, droneLocationAlt),
                                //        new Waypoint(gotoLat, gotoLon, gotoAlt));


                                while (WPAdapter.getStatus() != WaypointNavigation.WaypointMissionStatus.READY &&
                                        WPAdapter.getStatus() != WaypointNavigation.WaypointMissionStatus.ACTIVE){
                                    try {
                                        Thread.sleep(500);
                                    } catch (InterruptedException ex) {
                                        Log.d(TAG, ex.toString());
                                    }
                                }

                                WPAdapter.stopWaypointMission();
                                WPAdapter.Goto(new Waypoint(droneLocationLat, droneLocationLng, droneLocationAlt),
                                       new Waypoint(gotoLat, gotoLon, gotoAlt), mSpeed);


                                //WPAdapter.startMission(new Waypoint(droneLocationLat, droneLocationLng, droneLocationAlt),
                                //        new Waypoint(gotoLat, gotoLon, gotoAlt), mSpeed);
                            }
                        });

                        /*
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                while (true) {

                                    stopWaypointMission();
                                    PrepareMap(gotoLat, gotoLon);

                                    //adapter.Goto(new Waypoint(droneLocationLat, droneLocationLng, droneLocationAlt),
                                    //        new Waypoint(gotoLat, gotoLon, gotoAlt), mSpeed);
                                    Goto(gotoLat, gotoLon, gotoAlt);
                                }
                            }
                        });*/

                    }
                    //Goto(gotoLat, gotoLon, gotoAlt);
                    //Goto((double) gotoRecord.get("latitude"), (double) gotoRecord.get("longitude"),
                    //        (float) gotoRecord.get("altitude"));
                }
            } catch (IOException e) {
                setResultToToast("Error in reading GOTO commands");
            }
        }

    }

    public class SchemaLoader {

        private final Map<String, Schema> schemaMap = new HashMap<>();
        private final Schema.Parser parser = new Schema.Parser();

        public void load() {
            try {
                String[] schemasNames = getAssets().list("schemas");
                for (String name : schemasNames) {
                    String schemaIdWithOutExt = name.split("\\.")[0]; //remove extension
                    Schema schema = parser.parse(getAssets().open("schemas/" + name));
                    schemaMap.put(schemaIdWithOutExt, schema);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public GenericRecord createGenericRecord(final String schemaId) {
            return new GenericData.Record(getSchema(schemaId));
        }

        private Schema getSchema(final String schemaId) {
            return schemaMap.get(schemaId);
        }

    }

}
