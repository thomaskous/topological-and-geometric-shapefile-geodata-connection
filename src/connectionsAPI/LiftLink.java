package preprocessing.connectionsAPI;

import com.vividsolutions.jts.geom.Coordinate;
import preprocessing.grading_strategy.LiftLinkGrading;

/**
 * 
 * @author Thomas Kouseras
 * A class which implements the Link abstract type and represents the lift connections
 */
public class LiftLink extends Link {

	public LiftLink(Coordinate start, Coordinate end) {
		super(start, end, new LiftLinkGrading());
		applyGrading();
		buildAttributeList();
	}

	@Override
	protected void applyGrading() {
		strategy.grade(this);
	}
	
}
