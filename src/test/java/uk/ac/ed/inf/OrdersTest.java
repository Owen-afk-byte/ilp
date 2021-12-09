package uk.ac.ed.inf;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.*;

public class OrdersTest {

    @Test
    public void testDate1() {
        // The webserver must be running on port 9876 to run this test.
        Orders orders = new Orders("9876");
        ArrayList<ArrayList<String>> date = orders.getDates(
                "2022", "04", "11");
        //assertEquals("dfbnodn", date);
    }

    @Test
    public void testDate2() {
        // The webserver must be running on port 9876 to run this test.
        Orders orders = new Orders("9876");
        ArrayList<ArrayList<String>> date = orders.getDates(
                "2022", "04", "11");
        HashMap<String, ArrayList<String>> conciseDetails = orders.getDetails(date);

        //assertEquals("dfbnodn", conciseDetails);
    }

}
