package preprocessing.connectionsAPI;

import java.util.Map;
import com.vividsolutions.jts.geom.Coordinate;

/**
 * 
 * @author Thomas Kouseras
 * An abstract class that provides common functionality to Candidate and Link classes
 */
public abstract class AbstractPointPair {
	protected Coordinate start_point;
	protected Coordinate end_point;
		
	public AbstractPointPair(Coordinate start, Coordinate end) {
		this.start_point = start;
		this.end_point = end;
	}
	
	public abstract double getHeightDiff();
	
	public abstract double getDistance();

	public abstract Map<String, Object> getAttributes();
	
	public Coordinate getStartPoint() {
		return this.start_point;
	}

	public Coordinate getEndPoint() {
		return this.end_point;
	}
}
