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

public class Buildings {
    public String port;

    /**
     * A constructor class used to represent the name and port used for the server
     * @param port a string representing the server port
     */
    public Buildings(String port){
        this.port = port;
    }

    private static final HttpClient client = HttpClient.newHttpClient();

    public static ArrayList<ArrayList<LongLat>> getBuildings(String port) {

        ArrayList<ArrayList<LongLat>> buildingsArray = new ArrayList<>();

        String urlString = "http://localhost:" + port + "/buildings/no-fly-zones.geojson";
        try {
            // HttpRequest assumes that it is a GET request by default.
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
            // We call the send method on the client which we created.
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            String responseBody = response.body();

            Type listType = new TypeToken<Coordinates>() {}.getType();
            // Use the ”fromJson(String, Type)” method
            System.out.println(responseBody);
            Coordinates responseList = new Gson().fromJson(responseBody, listType);
            System.out.println(responseList);
            Coordinates allBuildings = responseList;

            // Stores all of the buildings coordinates in an array
            // Loops through all of the features on the input list
            for (int i = 0; i < allBuildings.features.size(); i++) {
                Coordinates.Features features = allBuildings.features.get(i);
                //gets geometry
                Coordinates.Features.Geometry geometry = features.geometry;
                ArrayList<LongLat> innerBuildingsArray = new ArrayList<>();
                // Loops through first level of coordinates
                for (int j = 0; j < geometry.coordinates.size(); j++) {
                    ArrayList<ArrayList<Float>> firstArray = geometry.coordinates.get(j);
                    // Loops through second level of coordinates
                    for (int k = 0; k < firstArray.size(); k++) {
                        ArrayList<Float> secondArray = firstArray.get(k);
                        LongLat longLat = new LongLat(secondArray.get(0), secondArray.get(1));
                        innerBuildingsArray.add(longLat);
                    }
                }
                buildingsArray.add(innerBuildingsArray);
                System.out.println(buildingsArray.size());
            }
        } catch (ConnectException e) {
            System.out.println("Fatal error: Unable to connect to localhost at port " + port + ".");
            System.exit(1); // Exit the application
        } catch (IOException | InterruptedException e) {
            System.out.println(" ");
            System.exit(1); // Exit the application
        }

        for (int a = 0; a < buildingsArray.size(); a++) {
            System.out.println(buildingsArray.get(a).size());
        }

        return buildingsArray;
    }

    public static ArrayList<LongLat> getLandmarks(String port) {

        ArrayList<LongLat> landmarksArray = new ArrayList<>();

        String urlString = "http://localhost:" + port + "/buildings/landmarks.geojson";
        try {
            // HttpRequest assumes that it is a GET request by default.
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
            // We call the send method on the client which we created.
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            String responseBody = response.body();

            Type listType = new TypeToken<Landmarks>() {}.getType();
            // Use the ”fromJson(String, Type)” method
            System.out.println(responseBody);
            Landmarks responseList = new Gson().fromJson(responseBody, listType);
            System.out.println(responseList);
            Landmarks allLandmarks = responseList;

            // Stores all of the buildings coordinates in an array
            // Loops through all of the features on the input list
            for (int i = 0; i < allLandmarks.features.size(); i++) {
                Landmarks.Features features = allLandmarks.features.get(i);
                //gets geometry
                Landmarks.Features.Geometry geometry = features.geometry;
                double longitude = geometry.coordinates.get(0);
                double latitude = geometry.coordinates.get(1);
                LongLat longLat = new LongLat(longitude, latitude);
                landmarksArray.add(longLat);
            }
        } catch (ConnectException e) {
            System.out.println("Fatal error: Unable to connect to localhost at port " + port + ".");
            System.exit(1); // Exit the application
        } catch (IOException | InterruptedException e) {
            System.out.println(" ");
            System.exit(1); // Exit the application
        }


        return landmarksArray;
    }
}