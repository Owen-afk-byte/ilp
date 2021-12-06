package uk.ac.ed.inf;

import java.util.ArrayList;
import java.util.List;

public class Coordinates {
    String type;
    ArrayList<Coordinates.Features> features;

    public static class Features {
        String type;
        PropertiesBuildings properties;

        public static class PropertiesBuildings {
            String name;
            String fill;
        }

        Geometry geometry;

        public static class Geometry {
            String type;
            ArrayList<ArrayList<ArrayList<Float>>> coordinates;
        }
    }

}
