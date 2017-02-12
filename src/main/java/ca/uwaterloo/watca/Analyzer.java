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
    private final String scoreFileName;
    private final String outPath;    
    private final static String PROP_FILE_NAME = "/proportions.log";
    public final static String PER_ZONE_SCORE_FILE_NAME = "/scores_per_zone.log";
    
    public Analyzer(String outputPath, String fileName, boolean showZeroes, ScoreFunction sfn) {
        operations = new ArrayList();
        keyHistMap = new ConcurrentHashMap<>();
        this.sfn = sfn;
        scoreFileName = fileName;
        showZeroScores = showZeroes;
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
        
        PrintWriter logWriter = new PrintWriter(new FileWriter(outPath.replaceAll("/+$", "") + scoreFileName));       
        // compute scores for each history
        ConcurrentMap<String, List<Long>> keyScoreMap = new ConcurrentHashMap();
        // ScoreFunction sfn = new RegularScoreFunction();
        keyHistMap.entrySet().parallelStream().forEach((e) -> {
            keyScoreMap.put(e.getKey(), e.getValue().logScores(sfn, logWriter, showZeroScores));
        });
        logWriter.close();
        
        PrintWriter propWriter = new PrintWriter(new FileWriter(outPath.replaceAll("/+$", "") + PROP_FILE_NAME, true));        
        int noOfScores = 0;
        int noOfPosScores = 0;
        for (String key : keyHistMap.keySet()) {
            noOfScores += keyHistMap.get(key).getTotalNoOfScores();
            noOfPosScores += keyHistMap.get(key).getNoOfPositiveScores();
        }
        int noOfZeroScores = noOfScores - noOfPosScores;
        float prop = (float)noOfPosScores / noOfScores;
        propWriter.println(noOfScores + "\t" + noOfPosScores + "\t" + noOfZeroScores + "\t" + prop);
        propWriter.close();
        
        // Record scores again for SPECSHIFT histogram, the scores recorded in History.java
        // may not be reliable, as it is not necessarily per value
        PrintWriter scoreWriter = new PrintWriter(new FileWriter(outPath.replaceAll("/+$", "") + PER_ZONE_SCORE_FILE_NAME, true));
        List<Long> scoreList = new ArrayList(); 
        keyScoreMap.entrySet().stream().forEach((e) -> {
            scoreList.addAll(e.getValue());
        });
        for(Long s : scoreList)
            scoreWriter.println(s);
        scoreWriter.close();
    }
    
    public void processOperation(Operation op) {
        synchronized (operations) {
            operations.add(op);
        }
    }
}

