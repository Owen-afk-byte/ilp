package uk.ac.ed.inf;

import java.util.ArrayList;

/**
 * A class simply used to allow the reading in of the no fly zones
 */
public class Coordinates {

    public ArrayList<Coordinates.Features> features;

    public static class Features {

        Geometry geometry;

        public static class Geometry {
            ArrayList<ArrayList<ArrayList<Float>>> coordinates;
        }
    }

}
