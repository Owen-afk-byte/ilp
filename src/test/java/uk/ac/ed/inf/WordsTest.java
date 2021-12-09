package uk.ac.ed.inf;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class WordsTest {

    @Test
    public void testDate1() {
        // The webserver must be running on port 9898 to run this test.
        Words words = new Words("9898");
        LongLat coordinates = words.getInfo(
                "linked.pads.cigar");
        //assertEquals("dfbnodn", coordinates.longitude + " " + coordinates.latitude);
    }

}