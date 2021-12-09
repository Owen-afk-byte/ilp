package uk.ac.ed.inf;

import java.awt.geom.Line2D;
import java.net.http.HttpClient;
import java.sql.*;
import java.util.*;

import com.mapbox.geojson.*;
import com.mapbox.geojson.Point;

public class Algorithm {
    private String day;
    private String month;
    private String year;
    private String firstPort;
    private String secondPort;


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

    public String mainAlgorithm() {

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
        ArrayList<ArrayList<Double>> justPath = new ArrayList<>();
        ArrayList<String> justOrder = new ArrayList<>();

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
        HashMap<String, ArrayList<String>> deliveriesHashMap = new HashMap<>();
        HashMap<String, ArrayList<Double>> flightPathHashMap = new HashMap<>();

        ArrayList<LongLat> doNotEnter = new ArrayList<>();

        try {

            // read in everything
            Orders orders = new Orders(secondPort);
            ArrayList<ArrayList<String>> date = orders.getDates(year, month, day);
            HashMap<String, ArrayList<String>> details = orders.getDetails(date);


            Menus menus = new Menus(firstPort);

            Buildings buildings = new Buildings(firstPort);
            ArrayList<ArrayList<LongLat>> buildingCoordinates = buildings.getBuildings();
            ArrayList<LongLat> landmarkCoordinates = buildings.getLandmarks();

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
                    LongLat W3W = words.getInfo(getPickups);
                    pickupsArray.add(W3W);
                }
                allPickups.put(entry.getKey(), pickupsArray);
            }

            if (appletonReached == true){
                String jdbcString = "jdbc:derby://localhost:" + secondPort + "/derbyDB";        //<----- change this jdbc string
                Connection conn = DriverManager.getConnection(jdbcString);
                Statement statement = conn.createStatement();
                DatabaseMetaData databaseMetaData = conn.getMetaData();
                ResultSet resultSet = databaseMetaData.getTables(null, null, "DELIVERIES", null);
                if (resultSet.next()){
                    statement.execute("drop table deliveries");
                }
                statement.execute("create table deliveries(" + "orderNo char(8), " + "deliveredTo varchar(19), " + "costInPence int)");
                PreparedStatement psDeliveries = conn.prepareStatement("insert into deliveries values (?, ?, ?)");
                for (Map.Entry<String, ArrayList<String>> entry : deliveriesHashMap.entrySet()) {
                    String orderNo = entry.getKey();
                    List<String> placeAndPrice = entry.getValue();
                    String place = placeAndPrice.get(0);
                    int price = Integer.valueOf(placeAndPrice.get(1));
                    psDeliveries.setString(1, orderNo);
                    psDeliveries.setString(2, place);
                    psDeliveries.setInt(3, price);
                    psDeliveries.execute();
                }


                ResultSet resultSet2 = databaseMetaData.getTables(null, null, "FLIGHTPATH", null);
                if (resultSet.next()){
                    statement.execute("drop table flightpath");
                }
                statement.execute("create table flightpath(" + "orderNo char(8), " + "fromLongitude double, " + "fromLatitude double, " + "angle integer, " + "toLongitude double, " + "toLatitude double)");
                PreparedStatement psFlightPath = conn.prepareStatement("insert into flightpath values (?, ?, ?, ?, ?, ?)");
                for (int d = 0; d < justOrder.size(); d++) {
                    String orderNo = justOrder.get(d);
                    List<Double> flight = justPath.get(d);
                    psFlightPath.setString(1, orderNo);
                    psFlightPath.setDouble(2, flight.get(0));
                    psFlightPath.setDouble(3, flight.get(1));
                    psFlightPath.setInt(4, Integer.valueOf(String.valueOf(flight.get(2))));
                    psFlightPath.setDouble(5, flight.get(3));
                    psFlightPath.setDouble(6, flight.get(4));
                    psFlightPath.execute();
                }
            }

            //loop until deliveries are complete or drone uses all moves
            while (numberOfMoves < 1500 && numberDelivered < date.size() && appletonReached == false) {
                System.out.println(numberOfMoves);
                if (numberOfMoves == 0) {
                    startLocation = appleton;
                } else if (allDelivered == true) {
                    returnToAppleton = true;
                } else {
                    startLocation = nextMove;
                }
                if (returnToAppleton == true) {

                    startLocation = nextMove;

                    if (startLocation.closeTo(appleton)) {
                        returnToAppleton = false;
                        appletonReached = true;
                    }else{

                        //figure out next move
                        moves = moves(startLocation, appleton, buildingCoordinates);

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

                                landmark = closestLandmark(startLocation, landmarkCoordinates);
                                landmarkDistance = startLocation.distanceTo(landmark);
                                pickingUp = false;
                                pausedPickup = true;
                                headingLandmark = true;

                            }
                        }
                        //figure out next move
                        moves = moves(startLocation, appleton, buildingCoordinates);

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
                        ArrayList<Double> dronePathArray = new ArrayList<>();
                        dronePathArray.add(startLocation.longitude);
                        dronePathArray.add(startLocation.latitude);
                        dronePathArray.add((double) finalMove);
                        dronePathArray.add(nextMove.longitude);
                        dronePathArray.add(nextMove.latitude);
                        justPath.add(dronePathArray);
                        justOrder.add(pickupID);

                        numberOfMoves = numberOfMoves +1;
                    }
                }
                else if (currentlyDelivering == false && pickingUp == false && headingLandmark == false){

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
                        moves = moves(startLocation, pickupOne, buildingCoordinates);
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
                        ArrayList<Double> dronePathArray = new ArrayList<>();
                        dronePathArray.add(startLocation.longitude);
                        dronePathArray.add(startLocation.latitude);
                        dronePathArray.add((double) finalMove);
                        dronePathArray.add(nextMove.longitude);
                        dronePathArray.add(nextMove.latitude);
                        justPath.add(dronePathArray);
                        justOrder.add(pickupID);
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
                        ArrayList<Double> dronePathArray = new ArrayList<>();
                        dronePathArray.add(startLocation.longitude);
                        dronePathArray.add(startLocation.latitude);
                        dronePathArray.add((double) angle);
                        dronePathArray.add(nextMove.longitude);
                        dronePathArray.add(nextMove.latitude);
                        justPath.add(dronePathArray);
                        justOrder.add(pickupID);
                        numberOfMoves = numberOfMoves  +1;

                        pickupOne = pickupTwo;
                        pickupTwo = null;
                        if (pickupOne==null && pickupTwo==null){
                            pickingUp = false;
                            currentlyDelivering = true;
                        }
                    }else{

                        //figure out next move
                        moves = moves(startLocation, pickupOne, buildingCoordinates);

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
                        if (dronePath.size()>5) {
                            if ((dronePath.get(dronePath.size() - 5).longitude == checkMoveLong && dronePath.get(dronePath.size() - 5).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 4).longitude == checkMoveLong && dronePath.get(dronePath.size() - 4).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 3).longitude == checkMoveLong && dronePath.get(dronePath.size() - 3).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 2).longitude == checkMoveLong && dronePath.get(dronePath.size() - 2).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 1).longitude == checkMoveLong && dronePath.get(dronePath.size() - 1).latitude == checkMoveLat)) {
                                doNotEnter.add(dronePath.get(dronePath.size()-1));
                                numberOfMoves = numberOfMoves - 5;
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                startLocation = (dronePath.get(dronePath.size()-1));
                                landmark = closestLandmark(startLocation, landmarkCoordinates);
                                landmarkDistance = startLocation.distanceTo(landmark);
                                pickingUp = false;
                                pausedPickup = true;
                                headingLandmark = true;

                            }
                        }

                        //figure out next move
                        moves = moves(startLocation, pickupOne, buildingCoordinates);

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
                        ArrayList<Double> dronePathArray = new ArrayList<>();
                        dronePathArray.add(startLocation.longitude);
                        dronePathArray.add(startLocation.latitude);
                        dronePathArray.add((double) finalMove);
                        dronePathArray.add(nextMove.longitude);
                        dronePathArray.add(nextMove.latitude);
                        justPath.add(dronePathArray);
                        justOrder.add(pickupID);
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
                        ArrayList<Double> dronePathArray = new ArrayList<>();
                        dronePathArray.add(startLocation.longitude);
                        dronePathArray.add(startLocation.latitude);
                        dronePathArray.add((double) angle);
                        dronePathArray.add(nextMove.longitude);
                        dronePathArray.add(nextMove.latitude);
                        justPath.add(dronePathArray);
                        justOrder.add(pickupID);
                        numberOfMoves = numberOfMoves + 1;

                        currentlyDelivering = false;

                        if (dateCoordinates.isEmpty()) {
                            allDelivered = true;
                        }

                    } else {


                        //figure out next move
                        moves = moves(startLocation, deliveryLocation, buildingCoordinates);
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
                        if (dronePath.size()>5) {
                            if ((dronePath.get(dronePath.size() - 5).longitude == checkMoveLong && dronePath.get(dronePath.size() - 5).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 4).longitude == checkMoveLong && dronePath.get(dronePath.size() - 4).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 3).longitude == checkMoveLong && dronePath.get(dronePath.size() - 3).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 2).longitude == checkMoveLong && dronePath.get(dronePath.size() - 2).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 1).longitude == checkMoveLong && dronePath.get(dronePath.size() - 1).latitude == checkMoveLat)) {
                                doNotEnter.add(dronePath.get(dronePath.size()-1));
                                numberOfMoves = numberOfMoves - 5;
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                startLocation = (dronePath.get(dronePath.size()-1));
                                landmark = closestLandmark(startLocation, landmarkCoordinates);
                                landmarkDistance = startLocation.distanceTo(landmark);
                                currentlyDelivering = false;
                                pausedDelivery = true;
                                headingLandmark = true;

                            }
                        }

                        //figure out next move
                        moves = moves(startLocation, deliveryLocation, buildingCoordinates);
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
                        ArrayList<Double> dronePathArray = new ArrayList<>();
                        dronePathArray.add(startLocation.longitude);
                        dronePathArray.add(startLocation.latitude);
                        dronePathArray.add((double) finalMove);
                        dronePathArray.add(nextMove.longitude);
                        dronePathArray.add(nextMove.latitude);
                        justPath.add(dronePathArray);
                        justOrder.add(pickupID);
                        numberOfMoves = numberOfMoves + 1;



                    }
                }else if (currentlyDelivering == false && pickingUp == false && headingLandmark == true) {
                    startLocation = nextMove;
                    if (landmarkMoves < 15) {

                        moves = moves(startLocation, landmark, buildingCoordinates);
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
                            if ((dronePath.get(dronePath.size() - 5).longitude == checkMoveLong && dronePath.get(dronePath.size() - 5).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 4).longitude == checkMoveLong && dronePath.get(dronePath.size() - 4).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 3).longitude == checkMoveLong && dronePath.get(dronePath.size() - 3).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 2).longitude == checkMoveLong && dronePath.get(dronePath.size() - 2).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 1).longitude == checkMoveLong && dronePath.get(dronePath.size() - 1).latitude == checkMoveLat)) {
                                doNotEnter.add(dronePath.get(dronePath.size()-1));
                                numberOfMoves = numberOfMoves - 5;
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                dronePath.remove(dronePath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justPath.remove(justPath.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                justOrder.remove(justOrder.size()-1);
                                startLocation = (dronePath.get(dronePath.size()-1));
                                landmark = closestLandmark(startLocation, landmarkCoordinates);
                                landmarkDistance = startLocation.distanceTo(landmark);

                            }
                        }

                        //figure out next move
                        moves = moves(startLocation, landmark, buildingCoordinates);
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
                        ArrayList<Double> dronePathArray = new ArrayList<>();
                        dronePathArray.add(startLocation.longitude);
                        dronePathArray.add(startLocation.latitude);
                        dronePathArray.add((double) finalMove);
                        dronePathArray.add(nextMove.longitude);
                        dronePathArray.add(nextMove.latitude);
                        justPath.add(dronePathArray);
                        justOrder.add(pickupID);
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

                }
            }
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

        return jsonString;
    }

    public LongLat closestLandmark(LongLat startLocation, ArrayList<LongLat> landmarkCoordinates) {

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

    return currentLandmark;
    }

    public boolean safeMove(int angle, LongLat startLocation, ArrayList<ArrayList<LongLat>> buildingCoordinates) {

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

        return moveOkay;
    }

    public HashMap<Integer, Double> moves(LongLat startLocation, LongLat nextLocation, ArrayList<ArrayList<LongLat>> buildingCoordinates) {

        HashMap<Integer, Double> tempAngles = new HashMap<>();

        for (int a = 0; a < 36; a++) {
            int angle = a * 10;
            boolean checkSafe = safeMove(angle, startLocation, buildingCoordinates);

            if (checkSafe == true ) {
                LongLat tempMove = startLocation.nextPosition(angle);
                double tempDistance = tempMove.distanceTo(nextLocation);
                tempAngles.put(angle, tempDistance);
            }
        }
    return tempAngles;
    }
}
