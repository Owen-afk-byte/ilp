package uk.ac.ed.inf;

import java.awt.geom.Line2D;
import java.net.http.HttpClient;
import java.sql.*;
import java.util.*;

import com.mapbox.geojson.*;
import com.mapbox.geojson.Point;

public class Algorithm {
    public String day;
    public String month;
    public String year;
    public String firstPort;
    public String secondPort;


    public Algorithm(String day, String month, String year, String firstPort, String secondPort) {
        this.day = day;
        this.month = month;
        this.year = year;
        this.firstPort = firstPort;
        this.secondPort = secondPort;
    }

    private static final HttpClient client = HttpClient.newHttpClient();

    ArrayList<LongLat> doNotEnter = new ArrayList<>();
    ArrayList<LongLat> dronePath = new ArrayList<>();
    int numberOfMoves = 0;
    int landmarkMoves = 0;

    public String MainAlgorithm() {


        LongLat nextMove = null;
        LongLat startLocation = null;
        int numberDelivered = 0;
        ArrayList<String> picked = new ArrayList<>();

        LongLat deliveryLocation = null;
        LongLat pickupOne = null;
        LongLat pickupTwo = null;
        LongLat landmark = null;

        double landmarkDistance = 0;

        String pickupID = null;
        ArrayList<LongLat> deliveryLocations = new ArrayList<>();

        LongLat appleton = new LongLat(-3.186874, 55.944494);
        boolean returnToAppleton = false;
        boolean appletonReached = false;
        boolean currentlyDelivering = false;
        boolean pausedDelivery = false;
        boolean pickingUp = false;
        boolean pausedPickup = true;
        boolean allDelivered = false;
        boolean headingLandmark = false;

        HashMap<Integer, Double> moves = new HashMap<>();
        HashMap<Integer, Double> movesAgain = new HashMap<>();
        HashMap<String, List<String>> deliveriesHashMap = new HashMap<>();
        HashMap<Integer, List<String>> flightPathHashMap = new HashMap<>();

        ArrayList<LongLat> doNotEnter = new ArrayList<>();

        try {

            // read in everything
            Orders orders = new Orders(secondPort);
            ArrayList<ArrayList<String>> date = orders.getDates(year, month, day);
            HashMap<String, ArrayList<String>> details = orders.getDetails(date);


            Menus menus = new Menus(firstPort);

            Buildings buildings = new Buildings(firstPort);
            ArrayList<ArrayList<LongLat>> buildingCoordinates = buildings.getBuildings(buildings.port);
            ArrayList<LongLat> landmarkCoordinates = buildings.getLandmarks(buildings.port);

            Words words = new Words(firstPort);

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

            if (appletonReached == true){
                String jdbcString = "jdbc:derby://localhost:" + "port" + "/blabla";        //<----- change this jdbc string
                Connection conn = DriverManager.getConnection(jdbcString);
                Statement statement = conn.createStatement();
                DatabaseMetaData databaseMetaData = conn.getMetaData();
                ResultSet resultSet = databaseMetaData.getTables(null, null, "DELIVERIES", null);
                if (resultSet.next()){
                    statement.execute("drop table deliveries");
                }
                statement.execute("create table deliveries(" + "orderNo char(8), " + "deliveredTo varchar(19), " + "costInPence int)");

                ResultSet resultSet2 = databaseMetaData.getTables(null, null, "FLIGHTPATH", null);
                if (resultSet.next()){
                    statement.execute("drop table flightpath");
                }
                statement.execute("create table flightpath(" + "orderNo char(8), " + "fromLongitude double, " + "fromLatitude double, " + "angle integer, " + "toLongitude double, " + "toLatitude double)");
            }

            //loop until deliveries are complete or drone uses all moves
            while (numberOfMoves < 1500 && numberDelivered < date.size() && appletonReached == false) {
                System.out.println(numberOfMoves);
                System.out.println('x');
                if (numberOfMoves == 0) {
                    startLocation = appleton;
                } else if (allDelivered == true) {
                    returnToAppleton = true;
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
                System.out.println('x');
                if (returnToAppleton == true) {

                    System.out.println("keeman");
                    startLocation = nextMove;


                    if (startLocation.closeTo(appleton)) {
                        returnToAppleton = false;
                        appletonReached = true;
                    }else{

                        //figure out next move
                        moves = Moves(startLocation, appleton, buildingCoordinates);

                        //finds the closest angle to the destination
                        double finalDistance = 999;
                        int finalMove = 0;
                        int angleMove;
                        double distanceMove;
                        for (Map.Entry<Integer, Double> entry : moves.entrySet()) {
                            angleMove = entry.getKey();
                            distanceMove = entry.getValue();
                            if (distanceMove < finalDistance) {
                                finalDistance = distanceMove;
                                finalMove = angleMove;
                            }
                        }
                        LongLat checkMove = startLocation.nextPosition(finalMove);
                        double checkMoveLong = checkMove.longitude;
                        double checkMoveLat = checkMove.latitude;
                        if (dronePath.size()>3) {
                            if ((dronePath.get(dronePath.size() - 3).longitude == checkMoveLong && dronePath.get(dronePath.size() - 3).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 2).longitude == checkMoveLong && dronePath.get(dronePath.size() - 2).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 1).longitude == checkMoveLong && dronePath.get(dronePath.size() - 1).latitude == checkMoveLat)) {
                                doNotEnter.add(dronePath.get(dronePath.size()-1));

                                landmark = ClosestLandmark(startLocation, buildingCoordinates, landmarkCoordinates);
                                landmarkDistance = startLocation.distanceTo(landmark);
                                pickingUp = false;
                                pausedPickup = true;
                                headingLandmark = true;

                            }
                        }
                        //figure out next move
                        moves = Moves(startLocation, appleton, buildingCoordinates);

                        //finds the closest angle to the destination
                        finalDistance = 999;
                        finalMove = 0;
                        for (Map.Entry<Integer, Double> entry : moves.entrySet()) {
                            angleMove = entry.getKey();
                            distanceMove = entry.getValue();
                            if (distanceMove < finalDistance) {
                                finalDistance = distanceMove;
                                finalMove = angleMove;
                            }
                        }

                        nextMove = startLocation.nextPosition(finalMove);
                        dronePath.add(nextMove);
                        numberOfMoves = numberOfMoves +1;
                    }
                }
                else if (currentlyDelivering == false && pickingUp == false && headingLandmark == false){

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

                        //figure out next move
                        moves = Moves(startLocation, pickupOne, buildingCoordinates);
                        double finalDistance = 999;
                        int finalMove = 0;
                        for (Map.Entry<Integer, Double> entry : moves.entrySet()) {
                            int angleMove = entry.getKey();
                            double distanceMove = entry.getValue();
                            if (distanceMove < finalDistance) {
                                finalDistance = distanceMove;
                                finalMove = angleMove;
                            }
                        }
                        nextMove = startLocation.nextPosition(finalMove);

                        dronePath.add(nextMove);
                        System.out.println('a');
                        System.out.println('l');
                        pickingUp = true;
                        System.out.println('l');
                        numberOfMoves = numberOfMoves + 1;

                    }

                }else if (currentlyDelivering == false && pickingUp == true){
                    System.out.println('x');
                    startLocation = nextMove;
                    System.out.println(startLocation);
                    System.out.println(pickupOne);
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

                        //figure out next move
                        moves = Moves(startLocation, pickupOne, buildingCoordinates);
                        System.out.println("finalDistance zdfbsfbs " + startLocation.longitude+ " " + startLocation.latitude);
                        System.out.println("finalMove fdbdbds " + pickupOne.longitude+ " " + pickupOne.latitude);

                        //finds the closest angle to the destination
                        double finalDistance = 999;
                        int finalMove = 0;
                        int angleMove;
                        double distanceMove;
                        for (Map.Entry<Integer, Double> entry : moves.entrySet()) {
                            angleMove = entry.getKey();
                            distanceMove = entry.getValue();
                            if (distanceMove < finalDistance) {
                                finalDistance = distanceMove;
                                finalMove = angleMove;
                            }
                        }
                        LongLat checkMove = startLocation.nextPosition(finalMove);
                        double checkMoveLong = checkMove.longitude;
                        double checkMoveLat = checkMove.latitude;
                        if (dronePath.size()>3) {
                            if ((dronePath.get(dronePath.size() - 3).longitude == checkMoveLong && dronePath.get(dronePath.size() - 3).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 2).longitude == checkMoveLong && dronePath.get(dronePath.size() - 2).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 1).longitude == checkMoveLong && dronePath.get(dronePath.size() - 1).latitude == checkMoveLat)) {
                                doNotEnter.add(dronePath.get(dronePath.size()-1));
                                numberOfMoves = numberOfMoves - 3;
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                startLocation = (dronePath.get(dronePath.size()-1));
                                landmark = ClosestLandmark(startLocation, buildingCoordinates, landmarkCoordinates);
                                landmarkDistance = startLocation.distanceTo(landmark);
                                pickingUp = false;
                                pausedPickup = true;
                                headingLandmark = true;

                            }
                        }
                        System.out.println("THE ANGLE " + finalMove);

                        //figure out next move
                        moves = Moves(startLocation, pickupOne, buildingCoordinates);
                        System.out.println("finalDistance zdfbsfbs " + startLocation.longitude+ " " + startLocation.latitude);
                        System.out.println("finalMove fdbdbds " + pickupOne.longitude+ " " + pickupOne.latitude);

                        //finds the closest angle to the destination
                        finalDistance = 999;
                        finalMove = 0;
                        for (Map.Entry<Integer, Double> entry : moves.entrySet()) {
                            angleMove = entry.getKey();
                            distanceMove = entry.getValue();
                            if (distanceMove < finalDistance) {
                                finalDistance = distanceMove;
                                finalMove = angleMove;
                            }
                        }

                        nextMove = startLocation.nextPosition(finalMove);
                        dronePath.add(nextMove);
                        System.out.println('c');
                        numberOfMoves = numberOfMoves +1;
                    }
                }else if (currentlyDelivering == true && pickingUp == false) {

                    startLocation = nextMove;
                    if (startLocation.closeTo(deliveryLocation)) {

                        ArrayList<String> values = dateHash.get(pickupID);
                        String deliverTo = values.get(2);

                        ArrayList<String> items = details.get(pickupID);
                        String[] itemsArray = items.toArray(new String[0]);
                        int totalCost = menus.getDeliveryCost(itemsArray);

                        ArrayList<String> deliveriesList = new ArrayList<>();
                        deliveriesList.add(deliverTo);
                        deliveriesList.add(String.valueOf(totalCost));
                        deliveriesHashMap.put(pickupID, deliveriesList);

                        int angle = -999;
                        nextMove = startLocation.nextPosition(angle);
                        dronePath.add(nextMove);
                        System.out.println('d');
                        numberOfMoves = numberOfMoves + 1;

                        currentlyDelivering = false;

                        if (dateCoordinates.isEmpty()) {
                            allDelivered = true;
                        }


                    } else {


                        //figure out next move
                        moves = Moves(startLocation, deliveryLocation, buildingCoordinates);
                        double finalDistance = 999;
                        int finalMove = 0;
                        for (Map.Entry<Integer, Double> entry : moves.entrySet()) {
                            int angleMove = entry.getKey();
                            double distanceMove = entry.getValue();
                            if (distanceMove < finalDistance) {
                                finalDistance = distanceMove;
                                finalMove = angleMove;
                            }
                        }
                        LongLat checkMove = startLocation.nextPosition(finalMove);
                        double checkMoveLong = checkMove.longitude;
                        double checkMoveLat = checkMove.latitude;
                        if (dronePath.size()>3) {
                            if ((dronePath.get(dronePath.size() - 3).longitude == checkMoveLong && dronePath.get(dronePath.size() - 3).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 2).longitude == checkMoveLong && dronePath.get(dronePath.size() - 2).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 1).longitude == checkMoveLong && dronePath.get(dronePath.size() - 1).latitude == checkMoveLat)) {
                                System.out.println("hello there");
                                doNotEnter.add(dronePath.get(dronePath.size()-1));
                                numberOfMoves = numberOfMoves - 3;
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                startLocation = (dronePath.get(dronePath.size()-1));
                                landmark = ClosestLandmark(startLocation, buildingCoordinates, landmarkCoordinates);
                                landmarkDistance = startLocation.distanceTo(landmark);
                                currentlyDelivering = false;
                                pausedDelivery = true;
                                headingLandmark = true;

                            }
                        }

                        //figure out next move
                        moves = Moves(startLocation, deliveryLocation, buildingCoordinates);
                        finalDistance = 999;
                        finalMove = 0;
                        for (Map.Entry<Integer, Double> entry : moves.entrySet()) {
                            int angleMove = entry.getKey();
                            double distanceMove = entry.getValue();
                            if (distanceMove < finalDistance) {
                                finalDistance = distanceMove;
                                finalMove = angleMove;
                            }
                        }

                        System.out.println("THE ANGLE " + finalMove);
                        nextMove = startLocation.nextPosition(finalMove);
                        dronePath.add(nextMove);
                        System.out.println('e');
                        numberOfMoves = numberOfMoves + 1;



                    }
                }else if (currentlyDelivering == false && pickingUp == false && headingLandmark == true) {
                    System.out.println("landmark " + landmark.longitude + " " + landmark.latitude);
                    startLocation = nextMove;
                    if (landmarkMoves < 15) {

                        moves = Moves(startLocation, landmark, buildingCoordinates);
                        double finalDistance = 999;
                        int finalMove = 0;
                        for (Map.Entry<Integer, Double> entry : moves.entrySet()) {
                            int angleMove = entry.getKey();
                            double distanceMove = entry.getValue();
                            if (distanceMove < finalDistance) {
                                finalDistance = distanceMove;
                                finalMove = angleMove;
                            }
                        }
                        LongLat checkMove = startLocation.nextPosition(finalMove);
                        double checkMoveLong = checkMove.longitude;
                        double checkMoveLat = checkMove.latitude;
                        if (dronePath.size()>3) {
                            if ((dronePath.get(dronePath.size() - 3).longitude == checkMoveLong && dronePath.get(dronePath.size() - 3).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 2).longitude == checkMoveLong && dronePath.get(dronePath.size() - 2).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 1).longitude == checkMoveLong && dronePath.get(dronePath.size() - 1).latitude == checkMoveLat)) {
                                doNotEnter.add(dronePath.get(dronePath.size()-1));
                                doNotEnter.add(dronePath.get(dronePath.size()-2));
                                doNotEnter.add(dronePath.get(dronePath.size()-3));
                                numberOfMoves = numberOfMoves - 3;
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                startLocation = (dronePath.get(dronePath.size()-1));
                                landmark = ClosestLandmark(startLocation, buildingCoordinates, landmarkCoordinates);
                                landmarkDistance = startLocation.distanceTo(landmark);

                            }
                        }

                        //figure out next move
                        moves = Moves(startLocation, landmark, buildingCoordinates);
                        finalDistance = 999;
                        finalMove = 0;
                        for (Map.Entry<Integer, Double> entry : moves.entrySet()) {
                            int angleMove = entry.getKey();
                            double distanceMove = entry.getValue();
                            if (distanceMove < finalDistance) {
                                finalDistance = distanceMove;
                                finalMove = angleMove;
                            }
                        }

                        nextMove = startLocation.nextPosition(finalMove);

                        dronePath.add(nextMove);
                        System.out.println('x');
                        numberOfMoves = numberOfMoves + 1;
                        landmarkMoves = landmarkMoves + 1;
                    }else{
                        landmarkMoves = 0;
                        headingLandmark = false;
                        if (pausedDelivery == true){
                            currentlyDelivering = true;
                            pickingUp = false;
                            pausedDelivery = false;
                        }else if (pausedPickup == true){
                            currentlyDelivering = false;
                            pickingUp = true;
                            pausedPickup = false;
                        }
                    }
                    System.out.println("currentlyDelivering " + currentlyDelivering);
                    System.out.println("pickingUp " + pickingUp);
                    System.out.println("headingLandmark " + headingLandmark);

                }
            }
            System.out.println("size " + allPickups.size());
        } catch (NullPointerException e) {
            System.err.println("Null Pointer Exception");
            System.exit(1); // Exit the application
        } catch (SQLException e) {
            System.err.println("SQLException");
            System.exit(1); // Exit the application
        }

        ArrayList<LongLat> finalDronePath = new ArrayList<>();
        finalDronePath.add(appleton);
        finalDronePath.addAll(dronePath);

        int noPoints = 0;
        ArrayList<Point> points = new ArrayList<>();
        for (int d = 0; d < finalDronePath.size(); d++) {
            LongLat longLat = finalDronePath.get(d);
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

        System.out.println(finalDronePath);
        return jsonString;
    }


    /**public  ArrayList<LongLat> AppletonRoute(LongLat startLocation, LongLat appleton, ArrayList<ArrayList<LongLat>> buildingCoordinates) {
        LongLat tempMove = null;
        ArrayList<LongLat> tempPath = new ArrayList<>();
        int index = 0;
        boolean close = false;
        while (close == false) {
            //find angle to get to next point
            if (index != 0){
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
    public LongLat ClosestLandmark(LongLat startLocation, ArrayList<ArrayList<LongLat>> buildingCoordinates, ArrayList<LongLat> landmarkCoordinates) {

        LongLat firstLandmark = landmarkCoordinates.get(0);
        LongLat secondLandmark = landmarkCoordinates.get(1);
        LongLat currentLandmark;
        if (startLocation.distanceTo(firstLandmark) < startLocation.distanceTo(secondLandmark)){
            currentLandmark = firstLandmark;
        }else{
            currentLandmark = secondLandmark;
        }

        if (dronePath.size()>3) {
            if ((dronePath.get(dronePath.size() - 3).longitude == startLocation.longitude && dronePath.get(dronePath.size() - 3).latitude == startLocation.latitude) || (dronePath.get(dronePath.size() - 2).longitude == startLocation.longitude && dronePath.get(dronePath.size() - 2).latitude == startLocation.latitude) || (dronePath.get(dronePath.size() - 1).longitude == startLocation.longitude && dronePath.get(dronePath.size() - 1).latitude == startLocation.latitude) ) {
                if (currentLandmark == firstLandmark){
                    currentLandmark = secondLandmark;
                }else{
                    currentLandmark = firstLandmark;
                }
            }
        }
        System.out.println("landmark");
        System.out.println("keeman");

    return currentLandmark;
    }

    public boolean SafeMove(int angle, LongLat startLocation, ArrayList<ArrayList<LongLat>> buildingCoordinates) {

        LongLat nextMove = startLocation.nextPosition(angle);
        boolean moveOkay;
        boolean noEntry = false;
        float startLocationX = (float) startLocation.longitude;
        float startLocationY = (float) startLocation.latitude;
        float nextMoveX = (float) nextMove.longitude;
        float nextMoveY = (float) nextMove.latitude;


        boolean canMove = true;
        for (int m = 0; m < buildingCoordinates.size(); m++) {
            ArrayList<LongLat> building = buildingCoordinates.get(m);
            for (int n = 0; n < building.size(); n++) {
                LongLat cornerA;
                LongLat cornerB;
                if (n == (building.size() - 1)) {
                    cornerA = building.get(n);
                    cornerB = building.get(1);
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
                boolean contains = line1.contains(nextMoveX, nextMoveY);


                if (intersects == true || contains == true) {
                    canMove = false;
                }
            }
        }

        for (int a = 0; a < doNotEnter.size(); a++) {
            LongLat entry = doNotEnter.get(a);
            if (entry.longitude == nextMoveX && entry.latitude == nextMoveY){
                noEntry = true;
            }else{
                noEntry = false;
            }
        }



        if (canMove == true && nextMove.isConfined() == true && noEntry == false) {
            moveOkay = true;
        } else {
            moveOkay = false;
        }
        //System.out.println('l');

        return moveOkay;
    }

    public HashMap<Integer, Double> Moves(LongLat startLocation, LongLat nextLocation, ArrayList<ArrayList<LongLat>> buildingCoordinates) {

        HashMap<Integer, Double> tempAngles = new HashMap<>();

        /**
        for (int y = 0; y < 36; y++) {
            int angle = y * 10;
            boolean checkSafe = SafeMove(angle, startLocation, buildingCoordinates);

            if (checkSafe == true) {
                LongLat tempMoveInner = startLocation.nextPosition(angle);
                int trues = 0;
                int falses = 0;
                for (int b = 0; b < 36; b++) {
                    int angleInner = b * 10;
                    boolean checkSafeInner = SafeMove(angleInner, tempMoveInner, buildingCoordinates);
                    System.out.println("SAFE " + checkSafeInner);
                    if (checkSafeInner == true) {
                        trues = trues + 1;
                    } else {
                        falses = falses + 1;
                    }
                }
                if (falses > trues) {
                    doNotEnter.add(tempMoveInner);
                    doNotEnter.add(startLocation);
                }
            }
        }
        */


        /**
        int z = dronePath.size();
        if (z>0) {
            LongLat getD = dronePath.get(z - 1);
            double longitude = getD.longitude;
            double latitude = getD.latitude;
            for (int e = 0; e < doNotEnter.size(); e++) {
                LongLat dne = doNotEnter.get(e);
                double dneLongitude = dne.longitude;
                double dneLatitude = dne.latitude;
                if (longitude == dneLongitude && latitude == dneLatitude) {
                    dronePath.remove(z-1);
                    numberOfMoves = numberOfMoves - 1;
                }
            }
        }
        */


        for (int a = 0; a < 36; a++) {
            int angle = a * 10;
            boolean checkSafe = SafeMove(angle, startLocation, buildingCoordinates);

            System.out.println(checkSafe);

            LongLat nextPossiblePosition = startLocation.nextPosition(angle);
            double nPPLong = nextPossiblePosition.longitude;
            double nPPLat = nextPossiblePosition.latitude;

            boolean contains = false;

            if (dronePath.size()>0) {
                for (int d = 0; d < dronePath.size(); d++) {
                    LongLat dP = dronePath.get(d);
                    double dPLong = dP.longitude;
                    double dPLat = dP.latitude;
                    if (dPLat == nPPLat && dPLong == nPPLong) {
                        contains = true;
                    }
                }
            }


            if (checkSafe == true ) {
                LongLat tempMove = startLocation.nextPosition(angle);
                double tempDistance = tempMove.distanceTo(nextLocation);
                tempAngles.put(angle, tempDistance);
            }
        }
    return tempAngles;
    }
}
