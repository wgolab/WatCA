package ca.uwaterloo.watca;

import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 *
 * @author Wojciech Golab
 * @author Hua Fan
 */
public class RealtimeAnalyzer {

    private final List<Operation> operations;
    private final SortedMap<Long, Float> outputStaleProp;
    private final SortedMap<Long, Float> outputStalePropCopy;
    private final SortedMap<Long, List<Long>> outputStaleQuart;
    private final SortedMap<Long, Float> outputThroughput;
    private final SortedMap<Long, Float> outputThroughputCopy;
    private final SortedMap<Long, Float> outputLatencyAvg;
    private final SortedMap<Long, Float> outputLatencyAvgCopy;
    private final SortedMap<Long, Float> outputLatency95;
    private final SortedMap<Long, Float> outputLatency95Copy;
    private final List<Long> outputSpectrum;

    private AtomicInteger numLines;
    private int COMPUTE_WINDOW_SIZE = 1000;
    private int DISPLAY_WINDOW_SIZE = 60000;
    private int COMPUTE_INTERVAL = 1000;

    private ConcurrentHashMap<String, History> keyHistMap;
    private ScoreFunction sfn;
    private boolean saveLogs;
    private PrintWriter logFileWriter;

    public RealtimeAnalyzer(boolean saveLogs) {
	this.saveLogs = saveLogs;
        operations = new ArrayList();
        numLines = new AtomicInteger();
        outputStaleProp = new TreeMap();
        outputStalePropCopy = new TreeMap();
        outputStaleQuart = new TreeMap();
        outputThroughput = new TreeMap();
        outputThroughputCopy = new TreeMap();
        outputLatencyAvg = new TreeMap();
        outputLatencyAvgCopy = new TreeMap();
        outputLatency95 = new TreeMap();
        outputLatency95Copy = new TreeMap();
        outputSpectrum = new ArrayList();
        keyHistMap = new ConcurrentHashMap<>();
        // default score function
        sfn = new RegularScoreFunction();

        TimerTask task = new TimerTask() {
            public void run() {
                computeMetrics();
            }
        };
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(task, COMPUTE_INTERVAL, COMPUTE_INTERVAL);
    }

    public void processOperation(Operation op) {
        int n = numLines.getAndIncrement();
        synchronized (operations) {
            operations.add(op);
	    if (logFileWriter != null) {
		if (op.isRead()) {
		    logFileWriter.println(op.getStart() + "\tINV\t" + n + "\tR\t" + op.getKey());
		    logFileWriter.println(op.getFinish() + "\tRES\t" + n + "\tR\t" + op.getKey() + "\t" + op.getValue());
		} else {
		    logFileWriter.println(op.getStart() + "\tINV\t" + n + "\tW\t" + op.getKey() + "\t" + op.getValue());
		    logFileWriter.println(op.getFinish() + "\tRES\t" + n + "\tW\t" + op.getKey());
		}
		logFileWriter.flush();
	    }
        }
        if (n % 100000 == 0) {
            System.out.println("Total number of operations processed: " + n);
        }
    }

    public void newWorkload() {
	try {
	    if (logFileWriter != null) {
		logFileWriter.close();
	    }
	    File f = File.createTempFile("watca_", ".log", new File("."));
	    System.out.println("New workload logged to temporary file " + f.getCanonicalPath());
	    logFileWriter = new PrintWriter(new FileWriter(f));
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public void setScoreType(String type) {

        System.out.println("[Modify]Try to reset score type to: " + type);
        switch (type) {
            case "regular":
                sfn = new RegularScoreFunction();
                break;
            case "gk":
                sfn = new GKScoreFunction();
                break;
            default:
                throw new IllegalArgumentException("Invalid score type: "
                        + type);
        }
    }

    public void computeMetrics() {
        long now = System.currentTimeMillis();
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
        keyOpsMap.entrySet().parallelStream().forEach((e) -> {
            History h = keyHistMap.get(e.getKey());
            if (h == null) {
                h = new History(e.getKey());
                keyHistMap.put(e.getKey(), h);
            }
            List<Operation> ops = e.getValue();
            for (Operation op : ops) {
                h.addOperation(op);
            }
        });

        // now delete old ones
        long timeThreshold = now - COMPUTE_WINDOW_SIZE;
        keyHistMap.values().parallelStream().forEach((h) -> {
            h.deleteOld(timeThreshold);
        });

        // compute scores for each history
        ConcurrentMap<String, List<Long>> keyScoreMap = new ConcurrentHashMap();
        // ScoreFunction sfn = new RegularScoreFunction();
        keyHistMap.entrySet().parallelStream().forEach((e) -> {
            keyScoreMap.put(e.getKey(), e.getValue().getScores(sfn));
        });
        // collect the scores
        List<Long> scoreList = new ArrayList();
        keyScoreMap.values().stream().forEach((scores) -> {
            scoreList.addAll(scores);
        });

        synchronized (outputSpectrum) {
            outputSpectrum.clear();
            outputSpectrum.addAll(scoreList);
        }

        // Compute staleness  proportion.
        int numScores = scoreList.size();
        int numPosScores = (int) scoreList.stream()
                .filter(score -> score > 0)
                .count();

        float staleProp = 0;
        if (numScores > 0) {
            staleProp = (float) numPosScores / numScores;
        }

        synchronized (outputStaleProp) {
            outputStaleProp.put(now, staleProp);
        }

        // Compute staleness quartiles.
        List<Long> quarts = new ArrayList();
        List<Long> posScores = scoreList.stream()
                .filter(score -> score > 0)
                .collect(Collectors.toList());
        numScores = posScores.size();
        if (numScores > 0) {
            Collections.sort(posScores);
            quarts.add(posScores.get(0));
            quarts.add(posScores.get((int) (0.25 * (numScores - 1))));
            quarts.add(posScores.get((int) (0.75 * (numScores - 1))));
            quarts.add(posScores.get(numScores - 1));
        } else {
            quarts.add(0L);
            quarts.add(0L);
            quarts.add(0L);
            quarts.add(0L);
        }

        synchronized (outputStaleQuart) {
            outputStaleQuart.put(now, quarts);
        }

        // Analyze throughput.
        int numOps = 0;
        long maxFinish = Long.MIN_VALUE;
        long minFinish = Long.MAX_VALUE;
        for (Operation op : temp) {
            long fin = op.getFinish();
            maxFinish = Math.max(maxFinish, fin);
            minFinish = Math.min(minFinish, fin);
            numOps++;
        }
        float thru = 0;
        if (maxFinish > minFinish) {
            thru = (float)numOps / (maxFinish - minFinish);
        }
        synchronized (outputThroughput) {
            outputThroughput.put(now, thru);
        }

        // Analyze latency.
        long totLat = 0;
        List<Long> lats = new ArrayList();
        for (Operation op : temp) {
            long lat = op.getFinish() - op.getStart();
            totLat += lat;
            lats.add(lat);
        }
        Collections.sort(lats);
        synchronized (outputLatencyAvg) {
            if (lats.size() == 0) {
                outputLatencyAvg.put(now, (float) 0);
                outputLatency95.put(now, (float) 0);
            } else {
                outputLatencyAvg.put(now, (float) totLat / lats.size());
                int index = (int) (0.95 * (lats.size() - 1));
                outputLatency95.put(now, (float) lats.get(index));
            }
        }

        System.out.println("Metric computation at time " + now + " processed " + numOps + " ops in " +
			   (System.currentTimeMillis() - now) + " ms, " +
			   "operation throughput is " + thru + " kops/s, " +
			   "metric value (inconsistency) is " + staleProp);
    }

    public String getOutputStaleProp() {
        long now = System.currentTimeMillis();

        String ret = "{\n";
        ret += "\"cols\": [\n";
        ret += "{\"id\":\"time\",\"label\":\"Time\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"staleprop\",\"label\":\"Stale proportion\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"snapshot\",\"label\":\"\",\"type\":\"number\"}\n";
        ret += "],\n";
        ret += "\"rows\": [\n";

        boolean needComma = false;
        // first display data saved in snapshot		
        synchronized (outputStalePropCopy) {
            Long maxTime = 0L;
            if (!outputStalePropCopy.isEmpty()) {
                maxTime = outputStalePropCopy.lastKey();
            }

            ArrayList<Long> keys = new ArrayList<Long>(outputStalePropCopy.keySet());
            Collections.reverse(keys);
            for (Long time : keys) {
                Float staleness = outputStalePropCopy.get(time);
                if (needComma) {
                    ret += ",";
                } else {
                    needComma = true;
                }

                ret += "{\"c\":[{\"v\":" + (float) (time - maxTime) / 1000 + "},{\"v\":" + "null" + "},{\"v\":" + staleness + "}]}\n";
            }
        }

        // now display new data
        synchronized (outputStaleProp) {
            List<Long> toRemove = new ArrayList();
            Long maxTime = outputStaleProp.lastKey();
            for (Long time : outputStaleProp.keySet()) {
                if (now - time <= DISPLAY_WINDOW_SIZE) {
                    Float staleness = outputStaleProp.get(time);
                    if (needComma) {
                        ret += ",";
                    } else {
                        needComma = true;
                    }
                    ret += "{\"c\":[{\"v\":" + (float) (time - maxTime) / 1000 + "},{\"v\":" + staleness + "},{\"v\":" + "null" + "}]}\n";
                } else {
                    toRemove.add(time);
                }
            }
            for (Long time : toRemove) {
                outputStaleProp.remove(time);
            }
        }

        ret += "]\n";
        ret += "}\n";

        return ret;
    }

    public String getOutputThroughput() {
        long now = System.currentTimeMillis();

        String ret = "{\n";
        ret += "\"cols\": [\n";
        ret += "{\"id\":\"time\",\"label\":\"Time\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"throughput\",\"label\":\"Throughput\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"snapshot\",\"label\":\"\",\"type\":\"number\"}\n";
        //ret += "{\"id\":\"style\",\"label\":\"\",\"type\":\"string\",\"p\":{\"role\":\"style\"}}\n";
        ret += "],\n";
        ret += "\"rows\": [\n";

        TreeMap<Long, ArrayList<Float>> timeAndThroughputList = new TreeMap();

        boolean needComma = false;
        // first display data saved in snapshot		
        synchronized (outputThroughputCopy) {
            Long maxTime = 0L;
            if (!outputThroughputCopy.isEmpty()) {
                maxTime = outputThroughputCopy.lastKey();
            }

            ArrayList<Long> keys = new ArrayList<Long>(outputThroughputCopy.keySet());
            Collections.reverse(keys);
            for (Long time : keys) {
                Float thru = outputThroughputCopy.get(time);
                if (needComma) {
                    ret += ",";
                } else {
                    needComma = true;
                }

                ret += "{\"c\":[{\"v\":" + (float) (time - maxTime) / 1000 + "},{\"v\":" + "null" + "},{\"v\":" + thru + "}]}\n";
            }
        }

        // now display new data
        synchronized (outputThroughput) {
            List<Long> toRemove = new ArrayList();
            Long maxTime = outputThroughput.lastKey();

            for (Long time : outputThroughput.keySet()) {
                if (now - time <= DISPLAY_WINDOW_SIZE) {
                    Float thru = outputThroughput.get(time);
                    if (needComma) {
                        ret += ",";
                    } else {
                        needComma = true;
                    }

                    Long timeToPlot = time - maxTime;
                    ArrayList<Float> values;
                    ret += "{\"c\":[{\"v\":" + (float) (time - maxTime) / 1000 + "},{\"v\":" + thru + "},{\"v\":" + "null" + "}]}\n";
                } else {
                    toRemove.add(time);
                }
            }
            for (Long time : toRemove) {
                outputThroughput.remove(time);
            }
        }

        ret += "]\n";
        ret += "}\n";

        return ret;
    }

    public String getOutputLatency() {
        long now = System.currentTimeMillis();

        String ret = "{\n";
        ret += "\"cols\": [\n";
        ret += "{\"id\":\"time\",\"label\":\"Time\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"latencyavg\",\"label\":\"Average latency\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"latency95\",\"label\":\"95% latency\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"snapshotavg\",\"label\":\"Snapshot of average latency\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"snapshot95\",\"label\":\"Snapshot of 95% latency\",\"type\":\"number\"}\n";
        ret += "],\n";
        ret += "\"rows\": [\n";

        boolean needComma = false;

        // keys are reversed to prevent extra lines in the plots
        ArrayList<Long> keys = new ArrayList<Long>(outputLatencyAvgCopy.keySet());
        Collections.reverse(keys);

        // first display data saved in snapshot
        synchronized (outputLatencyAvgCopy) {
            synchronized (outputLatency95Copy) {
                Long maxTime = 0L;
                if (!outputLatencyAvgCopy.isEmpty()) {
                    maxTime = outputLatencyAvgCopy.lastKey();
                }
                for (Long time : keys) {
                    Float latAvg = outputLatencyAvgCopy.get(time);
                    Float lat95 = outputLatency95Copy.get(time);
                    if (needComma) {
                        ret += ",";
                    } else {
                        needComma = true;
                    }
                    ret += "{\"c\":[{\"v\":" + (float) (time - maxTime) / 1000 + "},{\"v\":" + "null" + "},{\"v\":" + "null" + "},{\"v\":" + latAvg + "},{\"v\":" + lat95 + "}]}\n";

                }
            }
        }
        // now display new data
        synchronized (outputLatencyAvg) {
            synchronized (outputLatency95) {
                List<Long> toRemove = new ArrayList();
                Long maxTime = outputLatencyAvg.lastKey();
                for (Long time : outputLatencyAvg.keySet()) {
                    if (now - time <= DISPLAY_WINDOW_SIZE) {
                        Float latAvg = outputLatencyAvg.get(time);
                        Float lat95 = outputLatency95.get(time);
                        if (needComma) {
                            ret += ",";
                        } else {
                            needComma = true;
                        }
                        ret += "{\"c\":[{\"v\":" + (float) (time - maxTime) / 1000 + "},{\"v\":" + latAvg + "},{\"v\":" + lat95 + "},{\"v\":" + "null" + "},{\"v\":" + "null" + "}]}\n";
                    } else {
                        toRemove.add(time);
                    }
                }
                for (Long time : toRemove) {
                    outputLatencyAvg.remove(time);
                    outputLatency95.remove(time);
                }
            }
        }

        ret += "]\n";
        ret += "}\n";

        return ret;
    }

    public String getOutputStaleQuart() {
        long now = System.currentTimeMillis();

        String ret = "{\n";
        ret += "\"cols\": [\n";
        ret += "{\"id\":\"time\",\"label\":\"Time\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"stale_quart_0\",\"label\":\"Staleness min\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"stale_quart_25\",\"label\":\"Staleness 25%\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"stale_quart_75\",\"label\":\"Staleness 75%\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"stale_quart_100\",\"label\":\"Staleness max\",\"type\":\"number\"}\n";
        ret += "],\n";
        ret += "\"rows\": [\n";

        synchronized (outputStaleQuart) {
            List<Long> toRemove = new ArrayList();
            Long maxTime = outputStaleQuart.lastKey();
            boolean needComma = false;
            for (Long time : outputStaleQuart.keySet()) {
                if (now - time <= DISPLAY_WINDOW_SIZE) {
                    List<Long> quarts = outputStaleQuart.get(time);
                    if (needComma) {
                        ret += ",";
                    } else {
                        needComma = true;
                    }
                    ret += "{\"c\":[{\"v\":" + (float) (time - maxTime) / 1000
                            + "},{\"v\":" + quarts.get(0)
                            + "},{\"v\":" + quarts.get(1)
                            + "},{\"v\":" + quarts.get(2)
                            + "},{\"v\":" + quarts.get(3)
                            + "}]}\n";
                } else {
                    toRemove.add(time);
                }
            }
            for (Long time : toRemove) {
                outputStaleQuart.remove(time);
            }
        }

        ret += "]\n";
        ret += "}\n";

        return ret;
    }

    public String getOutputLatencyStaleProp() {
        long now = System.currentTimeMillis();

        String ret = "{\n";
        ret += "\"cols\": [\n";
        ret += "{\"id\":\"latencyavg\",\"label\":\"Average latency\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"staleprop\",\"label\":\"Staleness\",\"type\":\"number\"},\n";
        ret += "{\"id\":\"style\",\"label\":\"\",\"type\":\"string\",\"p\":{\"role\":\"style\"}}\n";
        ret += "],\n";
        ret += "\"rows\": [\n";

        boolean needComma = false;
        // first display data saved in snapshot
        synchronized (outputLatencyAvgCopy) {
            synchronized (outputStalePropCopy) {
                for (Long time : outputLatencyAvgCopy.keySet()) {
                    Float latAvg = outputLatencyAvgCopy.get(time);
                    Float staleProp = outputStalePropCopy.get(time);
                    if (needComma) {
                        ret += ",";
                    } else {
                        needComma = true;
                    }
                    ret += "{\"c\":[{\"v\":" + latAvg + "},{\"v\":" + staleProp + "},{\"v\":\"gray\"}]}\n";
                }
            }
        }
        // now display new data
        synchronized (outputLatencyAvg) {
            synchronized (outputStaleProp) {
                List<Long> toRemove = new ArrayList();
                Long maxTime = outputLatencyAvg.lastKey();
                for (Long time : outputLatencyAvg.keySet()) {
                    if (now - time <= DISPLAY_WINDOW_SIZE) {
                        Float latAvg = outputLatencyAvg.get(time);
                        Float staleProp = outputStaleProp.get(time);
                        if (needComma) {
                            ret += ",";
                        } else {
                            needComma = true;
                        }
                        ret += "{\"c\":[{\"v\":" + latAvg + "},{\"v\":" + staleProp + "},{\"v\":\"blue\"}]}\n";
                    } else {
                        toRemove.add(time);
                    }
                }
                for (Long time : toRemove) {
                    outputLatencyAvg.remove(time);
                    outputStaleProp.remove(time);
                }
            }
        }

        ret += "]\n";
        ret += "}\n";

        return ret;
    }

    public String getOutputSpectrum() {
        long now = System.currentTimeMillis();

        String ret = "{\n";
        ret += "\"cols\": [\n";
        ret += "{\"id\":\"bucket\",\"label\":\"Bucket\",\"type\":\"string\"},\n";
        ret += "{\"id\":\"freq\",\"label\":\"Frequency\",\"type\":\"number\"}\n";
        ret += "],\n";
        ret += "\"rows\": [\n";

        long scale = 1;
        Map<Integer, Integer> map = new HashMap<>();
        int numZeros = 0;
        long maxVal = 9;
        int numBars = 10;

        synchronized (outputSpectrum) {
            for (Long score : outputSpectrum) {
                maxVal = Math.max(maxVal, score);
            }
            float f = (float) (Math.log(maxVal) / Math.log(10));
            int i = (int) f;
            maxVal = (int) Math.pow(10, i + 1);
            scale = Math.max(1, maxVal / numBars);

            for (Long score : outputSpectrum) {
                if (score == 0) {
                    numZeros++;
                } else {
                    i = (int) (score / scale);
                    if (map.containsKey(i)) {
                        map.put(i, map.get(i) + 1);
                    } else {
                        map.put(i, 1);
                    }
                }
            }
        }

        ret += "{\"c\":[{\"v\":\"0\"},{\"v\":" + numZeros + "}]}\n";
        for (int i = 0; i < numBars; i++) {
            ret += ",";
            int j = 0;
            if (map.containsKey(i)) {
                j = map.get(i);
            }
            ret += "{\"c\":[{\"v\":\"(" + (i * scale) + ", " + ((i + 1) * scale) + "]\"},{\"v\":" + j + "}]}\n";
        }

        ret += "]\n";
        ret += "}\n";

        return ret;
    }

    public void snapshot() {
        synchronized (outputStalePropCopy) {
            outputStalePropCopy.clear();
            synchronized (outputStaleProp) {
                outputStalePropCopy.putAll(outputStaleProp);
            }
        }
        synchronized (outputLatencyAvgCopy) {
            outputLatencyAvgCopy.clear();
            synchronized (outputLatencyAvg) {
                outputLatencyAvgCopy.putAll(outputLatencyAvg);
            }
        }
        synchronized (outputThroughputCopy) {
            outputThroughputCopy.clear();
            synchronized (outputThroughput) {
                outputThroughputCopy.putAll(outputThroughput);
            }
        }
        synchronized (outputLatency95Copy) {
            outputLatency95Copy.clear();
            synchronized (outputLatency95) {
                outputLatency95Copy.putAll(outputLatency95);
            }
        }
    }

    public void clearData() {
        synchronized (outputStaleProp) {
            outputStaleProp.clear();
        }
        synchronized (outputStaleQuart) {
            outputStaleQuart.clear();
        }
        synchronized (outputThroughput) {
            outputThroughput.clear();
        }
        synchronized (outputLatencyAvg) {
            outputLatencyAvg.clear();
        }
        synchronized (outputLatency95) {
            outputLatency95.clear();
        }
        synchronized (outputSpectrum) {
            outputSpectrum.clear();
        }
        synchronized (outputStalePropCopy) {
            outputStalePropCopy.clear();
        }
        synchronized (outputLatencyAvgCopy) {
            outputLatencyAvgCopy.clear();
        }
        synchronized (outputThroughputCopy) {
            outputThroughputCopy.clear();
        }
        synchronized (outputLatency95Copy) {
            outputLatency95Copy.clear();
        }
    }
}
