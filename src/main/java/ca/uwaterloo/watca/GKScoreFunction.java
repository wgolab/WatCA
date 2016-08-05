package ca.uwaterloo.watca;

import java.util.ArrayList;
import java.util.List;

/**
 * Score function based on Gibbons and Korach paper on testing shared memories.
 */
public class GKScoreFunction implements ScoreFunction {

    public List<Long> getScores(Cluster a, Cluster b) {
        List<Long> ret = new ArrayList(1);
        ret.add(getScore(a, b));
        return ret;
    }

    public long getScore(Cluster a, Cluster b) {
        long aMinFinish = a.getMinFinish();
        long aMaxStart = a.getMaxStart();
        long bMinFinish = b.getMinFinish();
        long bMaxStart = b.getMaxStart();
        boolean aIsForward = aMinFinish < aMaxStart;
        boolean bIsForward = bMinFinish < bMaxStart;

	if (a == b)
	    if (a.getDictWrite() == null)
		return 2; // zone missing a dictating write
	    else
		return 0;

        if (aIsForward || bIsForward) {
            // at least one forward zone
            if (aMaxStart < bMinFinish || bMaxStart < aMinFinish) {
                // no conflict
                return 0;
            } else {
                // yes conflict
                return 1;
            }
        } else {
            // two backward zones, no conflict
            return 0;
        }
    }
}

