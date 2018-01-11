package preprocessing.connectionsAPI;

import java.util.LinkedHashMap;
import java.util.Map;

import com.vividsolutions.jts.geom.Coordinate;

import preprocessing.StartConfiguration;

/**
 * 
 * @author Thomas Kouseras
 * class that implements a link Candidate. Class constructor validates the candidate and if height difference is within the given 
 * threshold a new Link is constructed
 */
public class Candidate extends AbstractPointPair {
	private double maxAllowedHeightDif;
	private final String TYPE; 
	private String xml_gid_start;
	private String xml_gid_end;
	private String de_name;
	
	/**
	 * Class constructor
	 */
	public Candidate (Coordinate start, Coordinate end, String linkType, String id_start, String id_end, String region){
		super(start, end);
		this.TYPE = linkType;
		this.xml_gid_start = id_start;
		this.xml_gid_end = id_end;
		this.de_name = region;
		setMaxAllowedHeightDif();
	}
	
	/**
	 * Alternative class constructor 
	 */
	public Candidate(Coordinate[] endpoints, String linkType,  String id_start, String id_end, String region) {
		this(endpoints[0], endpoints[1], linkType,  id_start, id_end, region);
	}
	
	/**
	 * sets the maximum allowed end-point height difference, which  is used in Candidate validation,
	 * when a Candidate is evaluated whether it should turn to a Link
	 */
	private void setMaxAllowedHeightDif() {
		switch(this.TYPE) {
			case "LiftLink":
				this.maxAllowedHeightDif = (double) StartConfiguration.getInstance().getLift_heights()[0] + 0.5;
				break;
			case "SlopeLift":
				this.maxAllowedHeightDif = (double) StartConfiguration.getInstance().getSlope_heights()[0] + 0.5;
				break;
			case "Slope2Slope":
				this.maxAllowedHeightDif = (double) StartConfiguration.getInstance().getSlope_heights()[5] + 0.5;
				break;
			case "BusLink":
				this.maxAllowedHeightDif = (double) StartConfiguration.getInstance().getBus_heights()[0] + 0.5;
		}
	}

	
	@Override
	public double getDistance() {
		double dist = this.getStartPoint().distance(getEndPoint());
		return dist;
	}
	
	/**
	 * Height difference of from end to start point. 
	 * Lift links go upwards, difference should be positive.
	 * Slope links go downwards, difference should be negative. 
	 */
	@Override
	public double getHeightDiff() {
		return this.end_point.z/10 - this.start_point.z/10;
	}
	
	/**
	 * Validation with use of semantic and geometric rules
	 * Height differences larger than 35 meters for lift2lift connections are disqualified even if traverse trajectory is downwards;
	 * Height differences larger than 30 meters for slope2lift connections are disqualified even if traverse trajectory is downwards;
	 * Height differences larger than 5 meters for slope2slope connections are disqualified;
	 * Height differences larger than 20 meters for bus link connections are disqualified;
	 * Validation with use of semantic and geometric rules
	 */
	private boolean validate() {
		boolean isValid = false;
		
		if (this.TYPE.equals("Slope2Slope")) {
			isValid = getHeightDiff() < this.maxAllowedHeightDif ? true : false;
		} else {
			isValid = Math.abs(getHeightDiff()) < this.maxAllowedHeightDif ?  true : false;
		}
		
		return isValid;
	}
	
	/**
	 * creates the correspondent Link object according to the Candidate's TYPE attribute
	 * @return Link
	 */
	public Link createLink(){
		boolean valid = validate();
		if (valid) {
			Link newLink;
			switch (this.TYPE) {
				case "LiftLink":
					newLink = new LiftLink(this.start_point, this.end_point);
					break;
				case "SlopeLift":
					newLink = new SlopeLift(this.start_point, this.end_point);
					break;
				case "Slope2Slope":
					newLink = new SlopeLink(this.start_point, this.end_point);
					break;
				case "BusLink":
					newLink = new BusLink(this.start_point, this.end_point);
					break;
				default:
					newLink = null;
					break;
			}
			return newLink;
		}
		return null;
	}

	@Override
	public Map<String, Object> getAttributes() {
		Map<String, Object> attrs = new LinkedHashMap<String, Object>();
		attrs.put("de_name", this.de_name);
		attrs.put("gid_start", this.xml_gid_start);
		attrs.put("gid_end", this.xml_gid_end);
		attrs.put("length", this.getDistance());
		attrs.put("heightDif", this.getHeightDiff());
		return attrs;
	}

	
	@Override
	public String toString() {
		return "Candidate " + TYPE + "[xml_gid_start=" + xml_gid_start 
				+ ", xml_gid_end=" + xml_gid_end + ", height=" + getHeightDiff() + "]";
	}

		
	public String getXml_gid_start() {
		return xml_gid_start;
	}
	
	public String getXml_gid_end() {
		return xml_gid_end;
	}
	
	public String getDe_name() {
		return de_name;
	}
	
	public String getType() {
		return TYPE;
	}

}
