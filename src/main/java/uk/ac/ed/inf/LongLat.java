package uk.ac.ed.inf;
import java.lang.Math;

public class LongLat {
    public double longitude;
    public double latitude;

    /**
     * A constructor class used to represent 2 points. Those 2 points being longitude and latitude
     * @param longitude is a double representing the longitude of the drones position
     * @param latitude is a double representing the latitude of the drones position
     */
    public LongLat(double longitude,double latitude){
        this.longitude = longitude;
        this.latitude = latitude;
    }

    /**
     * Is a class to check if the drone is within the area that is should be confined to
     * @return a boolean true or false depending on if the drone is within the confinement zone
     */
    public boolean isConfined(){
        boolean outside = true;
        if (longitude <= -3.192473 || longitude >= -3.184319 || latitude <= 55.942617 || latitude >= 55.946233){
            outside = false;
        }
        return outside;
    }

    /**
     * Finds the distance between the drones current position and it's next one
     * @param longLat holds both the longitude and latitude of the drones next position
     * @return a double representing the distance
     */
    public double distanceTo(LongLat longLat){
        double startLong = longitude;
        double startLat = latitude;
        double endLong = longLat.longitude;
        double endLat = longLat.latitude;
        double distance = Math.sqrt(Math.pow(startLong-endLong,2)+Math.pow(startLat-endLat,2));
        return distance;
    }

    /**
     * Decides if the distance between 2 points is less than 0.00015
     * @param longLat longLat holds both the longitude and latitude of the drones next position
     * @return a boolean true or false depending on if the 2 points are close to each other
     */
    public boolean closeTo(LongLat longLat){
        boolean close = false;
        double distance = distanceTo(longLat);
        if (distance < 0.00015){
            close = true;
        }
        return close;
    }

    /**
     * Calculates the next drone position given an angle
     * @param angle is the angle which determines the direction that the drone will go
     * @return integer representing the calculated drone position
     */
    public LongLat nextPosition(int angle){
        LongLat pos = new LongLat(longitude, latitude);
        if (angle == -999){
            // don't change longitude or latitude
        }else{
            double newLong = 0.00015 * Math.cos(Math.toRadians(angle));
            double newLat = 0.00015 * Math.sin(Math.toRadians(angle));
            pos = new LongLat(longitude+newLong, latitude+newLat);
        }
        return pos;
    }
}
