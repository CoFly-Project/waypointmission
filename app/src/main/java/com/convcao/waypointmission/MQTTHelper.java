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

import java.util.Random;


public class MQTTHelper {

    public MqttAndroidClient mMqttAndroidClient;

//    final String serverUri = "tcp://192.168.1.8:1883";
final String serverIp;


    final String canonicalName = "dji.phantom.4.pro.hawk.1";

//    final String clientId = "ExampleAndroidClient2";
final String clientId;
//    final String missionStartTopic = "missionStart/" + canonicalName;
    private String missionStartTopic;
//    final String missionAbortTopic = "missionAbort/" + canonicalName;

    private String missionAbortTopic;
    
    private DroneState mDroneState = DroneState.getInstance();
    Handler mHandler;

    ObjectMapper mObjectMapper = new ObjectMapper();

//    ImageProvider mImageProvider;

    public MQTTHelper(Context context, String canonicalName, String clientId, String serverIp) {

//        mImageProvider = new ImageProvider(context);

//        this.canonicalName = canonicalName;
////        this.clientId = clientId;
////        this.serverIp = "tcp://" + serverIp + ":1883";
        this.serverIp = "tcp://" + serverIp + ":1883";
        this.clientId = clientId;
//
        this.missionStartTopic =  "missionStart/" + canonicalName;
        this.missionAbortTopic = "missionAbort/" + canonicalName;

//        mMqttAndroidClient = new MqttAndroidClient(context, "tcp://" + serverIp + ":1883", clientId);
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
        telemetryMessage.setTimestamp(System.currentTimeMillis());
        telemetryMessage.setLatitude(latitude);
        telemetryMessage.setLongitude(longitude);
        telemetryMessage.setAltitude(alt);
        telemetryMessage.setHeading(heading);
        telemetryMessage.setMissionId(1);

        try {
            mMqttAndroidClient.publish("telemetry/" + canonicalName, new MqttMessage(mObjectMapper.writeValueAsBytes(telemetryMessage)));
        } catch (MqttException | JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public void publishToCameraTopic(double latitude, double longitude,
                                     double alt, float gimbalPitch, int heading,
                                     float velocityX, float velocityY, float velocityZ, byte[] image) {

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

    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            // Do something here on the main thread
            publishToTelemetryTopic(mDroneState.getLatitude(), mDroneState.getLongitude(),
                    mDroneState.getAltitude(), mDroneState.getHeading());
//            publishToCameraTopic();
            // Repeat this the same runnable code block again another 2 seconds
            // 'this' is referencing the Runnable object
            mHandler.postDelayed(this, 500);
        }
    };

}
