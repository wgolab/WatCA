package ca.uwaterloo.watca;

import java.util.List;

/**
 *
 * @author Wojciech Golab
 */
public interface ScoreFunction {

    public List<Long> getScores(Cluster a, Cluster b);
}

