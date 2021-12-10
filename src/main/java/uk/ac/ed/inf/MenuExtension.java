package uk.ac.ed.inf;

import java.util.ArrayList;

/**
 * A class simply used to allow the reading in of the information from the menus folder
 */
public class MenuExtension {
    String location;

    ArrayList<Items> menu;
    public static class Items {
        String item;
        int pence;
    }
}
