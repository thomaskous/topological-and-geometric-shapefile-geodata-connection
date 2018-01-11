package preprocessing.grading_strategy;

import java.util.Arrays;

import preprocessing.StartConfiguration;
import preprocessing.connectionsAPI.Link;

/**
 * @author Thomas Kouseras
 * Implements the Grading Strategy of the Slope-Lift Links
 */
public class SlopeLiftGrading implements GradingStrategy {
	
	/**
	 * The Grading of the SLOPE-LIFT LINKS needs 5 distance values and 6 height values to work
	 * The first item found in the slope_distances is the max distance allowed to get Candidates
	 * The first item found in the slope_height_dif is the max height difference allowed to retain a Candidate as a Link
	 * Grading strategy is applied by combining each distance group with three height groups. Specifically:
	 * 	- candidates with length smaller than the lower distance threshold (default: <40m), that is distance < slope_distance[4]:
	 * 		- height_distances[2]< candidate height < height_distances[0] -> Rate C (default: 20< <30)
	 * 		- height_distances[3]< candidate height < height_distances[2] -> Rate B (default: 15< <20)
	 * 		- candidate height < height_distances[3] -> Rate A (default: <15)
	 *  - candidates with length < slope_distance[3] (default:40m< <60m) are also divided in 3 rate groups:
	 * 		- height_distances[2]< candidate height < height_distances[0] -> Rate C (default: 20< <30)
	 * 		- height_distances[3]< candidate height < height_distances[2] -> Rate B (default: 15< <20)
	 * 		- candidate height < height_distances[3] -> Rate A (default: <15)
	 * 	- candidates with length < slope_distance[2] (default: 60m< <80m) are divided in 4 rate groups:
	 * 		- height_distances[1]< candidate height						  -> Delete (default: >25)
	 * 		- height_distances[3]< candidate height < height_distances[1] -> Rate D (default: 15< <25)
	 * 		- height_distances[4]< candidate height < height_distances[3] -> Rate B (default: 10< <15)
	 * 	 	- candidate height < height_distances[4] 					  -> Rate A (default: <10)
	 * 	- candidates with length < slope_distance[1] (default: 80m< <100m) are divided in 3 rate groups:
	 * 		- height_distances[1]< candidate height						  -> Delete (default: >25)
	 * 		- height_distances[4]< candidate height < height_distances[1] -> Rate D (default: 10< <25)
	 * 	 	- height_distances[5]< candidate height < height_distances[4] -> Rate C (default: 5< <10)
	 * 		- candidate height < height_distances[5] 					  -> Rate B (default: <5)
	 * 	- max distance candidates (default: 100m< <120m) are divided in 2 rate groups:
	 * 		- height_distances[4]< candidate height						  -> Delete (default: >10)
	 * 	 	- height_distances[5]< candidate height < height_distances[4] -> Rate D (default: 5< <10)
	 * 		- candidate height < height_distances[5] 					  -> Rate C (default: <5)
	 */
	@Override
	public void grade(Link link) {
		double length = link.getDistance(), 
			   heightDif = link.getHeightDiff();
		int [] distThresholds = Arrays.copyOf(StartConfiguration.getInstance().getSlope_distances(), StartConfiguration.getInstance().getSlope_distances().length), 
			   heightThresholds = Arrays.copyOf(StartConfiguration.getInstance().getSlope_heights(), StartConfiguration.getInstance().getSlope_heights().length);
		
		//case: length < 40 m
		if (length < distThresholds[4]) {
			if (heightDif <= heightThresholds[3]) {
				//link height < 15 m -> Rate A
				link.setGrade('A');
			} else if (heightDif <= heightThresholds[2]) {
				//link height < 20 m -> Rate B
				link.setGrade('B');
			} else {
				//link height < 30 m -> Rate C
				link.setGrade('C');
			}
		} 
		//case: 40 m < length < 60 m
		else if (length < distThresholds[3]) {
			if (heightDif <= heightThresholds[3]) {
				//link height < 15m -> Rate A
				link.setGrade('A');
			} else if (heightDif <= heightThresholds[2]) {
				//link height < 20m -> Rate B
				link.setGrade('B');
			} else {
				//link height < 30m -> Rate C
				link.setGrade('C');
			}
		}
		//case 60 m < length < 80 m
		else if (length < distThresholds[2]) {
			if (heightDif <= heightThresholds[4]) {
				//link height < 10m -> Rate A
				link.setGrade('A');
			} else if (heightDif <= heightThresholds[3]) {
				//link height < 15m -> Rate B
				link.setGrade('B');
			} else if (heightDif <= heightThresholds[1]) {
				//link height < 25m -> Rate D
				link.setGrade('D');
			} else {
				//link height > 25m -> Delete Link
				link.setGrade('E');
			}
		}
		//case 80 m < length < 100 m
		else if (length < distThresholds[1]) {
			if (heightDif <= heightThresholds[5]) {
				//link height < 5m -> Rate B
				link.setGrade('B');
			} else if (heightDif <= heightThresholds[4]) {
				//link height < 10m -> Rate C
				link.setGrade('C');
			} else if (heightDif <= heightThresholds[1]) {
				//link height < 25m -> Rate D
				link.setGrade('D');
			} else {
				//link height > 25m -> Delete Link
				link.setGrade('E');
			}
		}
		//case 100 m < length < 120 m
		else if (length <= distThresholds[0]) {
			if (heightDif <= heightThresholds[5]) {
				//link height < 5m -> Rate C
				link.setGrade('C');
			} else if (heightDif <= heightThresholds[4]) {
				//link height < 10m -> Rate D
				link.setGrade('D');
			} else {
				//link height > 10m -> Delete Link
				link.setGrade('E');
			}
		}
	}

}
