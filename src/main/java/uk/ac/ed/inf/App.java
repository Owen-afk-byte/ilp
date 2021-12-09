package uk.ac.ed.inf;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        System.out.println("hello world");
        Algorithm algorithm = new Algorithm(args[0], args[1], args[2], args[3], args[4]);
        String flightPath = algorithm.MainAlgorithm();

        try {
            File file = new File("drone-"+args[0]+"-"+args[1]+"-"+args[2]+".geojson");
            // if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(flightPath);
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}


