package com.convcao.waypointmission;

public class Dist {

    public static double geo(double[] p1, double[] p2){
        double distance = 0;
        final int R = 6371; // Radius of the earth

        Double latDistance = Math.toRadians(p2[0] - p1[0]);
        Double lonDistance = Math.toRadians(p2[1] - p1[1]);
        Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(p1[0])) * Math.cos(Math.toRadians(p2[0]))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        distance = R * c * 1000; // convert to meters

        return distance;
    }

}
