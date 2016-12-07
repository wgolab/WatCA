package ca.uwaterloo.watca;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;

/**
 *
 * @author Shankha Subhra Chatterjee, Wojciech Golab
 */
public class LinearizabilityTest {
    public static void main(String[] args) throws IOException {       
        boolean showZeroScores = false;
        if (args.length < 2 || args.length > 3) {
            System.out.println("usage: java LinearizabilityTest path_to_trace_files output_file <optional - showzeroscores>");
            return;
        }
        
        if (args.length == 3 && args[2].equals("showzeroscores"))
            showZeroScores = true;
        LogParser t = new LogParser();       
        Analyzer a = new Analyzer(args[1], showZeroScores); 
        String sourcePath = args[0];
        File f = new File(sourcePath);
        try
        {            
            List<Operation> ops = t.parseFiles(f.listFiles(
                new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.matches(".*\\.log");
                    }
                }));
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
