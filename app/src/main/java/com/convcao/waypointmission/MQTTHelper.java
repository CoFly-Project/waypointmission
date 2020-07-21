package com.convcao.waypointmission;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

import static com.convcao.waypointmission.MainActivity.TAG;


public class MQTTHelper {

    public MqttAndroidClient mMqttAndroidClient;

    final String serverIp;

    final String canonicalName;
    final String clientId;
    private String missionStartTopic;
    private String missionAbortTopic;
    
    private DroneState mDroneState = DroneState.getInstance();
    Handler mHandler;

    ObjectMapper mObjectMapper = new ObjectMapper();

    public MQTTHelper(Context context, String canonicalName, String clientId, String serverIp, String serverPort) {

        this.serverIp = "tcp://" + serverIp + ":" + serverPort;
        this.clientId = clientId;
        this.canonicalName = canonicalName;
//
        this.missionStartTopic =  "missionStart/" + canonicalName;
        this.missionAbortTopic = "missionAbort/" + canonicalName;

        mMqttAndroidClient = new MqttAndroidClient(context, this.serverIp, this.clientId);


        mMqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.w("mqtt", serverURI);
            }

            @Override
            public void connectionLost(Throwable cause) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.w("Mqtt", message.toString());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });
        connect();
    }

    public void setCallback(MqttCallbackExtended callback) {
        mMqttAndroidClient.setCallback(callback);
    }

    private void connect() {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(true);

        try {
            mMqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mMqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeToTopic();

                    mHandler = new Handler();
                    mHandler.post(runnableCode);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.w("Mqtt", "Failed to connect to: " + serverIp + exception.toString());
                }
            });
        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    private void subscribeToTopic() {
        try {
            mMqttAndroidClient.subscribe(missionStartTopic, 2, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.w("Mqtt","Subscribed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.w("Mqtt", "Subscribed fail!");
                }
            });

            mMqttAndroidClient.subscribe(missionAbortTopic, 2, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.w("Mqtt","Subscribed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.w("Mqtt", "Subscribed fail!");
                }
            });


        } catch (MqttException ex) {
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }

    public void publishToTopic() {

        try {
            String message = "milhsa";
            mMqttAndroidClient.publish("hello/bro", new MqttMessage(message.getBytes()));
        } catch (MqttException ex) {
            System.err.println("Exception while publishing to hello/bro");
            ex.printStackTrace();
        }
    }
    public void publishToTelemetryTopic(double latitude, double longitude,
                                        double alt, int heading) {

        TelemetryMessage telemetryMessage = new TelemetryMessage();
        telemetryMessage.setSourceSystem(canonicalName);
        telemetryMessage.setDestinationSystem("choosepath-backend");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
//        Date latestTelemetry = new Date(currentTime);
        Log.w(TAG, "in notify telemetry , current time: " + sdf.format(new Date()) );
//                + "latestTelemetryMessageTimestamp: " + sdf.format(latestTelemetry));
        Date currentTime = Calendar.getInstance().getTime();
        Log.e(TAG, "TIME : " + currentTime);


        telemetryMessage.setTimestamp(System.currentTimeMillis());
        telemetryMessage.setLatitude(latitude);
        telemetryMessage.setLongitude(longitude);
        telemetryMessage.setAltitude(alt);
        telemetryMessage.setHeading(heading);

        try {
            mMqttAndroidClient.publish("telemetry/" + canonicalName, new MqttMessage(mObjectMapper.writeValueAsBytes(telemetryMessage)));
        } catch (MqttException | JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public void publishToCameraTopic(double latitude, double longitude,
                                     double alt, float gimbalPitch, int heading,
                                     float velocityX, float velocityY, float velocityZ,
                                     byte[] image) {

        CameraMessage cameraMessage = new CameraMessage();
        cameraMessage.setSourceSystem(canonicalName);
        cameraMessage.setDestinationSystem("choosepath-backend");
        cameraMessage.setTimestamp(System.currentTimeMillis());
        cameraMessage.setLatitude(latitude);
        cameraMessage.setLongitude(longitude);
        cameraMessage.setAltitude(alt);
        cameraMessage.setGimbalPitch(gimbalPitch);
        cameraMessage.setHeading(heading);
        cameraMessage.setVelocityX(velocityX);
        cameraMessage.setVelocityY(velocityY);
        cameraMessage.setVelocityZ(velocityZ);

        cameraMessage.setImage(image);

        try {
            mMqttAndroidClient.publish("camera/" + canonicalName, new MqttMessage(mObjectMapper.writeValueAsBytes(cameraMessage)));
        } catch (MqttException | JsonProcessingException e) {
            e.printStackTrace();
        }

    }

    public void publishToMissionStatusTopic(UUID missionId, MissionStatus missionStatus) {
        try {
            MissionStatusMessage missionStatusMessage = new MissionStatusMessage();
            missionStatusMessage.setMissionId(missionId);
            missionStatusMessage.setDestinationSystem("choosepath-backend");
            missionStatusMessage.setSourceSystem(canonicalName);
            missionStatusMessage.setMissionStatus(missionStatus);
            missionStatusMessage.setTimestamp(System.currentTimeMillis());

            Log.i(TAG, "missionStatus/" + canonicalName + "      -> " + missionStatus);
            mMqttAndroidClient.publish("missionStatus/" + canonicalName, new MqttMessage(mObjectMapper.writeValueAsBytes(missionStatusMessage)));
        } catch (MqttException | JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            // Do something here on the main thread
//            publishToTelemetryTopic(mDroneState.getLatitude(), mDroneState.getLongitude(),
//                    mDroneState.getAltitude(), mDroneState.getHeading());
//            publishToCameraTopic();
            // Repeat this the same runnable code block again another 2 seconds
            // 'this' is referencing the Runnable object
//            mHandler.postDelayed(this, 500);
        }
    };

}
