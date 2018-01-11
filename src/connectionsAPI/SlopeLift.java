package preprocessing.connectionsAPI;

import com.vividsolutions.jts.geom.Coordinate;
import preprocessing.grading_strategy.SlopeLiftGrading;

/**
 * 
 * @author Thomas Kouseras
 * A class which implements the Link abstract type and represents the slope - lift connections
 */
public class SlopeLift extends Link {

	public SlopeLift (Coordinate start, Coordinate end) {
		super(start, end, new SlopeLiftGrading());
		applyGrading();
		buildAttributeList();
	}

	@Override
	protected void applyGrading() {
		strategy.grade(this);		
	}

}
