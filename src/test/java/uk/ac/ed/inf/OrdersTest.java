package uk.ac.ed.inf;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.*;

public class OrdersTest {

    @Test
    public void testDate1() {
        // The webserver must be running on port 9898 to run this test.
        Orders orders = new Orders("localhost", "9876");
        ArrayList<ArrayList<String>> date = orders.getDates(
                "2022-04-11");
        // Don't forget the standard delivery charge of 50p
        assertEquals("dfbnodn", date);
    }

    @Test
    public void testDate2() {
        // The webserver must be running on port 9898 to run this test.
        Orders orders = new Orders("localhost", "9876");
        ArrayList<ArrayList<String>> date = orders.getDates(
                "2022-04-11");
        HashMap<String, ArrayList<String>> conciseDetails = orders.getDetails(date);

        assertEquals("dfbnodn", conciseDetails);
    }

}
