package preprocessing.grading_strategy;

import java.util.Arrays;

import preprocessing.StartConfiguration;
import preprocessing.connectionsAPI.Link;

/**
 * @author Thomas Kouseras
 * Implements the Grading Strategy of the Bus Links
 */
public class BusLinkStrategy implements GradingStrategy {

	
	/**
	 * The Grading of the BUS LINKS needs 3 distance and 3 height values to work
	 * The first item found in the bus_distances is the max distance allowed to get candidates
	 * The first item found in the bus_height_dif is the max height to retain a candidate as link
	 * Absolute height_dif is used. That means links are treated evenly independent of which end-point (start or end) is at lower ground
	 * Grading strategy is applied by combining each distance group with two height groups. Specifically:
	 * 	- For links with length smaller than the lower distance threshold (default: <100m), that is distance < bus_distance[2]:
	 * 		- bus_height_dif[1] < link height < bus_height_dif[0]  	-> Rate B (default: 15< <20)
	 *      - bus_height_dif[2] < link height < bus_height_dif[1]  	-> Rate B (default: 10< <15)
	 * 		- link height < bus_height_dif[2] 						-> Rate A (default: <10)
	 *  - For links with length between the first and the second value from the bus_distances (default: 100m <  < 200m):
	 * 		- bus_height_dif[1] < link height < bus_height_dif[0]  	-> Rate C (default: 15< <20)
	 *      - bus_height_dif[2] < link height < bus_height_dif[1]  	-> Rate B (default: 10< <15)
	 * 		- link height < bus_height_dif[2] 						-> Rate A (default: <10)
	 * 	- For links with length between the second and the third value (default: 200m < < 350m):
	 * 		- bus_height_dif[1] < link height < bus_height_dif[0]  	-> Rate D (default: 15< <20)
	 *      - bus_height_dif[2] < link height < bus_height_dif[1]  	-> Rate D (default: 10< <15)
	 * 		- link height < bus_height_dif[2] 						-> Rate B (default: <10)
	 */
	@Override
	public void grade(Link link) {
		double length = (double) Math.floor(link.getDistance());
		double heightDif = (double) Math.floor(Math.abs(link.getHeightDiff()));
		int [] distThresholds = Arrays.copyOf(StartConfiguration.getInstance().getBus_distances(), StartConfiguration.getInstance().getBus_distances().length);
		int [] heightThresholds = Arrays.copyOf(StartConfiguration.getInstance().getBus_heights(), StartConfiguration.getInstance().getBus_heights().length);
		
		
		//case length < 100m / distThresholds[2]
		if (length <= distThresholds[2]) {
			//height_dif < bus_height_diff[2] / 10m
			if (heightDif <= heightThresholds[2]) {
				link.setGrade('A');
			}
			//height_dif < bus_height_diff[1] / 15m
			else if (heightDif <= heightThresholds[1]) {
				link.setGrade('B');
			}
			//height_dif < bus_height_diff[0] / 20m
			else if (heightDif <= heightThresholds[0]) {
				link.setGrade('B');
			}
		}
		//case length < 200m / distThresholds[1]
		else if (length <= distThresholds[1]) {
			//height_dif < bus_height_diff[2] / 10m
			if (heightDif <= heightThresholds[2]) {
				link.setGrade('A');
			}
			//height_dif < bus_height_diff[1] / 15m
			else if (heightDif <= heightThresholds[1]) {
				link.setGrade('B');
			}
			//height_dif < bus_height_diff[0] / 20m
			else if (heightDif <= heightThresholds[0]) {
				link.setGrade('C');
			}
		}
		//case length < 350m / distThresholds[0]
		else if (length <= distThresholds[0]) {
			//height_dif < bus_height_diff[2] / 10m
			if (heightDif <= heightThresholds[2]) {
				link.setGrade('B');
			}
			//height_dif < bus_height_diff[1] / 15m
			else if (heightDif <= heightThresholds[1]) {
				link.setGrade('D');
			}
			//height_dif < bus_height_diff[0] / 20m
			else if (heightDif <= heightThresholds[0]) {
				link.setGrade('D');
			}
		}
	}

}
