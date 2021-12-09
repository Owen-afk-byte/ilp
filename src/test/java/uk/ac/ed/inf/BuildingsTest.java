package uk.ac.ed.inf;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class BuildingsTest {

    @Test
    public void testDate1() {
        // The webserver must be running on port 9898 to run this test.
        Buildings buildings = new Buildings("9898");
        ArrayList<ArrayList<LongLat>> coordinates = buildings.getBuildings("9898");
        // Don't forget the standard delivery charge of 50p
        //assertEquals("dfbnodn", coordinates);
    }

    @Test
    public void testDate2() {
        // The webserver must be running on port 9898 to run this test.
        Buildings buildings = new Buildings("9898");
        ArrayList<LongLat> coordinates = buildings.getLandmarks("9898");
        // Don't forget the standard delivery charge of 50p
        //assertEquals("dfbnodn", coordinates);
    }

}