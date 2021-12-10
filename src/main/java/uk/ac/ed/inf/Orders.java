package uk.ac.ed.inf;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

public class Orders {
    public String port;


    /**
     * A constructor class used to represent the port used for the server
     * @param port a string representing the server port
     */
    public Orders(String port){
        this.port = port;
    }

    /**
     * getDates uses the day, month and year to find and display the order table with those criteria
     * @param year a string representing the year which we are searching for
     * @param month a string representing the month which we are searching for
     * @param day a string representing the day which we are searching for
     * @return an integer representing the cost of all of the items + the 50p for delivery
     */
    public ArrayList<ArrayList<String>> getDates(String year, String month, String day) {
        ArrayList<ArrayList<String>> dateList = new ArrayList<>();

        String string = year + "-" + month + "-" + day;
        System.out.println(string);

        String jdbcString = "jdbc:derby://localhost:" + port + "/derbyDB";

        try {
            Connection conn = DriverManager.getConnection(jdbcString);

            //Create a statement object that we can use for running various SQL statement commands against the database
            Statement statement = conn.createStatement();

            final String dateQuery = "select * from orders where deliveryDate=(?)";
            PreparedStatement psDateQuery = conn.prepareStatement(dateQuery);
            psDateQuery.setString(1, string);

            ResultSet rs = psDateQuery.executeQuery();
            while (rs.next()){
                ArrayList<String> eachDate = new ArrayList<>();
                String orderNoString = rs.getString("orderNo");
                eachDate.add(orderNoString);
                String deliveryDateString = rs.getString("deliveryDate");
                eachDate.add(deliveryDateString);
                String customerString = rs.getString("customer");
                eachDate.add(customerString);
                String deliverToString = rs.getString("deliverTo");
                eachDate.add(deliverToString);
                dateList.add(eachDate);
            }


        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return dateList;
    }

    /**
     * getDetails takes in the information we found from getDates to return all of the items that each orderID should pickup
     * @param foundDateList an ArrayList<ArrayList<String>> containing all of the information given from the previous getDates class
     * @return a HashMap<String, ArrayList<String>> representing the orderDetails table matching the orderIDs that we entered
     */
    public HashMap<String, ArrayList<String>> getDetails(ArrayList<ArrayList<String>> foundDateList) {

        ArrayList<ArrayList<String>> detailsList = new ArrayList<>();
        HashMap<String, ArrayList<String>> conciseDetails = new HashMap<>();

        String jdbcString = "jdbc:derby://localhost:" + port + "/derbyDB";

        try {
            Connection conn = DriverManager.getConnection(jdbcString);

            for (int i = 0; i < foundDateList.size(); i++) {
                ArrayList<String> instances = foundDateList.get(i);
                String Ono = instances.get(0);
                final String detailsQuery = "select * from orderDetails where orderNo=(?)";
                PreparedStatement psDetailsQuery = conn.prepareStatement(detailsQuery);
                psDetailsQuery.setString(1, Ono);

                ResultSet rs_two = psDetailsQuery.executeQuery();
                while (rs_two.next()){
                    ArrayList<String> eachOrder = new ArrayList<>();
                    String OrderNoString = rs_two.getString("orderNo");
                    eachOrder.add(OrderNoString);
                    String itemString = rs_two.getString("item");
                    eachOrder.add(itemString);
                    detailsList.add(eachOrder);
                }
            }

            String oldOrderNumber = null;
            for (int j = 0; j < detailsList.size(); j++) {
                ArrayList<String> newOrderString = detailsList.get(j);
                String newOrderNumber = newOrderString.get(0);
                if (newOrderNumber.equals(oldOrderNumber)) {
                    //do nothing
                } else {
                    ArrayList<String> justItem = new ArrayList<>();
                    for (int k = 0; k < detailsList.size(); k++) {
                        ArrayList<String> loopOrderString = detailsList.get(k);
                        String loopOrderNumber = loopOrderString.get(0);
                        String loopItem = loopOrderString.get(1);
                        if (newOrderNumber.equals(loopOrderNumber)){
                            justItem.add(loopItem);
                        }
                    }
                    oldOrderNumber = newOrderNumber;
                    conciseDetails.put(newOrderNumber, justItem);
                }
            }

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return conciseDetails;
    }
}