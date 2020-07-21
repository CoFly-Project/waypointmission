package com.convcao.waypointmission;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

import static com.convcao.waypointmission.Dist.geo;

@Getter
@Setter
public class Mission {

    private static final String TAG = "Mission";
    private UUID missionId;

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

    public Mission(UUID missionId, float speed, float timeout, float cornerRadius, float gimbalPitch, List<Waypoint> waypoints) {
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

    public static void transformWaypoints(List<Waypoint> wps, float minimumWaypointCurve , float gimbalPitch, float cornerRadius,
                                          ArrayList<dji.common.mission.waypoint.Waypoint> waypointList,
                                          ArrayList<dji.common.mission.waypoint.Waypoint> waypointDisplayList) {
        try {

            int wpIndex = 0;
            double[] prevWP = new double[3];
            float prevCorner = minimumWaypointCurve;

            for (Waypoint wp : wps) {

                dji.common.mission.waypoint.Waypoint waypoint = new dji.common.mission.waypoint.Waypoint(wp.getLatitude(),
                       wp.getLongitude(), (float) wp.getAltitude());

                waypoint.gimbalPitch = gimbalPitch;

                if (wpIndex == 0 || wpIndex == (wps.size() - 1)) {

                    waypoint.cornerRadiusInMeters = minimumWaypointCurve;
                } else { //https://developer.dji.com/mobile-sdk/documentation/cn/faq/cn/api-reference/ios-api/Components/Missions/DJIWaypoint.html#djiwaypoint_cornerradiusinmeters_inline

                    double dist = geo(prevWP, new double[]{waypoint.coordinate.getLatitude(),
                            waypoint.coordinate.getLongitude(), waypoint.altitude});

                    if (prevCorner + cornerRadius < dist) {
                        waypoint.cornerRadiusInMeters = cornerRadius;
                    } else if ((dist - 2.0 * minimumWaypointCurve) > prevCorner) {
                        waypoint.cornerRadiusInMeters = (float) (dist - minimumWaypointCurve - prevCorner);
                    } else {
                        float newRadius = (float) ((dist / 2.0) - minimumWaypointCurve);
                        waypoint.cornerRadiusInMeters = newRadius;
                        waypointList.get(waypointList.size() - 1).cornerRadiusInMeters = newRadius;
                        waypointDisplayList.get(waypointDisplayList.size() - 1).cornerRadiusInMeters = newRadius;
                    }
                }

                prevWP[0] = waypoint.coordinate.getLatitude();
                prevWP[1] = waypoint.coordinate.getLongitude();
                prevWP[2] = waypoint.altitude;
                prevCorner = waypoint.cornerRadiusInMeters;
                wpIndex++;
                waypointList.add(waypoint);
                waypointDisplayList.add(waypoint);
            }
//            typeOfMission = MainActivity.TypeOfMission.Waypoint;
        } catch (Exception e) {
//            typeOfMission = MainActivity.TypeOfMission.NoMission;
            e.printStackTrace();
        }
    }


}
