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


	if (a == b)
	    if (a.getDictWrite() == null)
		return 2; // zone missing a dictating write
	    else if (a.hasMoreThanOneDictWrite())
		return 3;
	    else
		return 0;
        else if (aIsForward && bIsForward) {
            // two forward zones
            if (aMaxReadStart < bWriteFinish || bMaxReadStart < aWriteFinish) {
                // no conflict
                return 0;
            } else {
                // yes conflict
		long baseScore = Math.min(aMaxReadStart - bWriteFinish, bMaxReadStart - aWriteFinish)/2;
		long extraScore = Long.MAX_VALUE;
		if (aWriteFinish < bWriteStart) {
		    extraScore = Math.max(bWriteStart - aWriteFinish, (bMaxReadStart - bWriteFinish)/2);
		} else if (bWriteFinish < aWriteStart) {
		    extraScore = Math.max(aWriteStart - bWriteFinish, (aMaxReadStart - aWriteFinish)/2);
		} else {
		    extraScore = Math.min((aMaxReadStart - aWriteFinish)/2, (bMaxReadStart - bWriteFinish)/2);   
		}
                return Math.min(baseScore, extraScore);
            }
        } else if (aIsForward && !bIsForward) {
            // one forward zone
            if (aMaxReadStart < bWriteFinish || bWriteStart < aWriteFinish) {
                // no conflict
                return 0;
            } else {
                // yes conflict
                return Math.min((aMaxReadStart - bWriteFinish)/2, bWriteStart - aWriteFinish);
            }
        } else if (!aIsForward && bIsForward) {
            // one forward zone
            if (bMaxReadStart < aWriteFinish || aWriteStart < bWriteFinish) {
                // no conflict
                return 0;
            } else {
                // yes conflict
                return Math.min((bMaxReadStart - aWriteFinish)/2, aWriteStart - bWriteFinish);
            }
        } else {
            // two backward zones, no conflict
            return 0;
        }
    }
}
