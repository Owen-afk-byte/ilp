package uk.ac.ed.inf;

import com.google.gson.Gson;
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
import java.util.List;

public class Words {
    public String name;
    public String port;

    /**
     * A constructor class used to represent the name and port used for the server
     * @param name a string representing the server name
     * @param port a string representing the server port
     */
    public Words(String port){
        this.port = port;
    }

    private static final HttpClient client = HttpClient.newHttpClient();

    /**
     * Takes in list of items and calculates the delivery cost of given items
     * @param string a list of strings representing the items that we need to find the cost of
     * @return an integer representing the cost of all of the items + the 50p for delivery
     */
    public LongLat getInfo(String string) {
        LongLat coordsArray = new LongLat(0, 0);

        System.out.println(string);
        String splitStr[] = string.split("\\.");
        String urlString = "http://localhost:" + port + "/words/" + splitStr[0] + "/" + splitStr[1] + "/" + splitStr[2] + "/details.json";
        try {
            // HttpRequest assumes that it is a GET request by default.
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
            // We call the send method on the client which we created.
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            String responseBody = response.body();

            Type listType = new TypeToken<WordExtension>() {}.getType();
            // Use the ”fromJson(String, Type)” method
            //System.out.println(responseBody);
            WordExtension responseList = new Gson().fromJson(responseBody, listType);


            WordExtension.Coordinates coords = responseList.coordinates;
            coordsArray = new LongLat(coords.lng, coords.lat);
            System.out.println(coords.lng);
            System.out.println(coords.lat);
            //coordinatesFinal.add(coordsArray);


        } catch (ConnectException e) {
            System.out.println("Fatal error: Unable to connect to " + name + " at port " + port + ".");
            System.exit(1); // Exit the application
        } catch (IOException | InterruptedException e) {
            System.out.println(" ");
            System.exit(1); // Exit the application
        }

        return coordsArray;
    }
}
