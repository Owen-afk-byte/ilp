package uk.ac.ed.inf;

import java.util.ArrayList;

public class WordExtension {
    String country;
    Square square;
    public static class Square {
        Coordinates southwest;
        Coordinates northeast;
    }
    String nearestPlace;
    Coordinates coordinates;
    public static class Coordinates {
        float lng;
        float lat;
    }
    String words;
    String language;
    String map;
}
