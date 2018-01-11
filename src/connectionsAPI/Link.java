package preprocessing.connectionsAPI;

import java.util.LinkedHashMap;
import java.util.Map;
import com.vividsolutions.jts.geom.Coordinate;
import preprocessing.grading_strategy.GradingStrategy;

/**
 * @author Thomas Kouseras
 * An abstract class that defines common functionality for all type of links
 * as in Lift Link, SlopeLift, Slope and Bus link
 */
public abstract class Link extends AbstractPointPair{
	protected Map<String, Object> attributes;
	protected char grade;
	protected GradingStrategy strategy;
	
	public Link(Coordinate start, Coordinate end, GradingStrategy linkStrategy){
		super(start, end);
		attributes = new LinkedHashMap<String, Object>();
		this.attributes.put("r_id", new Integer(0));
		this.attributes.put("de_name", "");
		this.attributes.put("gid_start", "");
		this.attributes.put("gid_end", "");
		this.strategy = linkStrategy;
	}
	
	protected abstract void applyGrading();
	
	public double getHeightDiff() {
		double diff = this.getEndElevation() - this.getStartElevation();
		return diff;
	};
	
	public double getDistance(){
		double dist = this.getStartPoint().distance(getEndPoint());
		return dist;
	}
	
	protected void buildAttributeList() {
		this.attributes.put("length", this.getDistance());
		this.attributes.put("height", this.getHeightDiff());
		this.attributes.put("rate", String.valueOf(this.grade));
	}
	
	public Map<String, Object> getAttributes(){
		return this.attributes;
	}
	
	public void setAttributes(Map<String, Object> attrs){
		this.attributes = attrs;
	}
	
	public String getAttributeValue(String attrName){
		String value = this.attributes.get(attrName).toString();
		return value;
	}
	
	public void setAttributeValue(String name, Object value){
		this.attributes.put(name, value);
	}
	
	public double getStartElevation(){
		return this.start_point.z/10;
	};
	
	public double getEndElevation(){
		return this.end_point.z/10;
	};
	
	public Coordinate[] getEndPoints() {
		Coordinate [] end_points = new Coordinate[2];
		end_points[0] = this.start_point;
		end_points[1] = this.end_point;
		return end_points;
	};
	
	public char getGrade() {
		return grade;
	}

	public void setGrade(char grade) {
		this.grade = grade;
	}
		
}
