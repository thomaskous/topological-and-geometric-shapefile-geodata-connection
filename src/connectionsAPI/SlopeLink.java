package preprocessing.connectionsAPI;

import com.vividsolutions.jts.geom.Coordinate;
import preprocessing.grading_strategy.SlopeLinkGrading;

/**
 * 
 * @author Thomas Kouseras
 * A class which implements the Link abstract type and represents the slope connections
 */
public class SlopeLink extends Link {

	public SlopeLink(Coordinate start, Coordinate end) {
		super(start, end, new SlopeLinkGrading());
		applyGrading();
		buildAttributeList();
	}

	@Override
	protected void applyGrading() {
		strategy.grade(this);
	}
	
}
