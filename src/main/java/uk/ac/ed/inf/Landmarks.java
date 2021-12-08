package uk.ac.ed.inf;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.SerializedName;

public class Landmarks {
    String type;
    ArrayList<Landmarks.Features> features;

    public static class Features {
        String type;
        Geometry geometry;

        public static class Geometry {
            String type;
            ArrayList<Float> coordinates;
        }

        PropertiesBuildings properties;

        public static class PropertiesBuildings {
            String name;
            String location;
            @SerializedName("marker-symbol")
            String markerSymbol;
            @SerializedName("marker-color")
            String markerColor;
        }


    }

}
