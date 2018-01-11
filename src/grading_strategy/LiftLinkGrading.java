package preprocessing.grading_strategy;

import java.util.Arrays;

import preprocessing.StartConfiguration;
import preprocessing.connectionsAPI.Link;

/**
 * @author Thomas Kouseras
 * Implements the Grading Strategy of the Lift Links
 */
public class LiftLinkGrading implements GradingStrategy {
	
	/**
	 * The Grading of the LIFT LINKS needs 4 distance values and 4 height values to work
	 * The first item found in the lift_distances is the max distance to get candidates
	 * The first item found in the height_distances is the max height to retain a candidate
	 * Grading strategy is applied by combining each distance group with two height groups. Specifically:
	 * 	- candidates with smaller distance threshold (default: <50m), that is distance < lift_distance[3] awarded two rates combined with two larger heights:
	 * 		- height_distances[1] < candidate height < height_distances[0]  -> Rate B (default: 10< <35)
	 * 		- candidate height < height_distances[1] 						-> Rate A (default: <10)
	 *  - candidates with length < lift_distance[2] (default: 50m <  < 95m) are also checked over the two larger heights:
	 * 		- height_distances[1] < candidate height < height_distances[0]  -> Rate B (default: 10< <35)
	 * 		- candidate height < height_distances[1] 						-> Rate A (default: <10)
	 * 	- candidates with length, that is distance < lift_distance[1] (default: 95m < < 160m) are compared over the two larger heights:
	 * 		- height_distances[2] < candidate height 						-> Delete (default: >5)
	 * 		- height_distances[3] < candidate height < height_distances[2]  -> Rate C (default: 1< <5)
	 * 		- candidate height < height_distances[3] 						-> Rate B (default: <1)
	 * 	- max distance candidates (default: 160m< <200m) are awarded two rates combined with two larger heights:
	 * 		- height_distances[2] < candidate height 						-> Delete (default: >5)
	 * 		- height_distances[3] < candidate height < height_distances[2]  -> Rate D (default: 1< <5)
	 * 		- candidate height < height_distances[3] 						-> Rate B (default: <1)
	 */
	@Override
	public void grade(Link link) {
		double length = link.getDistance();
		double heightDif = link.getHeightDiff();
		int [] distThresholds = Arrays.copyOf(StartConfiguration.getInstance().getLift_distances(), StartConfiguration.getInstance().getLift_distances().length);
		int [] heightThresholds = Arrays.copyOf(StartConfiguration.getInstance().getLift_heights(), StartConfiguration.getInstance().getLift_heights().length);
		
		//case: length < 50 m
		if (length < distThresholds[3]) {	
			if (heightDif <= heightThresholds[1]) {
				//height diff < 10 m -> Rate A
				link.setGrade('A');
			} else if (heightDif <= heightThresholds[0]) {
				//height diff < 35 m -> Rate B
				link.setGrade('B');
			}
		} 
		//case: 50 m < length < 95 m
		else if (length < distThresholds[2]) {
			if (heightDif <= heightThresholds[1]) {
				//height diff < 10 m -> Rate A
				link.setGrade('A');
			} else if (heightDif <= heightThresholds[0]) {
				//height diff < 35 m -> Rate B
				link.setGrade('B');
			}
		}
		//case:95 m < length < 160 m
		else if (length < distThresholds[1]) {	
			if (heightDif <= heightThresholds[3]) {
				//height diff < 1m -> Rate B
				link.setGrade('B');
			} else if (heightDif <= heightThresholds[2]) {
				//height diff < 5 m -> Rate C
				link.setGrade('C');
			} else {
				//height diff > 5 m -> Delete Link
				link.setGrade('E');
			}
		} 
		//case 160 m < length < 200 m
		else if (length <= distThresholds[0]) {
			if (heightDif <= heightThresholds[3]) {
				//height diff < 1m -> Rate B
				link.setGrade('B');
			} else if (heightDif <= heightThresholds[2]) {
				//height diff < 5 m -> Rate D
				link.setGrade('D');
			} else {
				//height diff > 5 m -> Delete Link
				link.setGrade('E');
			}
			
		}

	}

}
