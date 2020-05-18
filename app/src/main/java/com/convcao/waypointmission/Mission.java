package com.convcao.waypointmission;

import android.os.Handler;
import android.util.Log;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Mission {

    private static final String TAG = "Mission";
    private int missionId;

    //m/s
    private float speed;

    //seconds
    private float timeout;

    //m
    private float cornerRadius;

    private float gimbalPitch;

    private List<Waypoint> waypoints;

    private DroneState mDroneState;
    private Handler mHandler;

    private int index;

    public Mission(int missionId, float speed, float timeout, float cornerRadius, float gimbalPitch, List<Waypoint> waypoints) {
        Log.w(TAG, "Mission: waypoints" + waypoints.toString());
        this.missionId = missionId;
        this.speed = speed;
        this.timeout = timeout;
        this.cornerRadius = cornerRadius;
        this.gimbalPitch = gimbalPitch;
        this.waypoints = waypoints;
    }

    public void deploy() {

        index = 0;

        mHandler = new Handler();
        mHandler.post(runnableCode);

    }

    public void abort() {
        waypoints.clear();
        mHandler.removeCallbacks(runnableCode);
    }

    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {

            // Do something here on the main thread
            move();
            // Repeat this the same runnable code block again another 2 seconds
            // 'this' is referencing the Runnable object
            mHandler.postDelayed(this, 5000);

            if(index >= waypoints.size()) {
                mHandler.removeCallbacks(this);
            }
        }
    };

    private void move() {


        mDroneState = DroneState.getInstance();
        mDroneState.setLatitude(waypoints.get(index).getLatitude());
        mDroneState.setLongitude(waypoints.get(index).getLongitude());
        mDroneState.setAltitude(waypoints.get(index).getAltitude());
        mDroneState.setGimbalPitch(gimbalPitch);

        Log.d(TAG, "move: moved");
        index = (index + 1) ;

    }


}
