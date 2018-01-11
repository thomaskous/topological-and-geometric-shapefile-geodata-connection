package preprocessing.grading_strategy;

import preprocessing.StartConfiguration;
import preprocessing.connectionsAPI.Link;

/**
 * @author Thomas Kouseras
 * Implements the Grading Strategy of the Slope Links
 */
public class SlopeLinkGrading implements GradingStrategy {
	
	/**
	 * The Grading of the SLOPE LINKS uses two length groups and 3 height groups. 
	 * Two define the distance groups slope_endpoint_distance and slope_midpoint_distance from the user configuration is used 
	 * Grading strategy is applied by combining distance with height groups. Specifically:
	 * 	- links with length < slope_midpoint_dist (default: length < 10m):
	 * 		- 1m < Link height < 5m 	-> Rate B
	 * 		- -15m < Link height <= 1m 	-> Rate A 
	 * 		- Link height <= -15m        -> Delete
	 * 	- links with length: slope_midpoint_dist < Link length < slope_endpoint_distance (default 10m < length < 20m):
	 * 		- 1m < Link height < 5m 	-> Rate C
	 * 		- -15m < Link height <= 1m 	-> Rate B
	 * 		- Link height <= -15m        -> Delete
	 */
	@Override
	public void grade(Link link) {
		double length = link.getDistance(),
			   heightDif = link.getHeightDiff();
		double [] distThresholds = {StartConfiguration.getInstance().getSlope_endpoint_dist(), StartConfiguration.getInstance().getSlope_midpoint_dist()[1]};
		double [] heightThresholds = {-15.00, 1.00, 5.00}; //default hardcoded values
		//case: length < 10 m
		if (length < distThresholds[1]) {
			if (heightDif <= heightThresholds[0]) {
				//link height <= -15 m -> Delete
				link.setGrade('E');
			} else if (heightDif <= heightThresholds[1]) {
				//link height <= 1 m -> Rate A
				link.setGrade('A');
			} else {
				//link height < 5 m -> RateB
				link.setGrade('B');
			}
		} 
		//case: 10 m < length < 20 m
		else if (length <= distThresholds[0]) {
			if (heightDif <= heightThresholds[0]) {
				//link height <= -15 m -> Delete
				link.setGrade('E');
			} else if (heightDif <= heightThresholds[1]) {
				//link height <= 1m -> Rate B
				link.setGrade('B');
			} else {
				//link height < 5m -> Rate C
				link.setGrade('C');
			}
		}
	}

}
