package preprocessing.connectionsAPI;

import com.vividsolutions.jts.geom.Coordinate;
import preprocessing.grading_strategy.BusLinkStrategy;

/**
 * 
 * @author Thomas Kouseras
 * A class which implements the Link abstract type and represents the links between bus lines with all other features
 */
public class BusLink extends Link {

	public BusLink(Coordinate start, Coordinate end) {
		super(start, end, new BusLinkStrategy());
		applyGrading();
		buildAttributeList();
	}

	@Override
	protected void applyGrading() {
		strategy.grade(this);
	}

}
