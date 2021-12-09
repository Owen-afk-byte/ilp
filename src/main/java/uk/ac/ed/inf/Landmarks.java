package uk.ac.ed.inf;

import java.util.ArrayList;

public class Landmarks {
    ArrayList<Landmarks.Features> features;

    public static class Features {
        Geometry geometry;

        public static class Geometry {
            ArrayList<Float> coordinates;
        }
    }

}
