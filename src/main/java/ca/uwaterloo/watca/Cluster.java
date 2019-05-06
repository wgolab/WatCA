package ca.uwaterloo.watca;


import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Wojciech Golab
 */
public class Cluster implements Comparable<Cluster> {

    private String key;
    private String value;
    private Set<Operation> reads;
    private Operation dictWrite;
    private boolean moreThanOneDictWrite = false;
    private long minStart;
    private long maxStart;
    private long maxReadStart;
    private long minFinish;
    private long maxFinish;
    private long score = 0;

    public Cluster(String key, String value) {
        this.key = key;
        this.value = value;
        minStart = Long.MAX_VALUE;
        maxStart = Long.MIN_VALUE;
        maxReadStart = Long.MIN_VALUE;
        minFinish = Long.MAX_VALUE;
        maxFinish = Long.MIN_VALUE;
        reads = new HashSet();
        score = 0;
    }

    public boolean equals(Object o) {
        Cluster z = (Cluster) o;
        return key.equals(z.key) && value.equals(z.value);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setScore(long s) {
        score = s;
    }

    public long getScore() {
        return score;
    }

    public void addOperation(Operation op) {
        if (op.isRead()) {
            reads.add(op);
            maxReadStart = Math.max(maxReadStart, op.getStart());
        } else if (op.isWrite()) {
	    if (dictWrite != null) {
		moreThanOneDictWrite = true;
		return;
	    } else {
		dictWrite = op;
	    }
        }
        minStart = Math.min(minStart, op.getStart());
        maxStart = Math.max(maxStart, op.getStart());
        minFinish = Math.min(minFinish, op.getFinish());
        maxFinish = Math.max(maxFinish, op.getFinish());

        long s = op.getStart();
        long f = op.getFinish();
    }

    public boolean overlaps(Cluster z) {
        return !(getRight() < z.getLeft() || z.getRight() < getLeft());
    }

    public long getLeft() {
        return minStart;
    }

    public long getRight() {
        return maxFinish;
    }

    public long getMinStart() {
        return minStart;
    }

    public long getMaxStart() {
        return maxStart;
    }

    public long getMaxReadStart() {
        return maxReadStart;
    }

    public long getMinFinish() {
        return minFinish;
    }

    public long getWriteStart() {
        if (dictWrite == null) {
            return Long.MIN_VALUE;
        } else {
            return dictWrite.getStart();
        }
    }

    public long getWriteFinish() {
        if (dictWrite == null) {
            return Long.MAX_VALUE;
        } else {
            return dictWrite.getFinish();
        }
    }

    public long getMaxFinish() {
        return maxFinish;
    }

    public int compareTo(Cluster z) {
        if (getLeft() == z.getLeft()) {
            if (getRight() == z.getRight()) {
                int cmp = key.compareTo(z.key);
                if (cmp != 0) {
                    return cmp;
                } else {
                    cmp = value.compareTo(z.value);
                    return cmp;
                }
            } else {
                return Long.compare(getRight(), z.getRight());
            }
        } else {
            return Long.compare(getLeft(), z.getLeft());
        }
    }

    public Operation getDictWrite() {
        return dictWrite;
    }

    public int getNumReads() {
        return reads.size();
    }

    public Set<Operation> getReads() {
        return reads;
    }

    public int getNumOperations() {
        return reads.size() + (dictWrite != null ? 1 : 0);
    }

    public boolean hasMoreThanOneDictWrite() {
	return moreThanOneDictWrite;
    }

    public String toString() {
        //key=" + key + " value=" + value + "
        return "<dwstart=" + dictWrite.getStart() + " dwfin=" + dictWrite.getFinish() + " minf=" + minFinish + " maxs=" + maxStart + ">";
        //return value;
    }
}

