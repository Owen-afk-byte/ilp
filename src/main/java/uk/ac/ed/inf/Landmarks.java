package uk.ac.ed.inf;

import java.util.ArrayList;

/**
 * A class simply used to allow the reading in of the landmarks to help the drone navigate
 */
public class Landmarks {
    ArrayList<Landmarks.Features> features;

    public static class Features {
        Geometry geometry;

        public static class Geometry {
            ArrayList<Float> coordinates;
        }
    }

}
