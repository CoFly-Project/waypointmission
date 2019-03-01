package com.convcao.waypointmission;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.flightcontroller.FlightControllerState;
import dji.common.gimbal.GimbalState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;

import dji.common.product.Model;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.products.Aircraft;

import static org.apache.commons.io.IOUtils.copy;


public class MainActivity extends FragmentActivity implements TextureView.SurfaceTextureListener,
        View.OnClickListener, OnMapReadyCallback, ScreenShotCompleted, DJICodecManager.YuvDataCallback {

    Handler handler = new Handler();
    protected static final String TAG = "WaypointMissionActivity";
    protected static final String TAGsocket = "ReceiveWayPoint";
    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;
    // Codec for video live view
    protected DJICodecManager mCodecManager = null;
    protected TextureView mVideoSurface = null;
    private MediaCodec codec;

    private GoogleMap gMap;

    private FlightAssistant FA = new FlightAssistant();

    private Switch switchB;
    private ImageButton locate, infoB;
    private Button stop;
    private Spinner publisher;

    public boolean inOperation = false;

    private double droneLocationLat = 181, droneLocationLng = 181, minimumArmHeight;
    private float droneLocationAlt;
    private float droneGimbal;
    private int droneRotation = 0;
    private float droneVelocityX;
    private float droneVelocityY;
    private float droneVelocityZ;

    private double cameraLat, cameraLon;
    private float cameraAlt, cameraGimbal, cameraVelocityX, cameraVelocityY, cameraVelocityZ;
    private int cameraRotation;

    //private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker markerWP = null;
    private Marker droneMarker = null;
    private LatLngBounds bounds = null;
    LatLngBounds.Builder builder;

    private float zoomLevel, dSpeed, dTimeout;

    private Waypoint WP;
    private LatLng point2D;
    private FlightController mFlightController;
    private Gimbal mGimbal;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;


    private SchemaLoader schemaLoader;
    private DispatchMessage dispatchMessage;
    private Schema schemaGoto, schemaPath;

    private long publishPeriod;
    private long lastPublishLocationOn;
    private int mapPadding;

    private String server_ip;
    private int server_port;
    private int android_port;
    private int connection_time_out;
    private String droneCanonicalName;

    private StopWaypointNavigation WPAdapter;

    private StartDJIMission adapter;

    private MessageListener gotoRun;

    private enum Command {
        GOTO, PATH_FOLLOWING, UNKNOWN
    }

    private byte[] cameraView;
    private boolean cameraBytesUpdated = false;
    private ScreenShot savePhoto;
    private int count;

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        initFlightController();
        initPreviewer();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        uninitPreviewer();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
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
        Log.d(TAG, "initUI");
        ScreenAlwaysOn_MaxBrightness();

        // init mVideoSurface
        mVideoSurface = (TextureView) findViewById(R.id.video_previewer_surface);
        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }


        infoB = (ImageButton) findViewById(R.id.connection_info);
        locate = (ImageButton) findViewById(R.id.locate);
        switchB = (Switch) findViewById(R.id.arm);
        stop = (Button) findViewById(R.id.stop);
        publisher = (Spinner) findViewById(R.id.publisher);

        infoB.setOnClickListener(this);
        locate.setOnClickListener(this);
        stop.setOnClickListener(this);

        switchB.setEnabled(false);
        infoB.setEnabled(true);
        stop.setEnabled(false);

        switchB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    infoB.setEnabled(false);
                    stop.setEnabled(true);
                } else {
                    infoB.setEnabled(true);
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

    public void onTaskComplete(byte[] result) {
        cameraView = result;
        cameraLat = droneLocationLat;
        cameraLon = droneLocationLng;
        cameraAlt = droneLocationAlt;
        cameraRotation = droneRotation;
        cameraGimbal = droneGimbal;
        cameraVelocityX = droneVelocityX;
        cameraVelocityY = droneVelocityY;
        cameraVelocityZ = droneVelocityZ;
        cameraBytesUpdated = true;
    }

    public void onWaypointReached() {
        publishCameraInfo(droneLocationLat, droneLocationLng, droneLocationAlt, droneRotation,
                droneGimbal, System.currentTimeMillis(), cameraView, droneVelocityX, droneVelocityY,
                droneVelocityZ);
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
        cameraView = new byte[1];
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

        droneCanonicalName = props.getProperty("canonical_name");
        android_port = Integer.parseInt(props.getProperty("port"));
        server_ip = props.getProperty("server_ip");
        server_port = Integer.parseInt(props.getProperty("server_port"));
        connection_time_out = Integer.parseInt(props.getProperty("connection_time_out"));


        minimumArmHeight = Double.parseDouble(props.getProperty("minimum_height_to_arm"));
        mapPadding = Integer.parseInt(props.getProperty("map_padding"));
        publishPeriod = Long.parseLong(props.getProperty("publish_location_period"));
        zoomLevel = Float.parseFloat(props.getProperty("zoomlevel"));
        dSpeed = Float.parseFloat(props.getProperty("speed"));
        dTimeout = Float.parseFloat(props.getProperty("timeout"));

        schemaLoader = new SchemaLoader();
        schemaLoader.load();
        schemaGoto = schemaLoader.getSchema("goto");
        schemaPath = schemaLoader.getSchema("path");

        gotoRun = new MessageListener(android_port);
        gotoRun.stop();
        gotoRun.start();

        lastPublishLocationOn = System.currentTimeMillis();

    }


    @Override
    public void onYuvDataReceived(final ByteBuffer yuvFrame, int dataSize, final int width, final int height) {
        //In this demo, we test the YUV data by saving it into JPG files.
        //DJILog.d(TAG, "onYuvDataReceived " + dataSize);
        if (count++ % 30 == 0 && yuvFrame != null) {
            final byte[] bytes = new byte[dataSize];
            yuvFrame.get(bytes);
            //DJILog.d(TAG, "onYuvDataReceived2 " + dataSize);
            savePhoto = new ScreenShot(width, height, MainActivity.this);
            savePhoto.execute(bytes);
        }
    }


    //First Person View
    private void initPreviewer() {
        Log.d(TAG, "initPreviewer");
        BaseProduct product = DJIApplication.getProductInstance();
        if (product == null || !product.isConnected()) {
            setResultToToast(getString(R.string.disconnected));
        } else {
            if (null != mVideoSurface) {
                mVideoSurface.setSurfaceTextureListener(this);
            }
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(mReceivedVideoDataCallBack);
                VideoFeeder.getInstance().getPrimaryVideoFeed().getVideoSource();
            }
        }
    }

    private void uninitPreviewer() {
        Log.d(TAG, "unInitPreviewer");
        Camera camera = DJIApplication.getCameraInstance();
        if (camera != null) {
            // Reset the callback
            VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(null);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
            mCodecManager.enabledYuvData(true);
            mCodecManager.setYuvDataCallback(this);
            //mCodecManager2 = new DJICodecManager(this, surface, 1280, 720);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d(TAG, "onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Log.d(TAG, "onSurfaceTextureUpdated");
    }

    //end of First Person View


    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange() {
        Log.d(TAG, "onProductConnectionChange");
        initFlightController();
        initPreviewer();
        //loginAccount();
    }

    private void initFlightController() {
        Log.d(TAG, "initFlightController");
        BaseProduct product = DJIApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
                mGimbal = product.getGimbal();
                FA.setCollisionAvoidanceEnabled(true, null);
                FA.setActiveObstacleAvoidanceEnabled(true, null);
            }
        }

        if (mGimbal != null) {
            mGimbal.setStateCallback(new GimbalState.Callback() {
                @Override
                public void onUpdate(@NonNull GimbalState gimbalState) {
                    droneGimbal = gimbalState.getAttitudeInDegrees().getPitch();
                }
            });
        }


        if (mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {

                @Override
                public void onUpdate(FlightControllerState djiFlightControllerCurrentState) {
                    droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                    droneLocationAlt = djiFlightControllerCurrentState.getAircraftLocation().getAltitude();
                    droneRotation = djiFlightControllerCurrentState.getAircraftHeadDirection();
                    droneVelocityX = djiFlightControllerCurrentState.getVelocityX();
                    droneVelocityY = djiFlightControllerCurrentState.getVelocityY();
                    droneVelocityZ = djiFlightControllerCurrentState.getVelocityZ();

                    updateDroneLocation();

                    long currentTime = System.currentTimeMillis();

                    if (switchB.isChecked() && publisher.getSelectedItem().toString().equals("Location & camera")
                            && cameraBytesUpdated) {
                        publishCameraInfo(cameraLat, cameraLon, cameraAlt,
                                cameraRotation, cameraGimbal, currentTime, cameraView, cameraVelocityX,
                                cameraVelocityY, cameraVelocityZ);
                        cameraBytesUpdated = false;
                    }


                    if (switchB.isChecked() && (currentTime - lastPublishLocationOn) >= publishPeriod) {

                        lastPublishLocationOn = currentTime;
                        publishLocation(droneLocationLat, droneLocationLng, droneLocationAlt,
                                droneRotation, currentTime);

//                        if (publisher.getSelectedItem().toString().equals("Location & camera") && cameraBytesUpdated) {
//                            publishCameraInfo(cameraLat, cameraLon, cameraAlt,
//                                    cameraRotation, cameraGimbal, currentTime, cameraView, cameraVelocityX,
//                                    cameraVelocityY, cameraVelocityZ);
//                            cameraBytesUpdated = false;
//                        }
//
//                        switch (publisher.getSelectedItem().toString()) {
//                            case "Only location":
//                                publishLocation(droneLocationLat, droneLocationLng, droneLocationAlt,
//                                        droneRotation, currentTime);
//                                break;
//                            case "Location & camera":
//                                publishCameraInfo(droneLocationLat, droneLocationLng, droneLocationAlt,
//                                        droneRotation, droneGimbal, currentTime, cameraView, droneVelocityX,
//                                        droneVelocityY, droneVelocityZ);
//                                break;
//                            default:
//                                publishLocation(droneLocationLat, droneLocationLng, droneLocationAlt,
//                                        droneRotation, currentTime);
//                                break;
//                        }
                    }
                }
            });
        }
    }


    //Send GenericRecord location to the server
    private void publishLocation(double locationLat, double locationLon, float alt, int heading, long time) {
        GenericRecord location = schemaLoader.createGenericRecord("location");
        location.put("sourceSystem", droneCanonicalName);
        location.put("listeningPort", android_port);
        location.put("time", time);
        location.put("latitude", locationLat);
        location.put("longitude", locationLon);
        location.put("altitude", alt);
        location.put("heading", heading);

        dispatchMessage = new DispatchMessage(schemaLoader.getSchema("location"), server_ip,
                server_port, connection_time_out);
        dispatchMessage.execute(location);
    }

    //Send Camera view to the server
    private void publishCameraInfo(double locationLat, double locationLon, float alt, int heading, float gimbal,
                                   long time, byte[] camera, float velocityX, float velocityY, float velocityZ) {
        GenericRecord cameraSchema = schemaLoader.createGenericRecord("camera");
        cameraSchema.put("sourceSystem", droneCanonicalName);
        cameraSchema.put("listeningPort", android_port);
        cameraSchema.put("time", time);
        cameraSchema.put("latitude", locationLat);
        cameraSchema.put("longitude", locationLon);
        cameraSchema.put("altitude", alt);
        cameraSchema.put("velocityX", velocityX);
        cameraSchema.put("velocityY", velocityY);
        cameraSchema.put("velocityZ", velocityZ);
        cameraSchema.put("heading", heading);
        cameraSchema.put("gimbalPitch", gimbal);
        cameraSchema.put("image", ByteBuffer.wrap(camera));

        dispatchMessage = new DispatchMessage(schemaLoader.getSchema("camera"), server_ip,
                server_port, connection_time_out);
        dispatchMessage.execute(cameraSchema);
    }


    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90.0 && latitude < 90.0 && longitude > -180.0 && longitude < 180.0) &&
                (latitude != 0f && longitude != 0f);
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
            case R.id.connection_info: {
                showConnectionDialog();
                break;
            }
            case R.id.locate: {
                updateDroneLocation();
                viewPointUpdate(zoomLevel); // Locate the drone's place
                break;
            }
            case R.id.stop: {
                WPAdapter = new StopWaypointNavigation();
                WPAdapter.execute();
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

    private void showConnectionDialog() {
        LinearLayout connectionSettings = (LinearLayout) getLayoutInflater().inflate(
                R.layout.dialog_info, null);

        EditText canonical_name_ET = ((EditText) connectionSettings.findViewById(R.id.canonical_name));
        canonical_name_ET.setText(droneCanonicalName, TextView.BufferType.EDITABLE);

        EditText android_port_ET = ((EditText) connectionSettings.findViewById(R.id.port));
        android_port_ET.setText(Integer.toString(android_port), TextView.BufferType.EDITABLE);

        EditText ip1_ET = ((EditText) connectionSettings.findViewById(R.id.ip1));
        ip1_ET.setText(server_ip.split("\\.")[0], TextView.BufferType.EDITABLE);

        EditText ip2_ET = ((EditText) connectionSettings.findViewById(R.id.ip2));
        ip2_ET.setText(server_ip.split("\\.")[1], TextView.BufferType.EDITABLE);

        EditText ip3_ET = ((EditText) connectionSettings.findViewById(R.id.ip3));
        ip3_ET.setText(server_ip.split("\\.")[2], TextView.BufferType.EDITABLE);

        EditText ip4_ET = ((EditText) connectionSettings.findViewById(R.id.ip4));
        ip4_ET.setText(server_ip.split("\\.")[3], TextView.BufferType.EDITABLE);

        EditText server_port_ET = ((EditText) connectionSettings.findViewById(R.id.server_port));
        server_port_ET.setText(Integer.toString(server_port), TextView.BufferType.EDITABLE);

        EditText timeout_ET = ((EditText) connectionSettings.findViewById(R.id.timeout));
        timeout_ET.setText(Integer.toString(connection_time_out), TextView.BufferType.EDITABLE);

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(connectionSettings)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        //TODO pass the parameters
                        droneCanonicalName = ((TextView) connectionSettings.findViewById(R.id.canonical_name))
                                .getText().toString();
                        int android_port_temp = Integer.parseInt(((TextView) connectionSettings.findViewById(R.id.port))
                                .getText().toString());
                        server_ip = ((TextView) connectionSettings.findViewById(R.id.ip1))
                                .getText().toString() + "." +
                                ((TextView) connectionSettings.findViewById(R.id.ip2))
                                        .getText().toString() + "." +
                                ((TextView) connectionSettings.findViewById(R.id.ip3))
                                        .getText().toString() + "." +
                                ((TextView) connectionSettings.findViewById(R.id.ip4))
                                        .getText().toString();
                        server_port = Integer.parseInt(((TextView) connectionSettings.findViewById(R.id.server_port))
                                .getText().toString());

                        connection_time_out = Integer.parseInt(((TextView) connectionSettings.findViewById(R.id.timeout))
                                .getText().toString());

                        if (android_port_temp != android_port) {
                            android_port = android_port_temp;
                            gotoRun.stop();
                            gotoRun = new MessageListener(android_port);
                            gotoRun.start();
                        }
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

        viewPointUpdate(zoomLevel);
        //gMap.getUiSettings().setMyLocationButtonEnabled(true);
    }


    class MessageListener implements Runnable {


        private Socket s;
        private ServerSocket ss;
        private AtomicBoolean isAlive = new AtomicBoolean(true);
        private Thread worker;
        private int consumerPort;

        MessageListener(int port) {
            isAlive.set(true);
            consumerPort = port;
        }

        public void start() {
            worker = new Thread(this);
            worker.start();
        }

        public void stop() {
            isAlive.set(false);
        }


        private boolean parseItAs(InputStream inputStream, Schema schema) {
            try {
                constructRecord(inputStream, schema);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private GenericRecord constructRecord(InputStream inputStream, Schema schema) throws Exception {
            DecoderFactory decoderFactory = new DecoderFactory();
            BinaryDecoder binaryDecoder = decoderFactory.binaryDecoder(inputStream, null);
            DatumReader datumReader = new GenericDatumReader(schema);
            GenericRecord record = new GenericData.Record(schema);
            datumReader.read(record, binaryDecoder);
            return record;
        }

        @Override
        public void run() {
            isAlive.set(true);
            try {
                ss = new ServerSocket(consumerPort);
            } catch (IOException e) {
                setResultToToast("Error in reading GOTO commands");
                Log.e(TAGsocket, "Cannot open server socket");
                Log.e(TAGsocket, e.toString());
                Log.e(TAGsocket, "Any incoming message cannot be executed");
                this.stop();
            }
            while (isAlive.get()) {

                GenericRecord sentRecord = null;
                Command type;
                try {

                    s = ss.accept();
                    InputStream inputStream = s.getInputStream();

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    copy(inputStream, baos);
                    byte[] inputStreamAsBytes = baos.toByteArray();

                    if (parseItAs(new ByteArrayInputStream(inputStreamAsBytes), schemaGoto)) {
                        type = Command.GOTO;
                        sentRecord = constructRecord(new ByteArrayInputStream(inputStreamAsBytes), schemaGoto);
                    } else if (parseItAs(new ByteArrayInputStream(inputStreamAsBytes), schemaPath)) {
                        type = Command.PATH_FOLLOWING;
                        sentRecord = constructRecord(new ByteArrayInputStream(inputStreamAsBytes), schemaPath);
                    } else {
                        type = Command.UNKNOWN;
                    }
                    Log.i(TAGsocket, "Command of type " + type.toString() + " received");
                } catch (Exception e) {
                    setResultToToast("Error in reading sent command");
                    type = Command.UNKNOWN;
                    Log.e(TAGsocket, e.toString());
                }

                if (switchB.isChecked()) {//Check if the drone is armed
                    switch (type) {
                        case GOTO:
                            GenericRecord gotoRecord = sentRecord;
                            double gotoLat = (double) gotoRecord.get("latitude");
                            double gotoLon = (double) gotoRecord.get("longitude");
                            float gotoAlt = (float) gotoRecord.get("altitude");
                            float gotoSpeed;
                            float timeout;

                            Waypoint fakeWP = new Waypoint(droneLocationLat, droneLocationLng, droneLocationAlt);
                            Waypoint realWP = new Waypoint(gotoLat, gotoLon, gotoAlt);

                            if (gotoRecord.get("timeout") != null) {
                                timeout = (float) gotoRecord.get("timeout");
                            } else {
                                timeout = dTimeout;
                            }


                            if (gotoRecord.get("speed") != null) {
                                gotoSpeed = (float) gotoRecord.get("speed");
                            } else {
                                gotoSpeed = dSpeed;
                            }

                            WaypointMissionHeadingMode mHeadingMode;
                            if (gotoRecord.get("heading") != null) {
                                fakeWP.heading = (int) gotoRecord.get("heading");
                                realWP.heading = fakeWP.heading;
                                mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                            } else {
                                mHeadingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER;
                            }


                            if (gotoRecord.get("gimbalPitch") != null) {
                                //fakeWP.gimbalPitch = -45.0f;//(int) gotoRecord.get("gimbalPitch");
                                realWP.gimbalPitch = (float) gotoRecord.get("gimbalPitch");
                                fakeWP.gimbalPitch = (float) gotoRecord.get("gimbalPitch");
                            }
                            new Handler(Looper.getMainLooper()).post(new Runnable() {

                                @Override
                                public void run() {
                                    // this will run in the main thread
                                    PrepareMap(gotoLat, gotoLon);

                                    Log.i(TAGsocket, "Inside run() realWP value -> Coordinate: " + realWP.coordinate.toString() + ", Altitude: " +
                                            realWP.altitude + ", Heading: " + realWP.heading + ", Gimbal Pitch: " + realWP.gimbalPitch);

                                    //DJISDKManager.getInstance().getMissionControl().destroyWaypointMissionOperator();
                                    adapter = new StartDJIMission(timeout, gotoSpeed, mHeadingMode);
                                    ArrayList<Waypoint> wpList = new ArrayList<>();
                                    wpList.add(fakeWP);
                                    wpList.add(realWP);
                                    adapter.execute(wpList);

                                }
                            });

                            break;
                        case PATH_FOLLOWING:

                            GenericRecord pathRecord = sentRecord;



                            break;
                        case UNKNOWN:

                    }
                }

            }
            try {
                s.close();
                ss.close();
            } catch (IOException e) {
                setResultToToast("Error in reading GOTO commands");
                Log.e(TAGsocket, "Cannot close server socket");
                Log.e(TAGsocket, e.toString());
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
