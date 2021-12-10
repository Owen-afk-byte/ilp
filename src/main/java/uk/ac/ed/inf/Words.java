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
    public String port;

    /**
     * A constructor class used to represent the port used for the server
     * @param port a string representing the server port
     */
    public Words(String port){
        this.port = port;
    }

    private static final HttpClient client = HttpClient.newHttpClient();

    /**
     * getInfo takes in a What3Words adress and then outputs the corresponding coordinates for that What3Words address as a LongLat
     * @param string is a string representing a What3Words address
     * @return a LongLat which contains the coordinates associated with the What3Words address
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
            System.out.println("Fatal error: Unable to connect to localhost at port " + port + ".");
            System.exit(1); // Exit the application
        } catch (IOException | InterruptedException e) {
            System.out.println(" ");
            System.exit(1); // Exit the application
        }

        return coordsArray;
    }
}
