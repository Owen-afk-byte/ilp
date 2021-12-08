package uk.ac.ed.inf;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class AlgorithmTest {

    @Test
    public void testDate1() {
        // The webserver must be running on port 9898 to run this test.
        Algorithm algorithm = new Algorithm("localhost", "9898", "localhost", "9876", "2023-12-27", "b");
        String answer = algorithm.MainAlgorithm();
        // Don't forget the standard delivery charge of 50p
        assertEquals("dfbnodn", answer);
    }

}