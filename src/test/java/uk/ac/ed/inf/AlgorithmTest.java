package uk.ac.ed.inf;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class AlgorithmTest {

    @Test
    public void testDate1() {
        Algorithm algorithm = new Algorithm("11", "04", "2022", "9898", "9876");
        String answer = algorithm.MainAlgorithm();
        //assertEquals("dfbnodn", answer);
    }

}