package ca.uwaterloo.watca;


import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 *
 * @author Shankha Subhra Chatterjee, Wojciech Golab
 */
public class Analyzer {    
    private final List<Operation> operations;
    private ConcurrentHashMap<String, History> keyHistMap;
    private ScoreFunction sfn;
    private boolean showZeroScores;
    private String logfile;
    private String outPath;    
    private static String propFileName = "/proportions.log"; 
    
    public Analyzer(String filename, boolean s, String outputPath) {
        operations = new ArrayList();
        keyHistMap = new ConcurrentHashMap<>();
        // default score function
        //sfn = new GKScoreFunction();
        sfn = new RegularScoreFunction();
        logfile = filename;
        showZeroScores = s;
        outPath = outputPath;
    }
    
    public void computeMetrics() throws IOException {        
        List<Operation> temp = new ArrayList();
        // the values(for zones) within the new batch ops
        HashMap<String, HashSet<String>> keyValueSet = new HashMap<>();

        synchronized (operations) {
            temp.addAll(operations);
            operations.clear();
        }

        // first add new operations
        ConcurrentMap<String, List<Operation>> keyOpsMap
                = temp.parallelStream().collect(Collectors.groupingByConcurrent(Operation::getKey));
        /*keyOpsMap.entrySet().parallelStream().forEach((e) -> {
            History h = keyHistMap.get(e.getKey());
            if (h == null) {
                h = new History(e.getKey());
                keyHistMap.put(e.getKey(), h);
            }
            List<Operation> ops = e.getValue();
            for (Operation op : ops) {
                h.addOperation(op);
            }
        });*/
        
        for (String key : keyOpsMap.keySet()) {
            History h = keyHistMap.get(key);
            if (h == null) {
                h = new History(key);
                keyHistMap.put(key, h);
            }
            List<Operation> ops = keyOpsMap.get(key);
            for (Operation op : ops) {
                h.addOperation(op);
            }
        }        
        
        PrintWriter logWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logfile), "utf-8")));       
        
        // compute scores for each history
        ConcurrentMap<String, List<Long>> keyScoreMap = new ConcurrentHashMap();
        // ScoreFunction sfn = new RegularScoreFunction();
        keyHistMap.entrySet().parallelStream().forEach((e) -> {
            keyScoreMap.put(e.getKey(), e.getValue().logScores(sfn, logWriter, showZeroScores));
        });
        logWriter.close();
        
        PrintWriter propWriter = new PrintWriter(new FileWriter(outPath.replaceAll("/+$", "") + propFileName, true));
        int noOfScores = 0;
        int noOfPosScores = 0;
        for (String key : keyHistMap.keySet()) {
            noOfScores += keyHistMap.get(key).getNoOfScores();
            noOfPosScores += keyHistMap.get(key).getNoOfPosScores();
        }
        int noOfZeroScores = noOfScores - noOfPosScores;
        float prop = (float)noOfPosScores/noOfScores;
        propWriter.println(noOfScores + "\t" + noOfPosScores + "\t" + noOfZeroScores + "\t" + prop);
        propWriter.close();
    }
    
    public void processOperation(Operation op) {
        synchronized (operations) {
            operations.add(op);
        }
    }
}

