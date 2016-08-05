package ca.uwaterloo.watca;

import java.io.IOException;
import java.util.List;

/**
 *
 * @author Shankha Subhra Chatterjee, Wojciech Golab
 */
public class LinearizabilityTest {
    public static void main(String[] args) {      
        
        if (args.length != 2) {
            System.out.println("usage: java ece454.test.LinearizabilityTest input_file output_file");
            return;
        }       
        LogParser t = new LogParser();       
        Analyzer a = new Analyzer(args[1]);        
        try
        {
            List<Operation> ops = t.parse(args[0]);
            ops.stream().forEach((o) -> {
                a.processOperation(o);
            });
            a.computeMetrics();
        }catch (IOException e)
        {
            System.out.println(e.getMessage());
        }
        System.out.println("Scores calculated successfully!  Open up " + args[1] + " and see for yourself.");
    }
}
