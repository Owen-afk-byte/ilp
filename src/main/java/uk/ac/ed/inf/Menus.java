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
import java.util.ArrayList;

public class Menus {
    public String name;
    public String port;

    /**
     * A constructor class used to represent the name and port used for the server
     * @param name a string representing the server name
     * @param port a string representing the server port
     */
    public Menus(String name,String port){
        this.name = name;
        this.port = port;
    }

    private static final HttpClient client = HttpClient.newHttpClient();

    /**
     * Takes in list of items and calculates the delivery cost of given items
     * @param strings a list of strings representing the items that we need to find the cost of
     * @return an integer representing the cost of all of the items + the 50p for delivery
     */
    public int getDeliveryCost(String... strings) {
        int total = 50;

        String urlString = "http://" + name + ":" + port + "/menus/menus.json";

        try {
            // HttpRequest assumes that it is a GET request by default.
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
            // We call the send method on the client which we created.
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            String responseBody = response.body();

            Type listType = new TypeToken<ArrayList<MenuExtension>>() {
            }.getType();
            // Use the ”fromJson(String, Type)” method
            ArrayList<MenuExtension> responseList = new Gson().fromJson(responseBody, listType);


            // Loops through all of the items on the input list
            for (int l = 0; l < strings.length; l++) {
                String s = strings[l];
                // Loops through all of the restaurants
                for (int m = 0; m < responseList.size(); m++) {
                    ArrayList<MenuExtension.Items> menu = responseList.get(m).menu;
                    // Loops through each item on the menu and checks if it matches with the given item from the list
                    for (int i = 0; i < menu.size(); i++) {
                        if ( menu.get(i).item.equals(s) ){
                            total = total + menu.get(i).pence;
                        }
                    }
                }
            }

        } catch (ConnectException e) {
            System.out.println("Fatal error: Unable to connect to " + name + " at port " + port + ".");
            System.exit(1); // Exit the application
        } catch (IOException | InterruptedException e) {
            System.out.println(" ");
            System.exit(1); // Exit the application
        }

        return total;
    }

    public String getW3W(String string) {

        String W3W = null;
        String urlString = "http://" + name + ":" + port + "/menus/menus.json";

        try {
            // HttpRequest assumes that it is a GET request by default.
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
            // We call the send method on the client which we created.
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            String responseBody = response.body();

            Type listType = new TypeToken<ArrayList<MenuExtension>>() {
            }.getType();
            // Use the ”fromJson(String, Type)” method
            ArrayList<MenuExtension> responseList = new Gson().fromJson(responseBody, listType);

            // Loops through all of the restaurants
            for (int m = 0; m < responseList.size(); m++) {
                ArrayList<MenuExtension.Items> menu = responseList.get(m).menu;
                String location = responseList.get(m).location;
                // Loops through each item on the menu and checks if it matches with the given item from the list
                for (int i = 0; i < menu.size(); i++) {
                    if (menu.get(i).item.equals(string)) {
                        W3W = location;
                    }
                }
            }
        } catch (ConnectException e) {
            System.out.println("Fatal error: Unable to connect to " + name + " at port " + port + ".");
            System.exit(1); // Exit the application
        } catch (IOException | InterruptedException e) {
            System.out.println(" ");
            System.exit(1); // Exit the application
        }

        return W3W;
    }
}
