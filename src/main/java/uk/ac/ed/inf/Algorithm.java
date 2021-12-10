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


    /**
     * A constructor class used to read in the arguments that it was given my the main function in the App class
     * @param day a string representing the day of the year which we will be finding the drone path for
     * @param month a string representing the month of the year which we will be finding the drone path for
     * @param year a string representing the year which we will be finding the drone path for
     * @param firstPort a string representing one of the server ports
     * @param secondPort a string representing the other server port
     */
    public Algorithm(String day, String month, String year, String firstPort, String secondPort) {
        this.day = day;
        this.month = month;
        this.year = year;
        this.firstPort = firstPort;
        this.secondPort = secondPort;
    }

    ArrayList<LongLat> doNotEnter = new ArrayList<>();
    ArrayList<LongLat> dronePath = new ArrayList<>();
    int numberOfMoves = 0;
    int landmarkMoves = 0;
    LongLat appleton = new LongLat(-3.186874, 55.944494);

    /**
     * The mainAlgorithm is where the majority of the work is done and it calls on all of the other functions in the class
     * It takes in no new parameters as it uses the parameters given in the constructor class
     * @return a string which represents the GeoJSON of the drone flight path and is given back to the main class
     */
    public String mainAlgorithm() {
        
        int numberDelivered = 0;

        LongLat nextMove = null;
        LongLat startLocation = null;
        LongLat deliveryLocation = null;
        LongLat pickupOne = null;
        LongLat pickupTwo = null;
        LongLat landmark = null;

        String pickupID = null;
        ArrayList<ArrayList<Double>> justPath = new ArrayList<>();
        ArrayList<String> justOrder = new ArrayList<>();
        ArrayList<LongLat> doNotEnter = new ArrayList<>();

        boolean returnToAppleton = false;
        boolean appletonReached = false;
        boolean currentlyDelivering = false;
        boolean pausedDelivery = false;
        boolean pickingUp = false;
        boolean pausedPickup = true;
        boolean allDelivered = false;
        boolean headingLandmark = false;

        HashMap<Integer, Double> moves;
        HashMap<String, ArrayList<String>> deliveriesHashMap = new HashMap<>();


        try {

            // reads in order and orderDetails tables
            Orders orders = new Orders(secondPort);
            ArrayList<ArrayList<String>> date = orders.getDates(year, month, day);
            HashMap<String, ArrayList<String>> details = orders.getDetails(date);

            // reads in from the menus folder
            Menus menus = new Menus(firstPort);

            // reads in buildings and landmarks from the menus folder
            Buildings buildings = new Buildings(firstPort);
            ArrayList<ArrayList<LongLat>> buildingCoordinates = buildings.getBuildings();
            ArrayList<LongLat> landmarkCoordinates = buildings.getLandmarks();

            // reads in from the words folder
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

            // one appleton has been reached everything is then added to the deliveries and flightpath tables
            if (appletonReached == true){
                String jdbcString = "jdbc:derby://localhost:" + secondPort + "/derbyDB";
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
                if (resultSet2.next()){
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
                System.out.println(allPickups.size());
                if (numberOfMoves == 0) {
                    //prevent null pointer exception by setting start location to appleton for the first move
                    startLocation = appleton;
                    // makes sure the drone returns to appleton after it exceeds 1450 moves
                } else if (allDelivered == true || numberOfMoves > 1450) {
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


                        //finds the closest angle to the destination
                        double firstDistance = 999;
                        int firstMove = 0;
                        int firstAngleMove;
                        double firstDistanceMove;
                        double secondDistance = 999;
                        int secondMove = 0;
                        int secondAngleMove;
                        double secondDistanceMove;
                        boolean doNotUse = false;
                        HashMap<Integer, Double> innerMoves = new HashMap<>();
                        //figure out next move
                        moves = moves(startLocation, appleton, buildingCoordinates);
                        for (Map.Entry<Integer, Double> entry : moves.entrySet()) {
                            firstAngleMove = entry.getKey();
                            firstDistanceMove = entry.getValue();
                            if (firstDistanceMove < firstDistance) {

                                LongLat checkFirstMove = startLocation.nextPosition(firstMove);
                                innerMoves = moves(checkFirstMove, appleton, buildingCoordinates);
                                for (Map.Entry<Integer, Double> innerEntry : innerMoves.entrySet()) {
                                    secondAngleMove = innerEntry.getKey();
                                    secondDistanceMove = innerEntry.getValue();
                                    if (secondDistanceMove < secondDistance) {
                                        secondDistance = secondDistanceMove;
                                        secondMove = secondAngleMove;
                                        LongLat checkSecondMove = checkFirstMove.nextPosition(secondMove);
                                        if ((checkSecondMove.longitude==checkFirstMove.longitude && checkSecondMove.latitude==checkSecondMove.latitude) || (checkSecondMove.longitude==startLocation.longitude && checkSecondMove.latitude==startLocation.latitude)){
                                            doNotUse = true;
                                        }
                                    }
                                }
                                if (doNotUse == true){
                                    //do nothing
                                    doNotUse = false;
                                }else{
                                    firstDistance = firstDistanceMove;
                                    firstMove = firstAngleMove;
                                }
                            }
                        }

                        // collates all of the necessary data about the drones actions to later be written to the database and GeoJSON
                        nextMove = startLocation.nextPosition(firstMove);
                        dronePath.add(nextMove);
                        ArrayList<Double> dronePathArray = new ArrayList<>();
                        dronePathArray.add(startLocation.longitude);
                        dronePathArray.add(startLocation.latitude);
                        dronePathArray.add((double) firstMove);
                        dronePathArray.add(nextMove.longitude);
                        dronePathArray.add(nextMove.latitude);
                        justPath.add(dronePathArray);
                        justOrder.add(pickupID);

                        numberOfMoves = numberOfMoves +1;
                    }
                }
                else if (currentlyDelivering == false && pickingUp == false && headingLandmark == false) {

                    // this is where the drone decides upon its next move, whether it will be delivering or picking up

                    // this next chunk of code is used to create a list of the closeness for each pickup and its associated delivery divided by the cost
                    // this allows the drone to go after the most cost effective option
                    HashMap<String, Double> jointCloseness = new HashMap<>();
                    for (Map.Entry<String, ArrayList<LongLat>> entry : allPickups.entrySet()) {
                        ArrayList<LongLat> pickupsArray = entry.getValue();
                        ArrayList<Double> closeness = new ArrayList<>();
                        for (int c = 0; c < pickupsArray.size(); c++) {
                            double close = startLocation.distanceTo(pickupsArray.get(c));
                            closeness.add(close);
                        }

                        String deliverTo = (dateHash.get(entry.getKey())).get(2);
                        LongLat deliverToCoordinates = words.getInfo(deliverTo);
                        double distanceToCoordinates = startLocation.distanceTo(deliverToCoordinates);

                        String[] items = details.get(entry.getKey()).toArray(new String[0]);
                        int cost = menus.getDeliveryCost(items);

                        double pickupCloseness = 0;
                        for (int d = 0; d < closeness.size(); d++) {
                            double closeTemp = closeness.get(d);
                            pickupCloseness = pickupCloseness + closeTemp;
                        }

                        double finalSum = (pickupCloseness + distanceToCoordinates) / cost;

                        jointCloseness.put(entry.getKey(), finalSum);
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
                    //once the pickups are made they are removed as so to not be used again when finding the closest pickups the next time
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

                    // collates all of the necessary data about the drones actions to later be written to the database and GeoJSON
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


                }else if (currentlyDelivering == false && pickingUp == true){

                    startLocation = nextMove;

                    if (startLocation.closeTo(pickupOne)){

                        // collates all of the necessary data about the drones actions to later be written to the database and GeoJSON
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

                        // after delivering one item(s) the set in the pickupTwo variable will move into variable pickupOne
                        // this allows for pickupOne to be the only variable used for searching for the items
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

                        // this is a check to ensure that the next move is not a duplicate of the last few
                        // if it is then it reverts a few moves and heads for a landmark to unstick itself
                        LongLat checkMove = startLocation.nextPosition(finalMove);
                        double checkMoveLong = checkMove.longitude;
                        double checkMoveLat = checkMove.latitude;
                        if (dronePath.size()>5) {
                            if ((dronePath.get(dronePath.size() - 5).longitude == checkMoveLong && dronePath.get(dronePath.size() - 5).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 4).longitude == checkMoveLong && dronePath.get(dronePath.size() - 4).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 3).longitude == checkMoveLong && dronePath.get(dronePath.size() - 3).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 2).longitude == checkMoveLong && dronePath.get(dronePath.size() - 2).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 1).longitude == checkMoveLong && dronePath.get(dronePath.size() - 1).latitude == checkMoveLat)) {
                                doNotEnter.add(dronePath.get(dronePath.size()-1));
                                numberOfMoves = numberOfMoves - 5;
                                for (int i = 0; i < 5; i++) {
                                    dronePath.remove(dronePath.size()-1);
                                    justPath.remove(justPath.size()-1);
                                    justOrder.remove(justOrder.size()-1);
                                }
                                startLocation = (dronePath.get(dronePath.size()-1));
                                landmark = closestLandmark(startLocation, landmarkCoordinates);
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

                        // collates all of the necessary data about the drones actions to later be written to the database and GeoJSON
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

                        // collates all of the necessary data about the drones actions to later be written to the database and GeoJSON
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

                        // checks to see if all of the deliveries have been made
                        // if this is the case then allDelivered will be set to true and the drone will head back to Appleton
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

                        // this is a check to ensure that the next move is not a duplicate of the last few
                        // if it is then it reverts a few moves and heads for a landmark to unstick itself
                        LongLat checkMove = startLocation.nextPosition(finalMove);
                        double checkMoveLong = checkMove.longitude;
                        double checkMoveLat = checkMove.latitude;
                        if (dronePath.size()>5) {
                            if ((dronePath.get(dronePath.size() - 5).longitude == checkMoveLong && dronePath.get(dronePath.size() - 5).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 4).longitude == checkMoveLong && dronePath.get(dronePath.size() - 4).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 3).longitude == checkMoveLong && dronePath.get(dronePath.size() - 3).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 2).longitude == checkMoveLong && dronePath.get(dronePath.size() - 2).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 1).longitude == checkMoveLong && dronePath.get(dronePath.size() - 1).latitude == checkMoveLat)) {
                                doNotEnter.add(dronePath.get(dronePath.size()-1));
                                numberOfMoves = numberOfMoves - 5;
                                for (int i = 0; i < 5; i++) {
                                    dronePath.remove(dronePath.size()-1);
                                    justPath.remove(justPath.size()-1);
                                    justOrder.remove(justOrder.size()-1);
                                }
                                startLocation = (dronePath.get(dronePath.size()-1));
                                landmark = closestLandmark(startLocation, landmarkCoordinates);
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

                        // collates all of the necessary data about the drones actions to later be written to the database and GeoJSON
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
                    // this prevents the drone from heading towards a landmark for more than 15 moves
                    // 15 was decided because it seems to be the most effective at unsticking the drone without using too many moves
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

                        // this is a check to ensure that the next move is not a duplicate of the last few
                        // if it is then it reverts a few moves and heads for a landmark to unstick itself
                        LongLat checkMove = startLocation.nextPosition(finalMove);
                        double checkMoveLong = checkMove.longitude;
                        double checkMoveLat = checkMove.latitude;
                        if (dronePath.size()>3) {
                            if ((dronePath.get(dronePath.size() - 3).longitude == checkMoveLong && dronePath.get(dronePath.size() - 3).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 2).longitude == checkMoveLong && dronePath.get(dronePath.size() - 2).latitude == checkMoveLat) || (dronePath.get(dronePath.size() - 1).longitude == checkMoveLong && dronePath.get(dronePath.size() - 1).latitude == checkMoveLat)) {
                                doNotEnter.add(dronePath.get(dronePath.size()-1));
                                numberOfMoves = numberOfMoves - 3;
                                for (int i = 0; i < 3; i++) {
                                    dronePath.remove(dronePath.size()-1);
                                    justPath.remove(justPath.size()-1);
                                    justOrder.remove(justOrder.size()-1);
                                }
                                startLocation = (dronePath.get(dronePath.size()-1));
                                landmark = closestLandmark(startLocation, landmarkCoordinates);
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

                        // collates all of the necessary data about the drones actions to later be written to the database and GeoJSON
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
                        // returns the drone back to its initial state prior ro heading towards a landmark
                        // means that drone can continue to pickup or deliver what it had intended
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

        // completes drone path
        // this was necessary to add the move from appleton to the next position of the drone
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

        // converts the drone path into a GeoJSON string to be outputed to the main class in the App class
        System.out.println("noPoints " + noPoints);
        LineString lineString = LineString.fromLngLats(points);
        Geometry geometry = (Geometry) lineString;
        Feature feature = Feature.fromGeometry(geometry);
        FeatureCollection featureCollection = FeatureCollection.fromFeature(feature);
        String jsonString = featureCollection.toJson();

        return jsonString;
    }

    /**
     * The closestLandmark class is used calculate which landmark is the closest to the drones current coordinates so that it can begin to fly there
     * @param startLocation is the location in which the drone begins at
     * @param landmarkCoordinates is an array containing the longitudes and latitudes of the two landmarks used to help with drone flight
     * @return a LongLat which is the position of the nearest landmark that the drone will then fly towards
     */
    public LongLat closestLandmark(LongLat startLocation, ArrayList<LongLat> landmarkCoordinates) {

        LongLat firstLandmark = landmarkCoordinates.get(0);
        LongLat secondLandmark = landmarkCoordinates.get(1);
        LongLat currentLandmark;
        if (startLocation.distanceTo(firstLandmark) < startLocation.distanceTo(secondLandmark)){
            currentLandmark = firstLandmark;
        }else{
            currentLandmark = secondLandmark;
        }

        // if the drone is still looping while travelling towards a landmark it will change which landmark that it is heading towards
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

    /**
     * The safeMove class is used calculate if a given angle causes the drone to move to a safe location
     * A safe location is determined if the drone is in the confined area, does not cross a no fly zone, and does not enter coordinates deemed doNotEnter
     * @param startLocation is the location in which the drone begins at
     * @param angle is the angle which will determine the drones next move
     * @param buildingCoordinates contains the coordinates of the 5 buildings/no-fly zones that the drone is to avoid
     * @return a boolean which determines if the drone is deemed to be making a safe move
     */
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

        // checks that the entered move is safe
        // the move has to be within the confined zone, not flying over no-fly zones, and not entering coordinates from the array doNotEnter
        if (canMove == true && nextMove.isConfined() == true && noEntry == false) {
            moveOkay = true;
        } else {
            moveOkay = false;
        }
        return moveOkay;
    }

    /**
     * The moves class is used calculate all of the possible 36 moves that a drone can make and return them if they are safe to make
     * A safe location is determined if the drone is in the confined area, does not cross a no fly zone, and does not enter coordinates deemed doNotEnter
     * @param startLocation is the location in which the drone begins at
     * @param nextLocation is the location of the drones next aimed location is
     * @param buildingCoordinates contains the coordinates of the 5 buildings/no-fly zones that the drone is to avoid
     * @return a HashMap<Integer, Double> which contains all of the angles of the drones next moves as well as the distances from the next move to the aimed location
     */
    public HashMap<Integer, Double> moves(LongLat startLocation, LongLat nextLocation, ArrayList<ArrayList<LongLat>> buildingCoordinates) {

        HashMap<Integer, Double> tempAngles = new HashMap<>();

        // all 36 directions are tested hear to make sure that the drone is finding the best possible route
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
