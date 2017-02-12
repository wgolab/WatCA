package ca.uwaterloo.watca;

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Shankha Subhra Chatterjee, Wojciech Golab
 */
public class LinearizabilityTest {
    public final static String SCORE_FILE_NAME = "/scores.log";
    public final static String LATENCY_FILE_NAME = "/latency.log";   
    
    public static void main(String[] args) throws IOException {       
        boolean showZeroScores = false;
        if (args.length < 2 || args.length > 3) {
            System.out.println("usage: java LinearizabilityTest path_to_trace_files path_to_output_files <optional - showzeroscores>");
            return;
        }
        
        if (args.length == 3 && args[2].equals("showzeroscores"))
            showZeroScores = true;
        LogParser t = new LogParser();        
        Analyzer a = new Analyzer(args[1], SCORE_FILE_NAME, showZeroScores, new GKScoreFunction());
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
            
            long totalLat = 0;
            long lat;
            int noOfValidOps = 0;
            long minStartTime = Long.MAX_VALUE, maxFinishTime = 0;
            List<Long> lats = new ArrayList();
            for (Operation o : ops) { 
                minStartTime = Math.min(minStartTime, o.getStart());                
                lat = o.getFinish() - o.getStart();
                if (o.getFinish() < Long.MAX_VALUE) {
                    totalLat += lat;
                    noOfValidOps++;
                    maxFinishTime = Math.max(maxFinishTime, o.getFinish());
                }
                lats.add(lat);
                a.processOperation(o);
            }            
            a.computeMetrics();            
            // Now record average and 95% latency of operations
            Collections.sort(lats);
            float latAvg = (float) totalLat / noOfValidOps;
            int index = (int) (0.95 * (lats.size() - 1));
            long lat95 = 0;
            if (lats.size() > 0)
                lat95 = lats.get(index);
            
            PrintWriter latWriter = new PrintWriter(new FileWriter(args[1].replaceAll("/+$", "") + LATENCY_FILE_NAME, true));
            latWriter.println("AvgLatency(ms)\t" + String.valueOf(latAvg));
            latWriter.println("95PercentileLatency(ms)\t" + String.valueOf(lat95));
            latWriter.println("Throughput(ops/s)\t" + String.valueOf((float)(ops.size() * 1000) / (maxFinishTime - minStartTime)));
            latWriter.close();          
            
        }catch (IOException e)
        {
            System.out.println(e.getMessage());
        }
        System.out.println("Scores calculated successfully!  Open up " + args[1] + " and see for yourself.");
    }
}
