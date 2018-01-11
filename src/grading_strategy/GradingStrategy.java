package preprocessing.grading_strategy;

import preprocessing.connectionsAPI.Link;

/**
 * @author Thomas Kouseras
 * An interface introducing a grading strategy, that will be used as a grading data type in the Link
 */
public interface GradingStrategy {
	public void grade(Link link);
}
