package uk.ac.ed.inf;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class AlgorithmTest {

    @Test
    public void testDate1() {
        Algorithm algorithm = new Algorithm("27", "12", "2023", "9898", "9876");
        String answer = algorithm.mainAlgorithm();
        //assertEquals("dfbnodn", answer);
    }

}