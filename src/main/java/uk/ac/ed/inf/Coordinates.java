package uk.ac.ed.inf;

import java.util.ArrayList;

public class Coordinates {

    public ArrayList<Coordinates.Features> features;

    public static class Features {

        Geometry geometry;

        public static class Geometry {
            ArrayList<ArrayList<ArrayList<Float>>> coordinates;
        }
    }

}
