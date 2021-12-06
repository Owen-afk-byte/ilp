package uk.ac.ed.inf;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.awt.*;
import java.awt.geom.Line2D;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.*;
import java.util.List;

import com.mapbox.geojson.*;
import com.mapbox.geojson.Point;


import static java.lang.Math.atan;

public class Algorithm {
    public String firstName;
    public String firstPort;
    public String secondName;
    public String secondPort;
    public String dateString;
    public String menusString;

    public Algorithm(String firstName, String firstPort, String secondName, String secondPort, String dateString, String menusString) {
        this.firstName = firstName;
        this.firstPort = firstPort;
        this.secondName = secondName;
        this.secondPort = secondPort;
        this.dateString = dateString;
        this.menusString = menusString;
    }

    private static final HttpClient client = HttpClient.newHttpClient();

    public String MainAlgorithm() {

        int numberOfMoves = 0;
        ArrayList<LongLat> dronePath = new ArrayList<>();
        LongLat nextMove = null;
        LongLat startLocation = null;
        int numberDelivered = 0;
        ArrayList<String> picked = new ArrayList<>();
        boolean currentlyDelivering = false;
        boolean pickingUp = false;
        LongLat deliveryLocation = null;
        ArrayList<LongLat> deliveryLocations = new ArrayList<>();
        String pickupID = null;
        LongLat pickupOne = null;
        LongLat pickupTwo = null;

        LongLat appleton = new LongLat(-3.186874, 55.944494);
        boolean returnedToAppleton = false;
        boolean appletonReached = false;

        boolean allDelivered = false;

        try {

            // read in everything
            Orders orders = new Orders(secondName, secondPort);
            ArrayList<ArrayList<String>> date = orders.getDates(dateString);
            HashMap<String, ArrayList<String>> details = orders.getDetails(date);


            Menus menus = new Menus(firstName, firstPort);
            int totalCost = menus.getDeliveryCost(menusString);

            Buildings buildings = new Buildings(firstName, firstPort);
            ArrayList<ArrayList<LongLat>> buildingCoordinates = buildings.getBuildings(buildings.name, buildings.port);
            ArrayList<ArrayList<LongLat>> landmarkCoordinates = buildings.getLandmarks(buildings.name, buildings.port);

            Words words = new Words(firstName, firstPort);

            //puts orders in hash table and LongLats in array
            HashMap<String, ArrayList<String>> dateHash = new HashMap<>();
            HashMap<String, LongLat> dateCoordinates = new HashMap<>();
            for (int m = 0; m < date.size(); m++) {
                ArrayList<String> row = date.get(m);
                ArrayList<String> values = new ArrayList<>();
                String orderNo = row.get(0);
                values.add(row.get(1));
                values.add(row.get(2));
                values.add(row.get(3));
                dateHash.put(orderNo, values);
                String W3W = values.get((2));
                LongLat deliverTo = words.getInfo(W3W);
                dateCoordinates.put(orderNo, deliverTo);
            }

            //find all delivery pickup LongLats for each item
            HashMap<String, ArrayList<LongLat>> allPickups = new HashMap<>();
            for (Map.Entry<String, ArrayList<String>> entry : details.entrySet()) {
                ArrayList<String> items = entry.getValue();
                ArrayList<LongLat> pickupsArray = new ArrayList<>();
                for (int k = 0; k < items.size(); k++) {
                    String getPickups = menus.getW3W(items.get(k));
                    System.out.println(getPickups);
                    LongLat W3W = words.getInfo(getPickups);
                    pickupsArray.add(W3W);
                }
                allPickups.put(entry.getKey(), pickupsArray);
            }

            //loop until deliveries are complete or drone uses all moves
            while (numberOfMoves <= 1500 && numberDelivered < date.size() && returnedToAppleton == false) {
                System.out.println(numberOfMoves);

                if (numberOfMoves == 0) {
                    startLocation = appleton;
                } else {
                    startLocation = nextMove;
                }
                /**ArrayList<LongLat> appletonRoute = AppletonRoute(startLocation, appleton, buildingCoordinates);
                if ((numberOfMoves + appletonRoute.size() > 1500)){
                    numberOfMoves = numberOfMoves - 1;
                    dronePath.remove(dronePath.size()-1);
                    dronePath.addAll(appletonRoute);
                    currentlyDelivering = false;
                    pickingUp = false;
                    returnedToAppleton = true;
                }*/
                if (currentlyDelivering == false && pickingUp == false){
                    System.out.println("size " + allPickups.size());
                    if (allPickups.size() == 0){
                        numberOfMoves = 1501;
                    }else {
                        HashMap<String, Double> jointCloseness = new HashMap<>();
                        for (Map.Entry<String, ArrayList<LongLat>> entry : allPickups.entrySet()) {
                            ArrayList<LongLat> pickupsArray = entry.getValue();
                            ArrayList<Double> closeness = new ArrayList<>();
                            for (int c = 0; c < pickupsArray.size(); c++) {
                                double close = startLocation.distanceTo(pickupsArray.get(c));
                                closeness.add(close);
                            }
                            double sumCloseness = 0;
                            for (int d = 0; d < closeness.size(); d++) {
                                double closeTemp = closeness.get(d);
                                sumCloseness = sumCloseness + closeTemp;
                            }
                            jointCloseness.put(entry.getKey(), sumCloseness);
                        }
                        String closenessLocation = null;
                        double closenessNumber = 999;
                        for (Map.Entry<String, Double> entry : jointCloseness.entrySet()) {
                            String location = entry.getKey();
                            double value = entry.getValue();
                            if (value < closenessNumber) {
                                closenessNumber = value;
                                closenessLocation = location;
                            }
                        }

                        pickupID = closenessLocation;

                        //get pickups for certain pickup id
                        //should be no more than 2 pickups
                        ArrayList<LongLat> items = allPickups.get(pickupID);
                        System.out.println(pickupID);
                        Set<LongLat> set = new HashSet<>(items);
                        items.clear();
                        items.addAll(set);
                        allPickups.remove(pickupID);

                        if (items.size() == 2) {
                            if (startLocation.distanceTo(items.get(0)) < startLocation.distanceTo(items.get(1))) {
                                pickupOne = items.get(0);
                                pickupTwo = items.get(1);
                            } else {
                                pickupOne = items.get(1);
                                pickupTwo = items.get(0);
                            }
                        } else {
                            pickupOne = items.get(0);
                            pickupTwo = null;
                        }

                        //set delivery location
                        deliveryLocation = dateCoordinates.get(pickupID);
                        dateCoordinates.remove(pickupID);

                        //find angle to get to next point
                        double xa = startLocation.longitude;
                        double ya = startLocation.latitude;
                        double xb = pickupOne.longitude;
                        double yb = pickupOne.latitude;
                        double slope = (yb - ya) / (xb - xa);
                        double angle = atan(slope);
                        int rounded = (int) (Math.round(angle / 10.0) * 10);

                        //figure out safe move
                        nextMove = SafeMove(rounded, startLocation, buildingCoordinates);
                        dronePath.add(nextMove);
                        System.out.println('a');
                        pickingUp = true;
                        numberOfMoves = numberOfMoves + 1;

                    }
                }else if (currentlyDelivering == false && pickingUp == true){
                    startLocation = nextMove;
                    if (startLocation.closeTo(pickupOne)){
                        int angle = -999;
                        System.out.println(angle);
                        nextMove = startLocation.nextPosition(angle);
                        dronePath.add(nextMove);
                        System.out.println('b');
                        numberOfMoves = numberOfMoves  +1;

                        pickupOne = pickupTwo;
                        pickupTwo = null;
                        System.out.println('l');
                        if (pickupOne==null && pickupTwo==null){
                            pickingUp = false;
                            currentlyDelivering = true;
                        }
                    }else{

                        //find angle to get to next point
                        double xa = startLocation.longitude;
                        double ya = startLocation.latitude;
                        double xb = pickupOne.longitude;
                        double yb = pickupOne.latitude;
                        System.out.println('c');
                        System.out.println(startLocation.longitude);
                        System.out.println(startLocation.latitude);
                        System.out.println(pickupOne.longitude);
                        System.out.println(pickupOne.latitude);
                        double slope = Math.atan2((yb-ya), (xb-xa));
                        float angle = (float) Math.toDegrees(slope);
                        if(angle < 0){
                            angle += 360;
                        }
                        int rounded = ((int) (Math.round(angle/10.0) * 10));
                        System.out.println(slope);
                        System.out.println(angle);
                        System.out.println(rounded);

                        nextMove = SafeMove(rounded, startLocation, buildingCoordinates);
                        dronePath.add(nextMove);
                        System.out.println('c');
                        numberOfMoves = numberOfMoves +1;
                    }
                }else if (currentlyDelivering == true && pickingUp == false) {

                    startLocation = nextMove;
                    if (startLocation.closeTo(deliveryLocation)) {
                        int angle = -999;
                        nextMove = startLocation.nextPosition(angle);
                        dronePath.add(nextMove);
                        System.out.println('d');
                        numberOfMoves = numberOfMoves + 1;

                        currentlyDelivering = false;
                    } else {


                        //find angle to get to next point
                        double xa = startLocation.longitude;
                        double ya = startLocation.latitude;
                        double xb = deliveryLocation.longitude;
                        double yb = deliveryLocation.latitude;
                        System.out.println('e');
                        System.out.println(startLocation.longitude);
                        System.out.println(startLocation.latitude);
                        System.out.println(deliveryLocation.longitude);
                        System.out.println(deliveryLocation.latitude);
                        double slope = Math.atan2((yb-ya), (xb-xa));
                        float angle = (float) Math.toDegrees(slope);
                        if(angle < 0){
                            angle += 360;
                        }
                        int rounded = ((int) (Math.round(angle/10.0) * 10));

                        nextMove = SafeMove(rounded, startLocation, buildingCoordinates);
                        dronePath.add(nextMove);
                        System.out.println('e');
                        numberOfMoves = numberOfMoves + 1;
                    }
                }
            }
            System.out.println("size " + allPickups.size());
        } catch (NullPointerException e) {
            System.out.println("Null Pointer Exception");
            System.exit(1); // Exit the application
        }

        int noPoints = 0;
        ArrayList<Point> points = new ArrayList<>();
        for (int d = 0; d < dronePath.size(); d++) {
            LongLat longLat = dronePath.get(d);
            Point point = Point.fromLngLat(longLat.longitude, longLat.latitude);
            points.add(point);
            noPoints = noPoints +1;
        }

        System.out.println("noPoints " + noPoints);
        LineString lineString = LineString.fromLngLats(points);
        Geometry geometry = (Geometry) lineString;
        Feature feature = Feature.fromGeometry(geometry);
        FeatureCollection featureCollection = FeatureCollection.fromFeature(feature);
        String jsonString = featureCollection.toJson();

        System.out.println(dronePath);
        return jsonString;
    }

    /**
    public  ArrayList<LongLat> AppletonRoute(LongLat startLocation, LongLat appleton, ArrayList<ArrayList<LongLat>> buildingCoordinates) {
        LongLat tempMove = null;
        ArrayList<LongLat> tempPath = new ArrayList<>();
        int index = 0;
        boolean close = false;
        while (close == false) {
            //find angle to get to next point
            if (index == 0){
                //do nothing
            }else {
                startLocation = tempMove;
            }
            double xa = startLocation.longitude;
            double ya = startLocation.latitude;
            double xb = appleton.longitude;
            double yb = appleton.latitude;
            System.out.println("apple");
            System.out.println(startLocation.longitude);
            System.out.println(startLocation.latitude);
            System.out.println(appleton.longitude);
            System.out.println(appleton.latitude);
            double slope = Math.atan2((yb-ya), (xb-xa));
            float angle = (float) Math.toDegrees(slope);
            if(angle < 0){
                angle += 360;
            }
            angle = angle % 360;
            int rounded = ((int) (Math.round(angle/10.0) * 10));
            System.out.println(slope);
            System.out.println(angle);
            System.out.println(rounded);

            tempMove = SafeMove(rounded, startLocation, buildingCoordinates);
            tempPath.add(tempMove);
            index = index + 1;
            if (tempMove.closeTo(appleton)){
                close = true;
            }
            System.out.println(tempMove.longitude);
            System.out.println(tempMove.latitude);
            System.out.println(appleton.longitude);
            System.out.println(appleton.latitude);
            System.out.println(close);
            System.out.println("apple");
        }
        return tempPath;
    }*/

    public LongLat SafeMove(int angle, LongLat startLocation, ArrayList<ArrayList<LongLat>> buildingCoordinates) {

        float startLocationX = (float) startLocation.longitude;
        float startLocationY = (float) startLocation.latitude;

        System.out.println(angle);
        LongLat nextMove = startLocation.nextPosition(angle);
        boolean moveOkay = false;

        while (moveOkay == false) {
            boolean canMove = true;
            float nextMoveX = (float) nextMove.longitude;
            float nextMoveY = (float) nextMove.latitude;
            for (int m = 0; m < buildingCoordinates.size(); m++) {
                ArrayList<LongLat> building = buildingCoordinates.get(m);
                for (int n = 0; n < building.size(); n++) {
                    LongLat cornerA;
                    LongLat cornerB;
                    if (n == (building.size() - 1)) {
                        cornerA = building.get(n);
                        cornerB = building.get(0);
                    } else {
                        cornerA = building.get(n);
                        cornerB = building.get(n + 1);
                    }
                    float cornerAX = (float) cornerA.longitude;
                    float cornerAY = (float) cornerA.latitude;
                    float cornerBX = (float) cornerB.longitude;
                    float cornerBY = (float) cornerB.latitude;

                    Line2D line1 = new Line2D.Float(cornerAX, cornerAY, cornerBX, cornerBY);
                    Line2D line2 = new Line2D.Float(startLocationX, startLocationY, nextMoveX, nextMoveY);
                    boolean intersects = line2.intersectsLine(line1);

                    if (intersects == true) {
                        canMove = false;
                    }
                }
            }
            if (canMove == true && nextMove.isConfined()==true ) {
                moveOkay = true;
            } else {
                angle = angle - 10;
                if(angle < 0){
                    angle += 360;
                }
                angle = angle % 360;
                nextMove = SafeMove(angle, startLocation, buildingCoordinates);
            }
        }
        return nextMove;
    }
}
