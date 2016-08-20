package ca.uwaterloo.watca;

import java.util.ArrayList;
import java.util.List;

/**
 * Score function based on Lamport's regular register.
 */
public class RegularScoreFunction implements ScoreFunction {

    public List<Long> getScores(Cluster a, Cluster b) {
        List<Long> ret = new ArrayList(1);
        ret.add(getScore(a, b));
        return ret;
    }

    public long getScore(Cluster a, Cluster b) {
        long aWriteStart = a.getWriteStart();
        long aWriteFinish = a.getWriteFinish();
        long aMaxReadStart = a.getMaxReadStart();
        long bWriteStart = b.getWriteStart();
        long bWriteFinish = b.getWriteFinish();
        long bMaxReadStart = b.getMaxReadStart();
        boolean aIsForward = aWriteFinish < aMaxReadStart;
        boolean bIsForward = bWriteFinish < bMaxReadStart;

        if (aIsForward && bIsForward) {
            // two forward zones
            if (aMaxReadStart < bWriteFinish || bMaxReadStart < aWriteFinish) {
                // no conflict
                return 0;
            } else {
                // yes conflict
                return Math.min(aMaxReadStart - bWriteFinish, bMaxReadStart - aWriteFinish);
            }
        } else if (aIsForward && !bIsForward) {
            // one forward zone
            if (aMaxReadStart < bWriteFinish || bWriteStart < aWriteFinish) {
                // no conflict
                return 0;
            } else {
                // yes conflict
                return Math.min(aMaxReadStart - bWriteFinish, bWriteStart - aWriteFinish);
            }
        } else if (!aIsForward && bIsForward) {
            // one forward zone
            if (bMaxReadStart < aWriteFinish || aWriteStart < bWriteFinish) {
                // no conflict
                return 0;
            } else {
                // yes conflict
                return Math.min(bMaxReadStart - aWriteFinish, aWriteStart - bWriteFinish);
            }
        } else {
            // two backward zones, no conflict
            return 0;
        }
    }
}
