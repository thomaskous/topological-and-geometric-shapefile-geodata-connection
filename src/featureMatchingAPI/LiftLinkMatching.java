package preprocessing.featureMatchingAPI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;

import preprocessing.StartConfiguration;
import preprocessing.connectionsAPI.Candidate;
import preprocessing.util.FeatureOperations;
import preprocessing.util.GeometryOperations;

/**
 * @author Thomas Kouseras
 * A class which specializes the FeatureMatching API for Lift to Lift feature matching
 */
public class LiftLinkMatching extends FeatureMatching {

	public LiftLinkMatching(SimpleFeature[] lifts) {
		super(lifts);
		
		this.init();
	}
	
	public void init() {
		//set max distance threshold according to which candidates are found
		this.max_threshold = (double) StartConfiguration.getInstance().getLift_distances()[0] + 0.5; //max distance tolerance to fetch lift candidate links (LIFTS_DIST first value in the config file)
		//set id_prefix, used to build an r_id
		this.id_prefix = "2";
		System.out.println("------------------------\n");
		System.out.println("Processing Lifts ... ...");
		this.matchingPath = "liftLinks";
		Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Proccessing Lifts");
		
		//helper variables to measure method execution time
		long startTime = System.currentTimeMillis(), endTime, elapsed;
		List<Candidate> candidates = ((LiftLinkMatching) this).getLiftCandidates();
		endTime = System.currentTimeMillis();
		elapsed = endTime - startTime;
		
		this.printAndFinalizeLinks(candidates, elapsed);
	}
	
	/**
	 * This method returns links between lifts.
	 * In a lift link the start point is the upper point of the lower feature and end point is the lower point of the upper one
	 * @return
	 */
	protected List<Candidate> getLiftCandidates() {
		//method parameters declaration
		String de_name, xml_gid_start = null, xml_gid_end = null;
		
		Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Features_in size is: " + this.getFeatures_in().length);
		//print metaData info
		System.out.println("max allowed distance is: " + this.getMax_threshold());
		System.out.println("max allowed height diffrenece for is: " + StartConfiguration.getInstance().getLift_heights()[0]); //max height tolerance to create a lift link (LIFTS_HEIGHT_DIF first value in the config file)
		
		List<SimpleFeature> lifts_out = new ArrayList<SimpleFeature>(Arrays.asList(this.getFeatures_in()));
		List<Candidate> candidates = new ArrayList<Candidate>();
		Candidate cand;
		
		for (SimpleFeature lift_in: this.getFeatures_in()) {
			
//if (!lift_in.getAttribute("DE_GR_L_1").equals("Sportgastein")) continue;	
			//fetch geometry, coordinate sequence and create start and end point
			Coordinate [] lift_in_endpoints = GeometryOperations.getOrderedEndPoints(lift_in);
			de_name = lift_in.getAttribute("DE_GR_L_0") + " - " + lift_in.getAttribute("DE_GR_L_1");

			//remove first item of the matched_feature list, to avoid checking a feature over itself
			lifts_out.remove(0);

			//iterate over the out set of features
			for (SimpleFeature lift_out: lifts_out){
				
				//topological check at top level. lower_in can connect to upper_out and upper_in to lower_out
				//prepare to create candidates
				Coordinate [] candidatePointPair = null;

				//fetch geometry, coordinate sequence and create start and end point
				Coordinate [] lift_out_endpoints = GeometryOperations.getOrderedEndPoints(lift_out);
				
				//check if lower_in matches upper_out
				if (lift_in_endpoints[0].distance(lift_out_endpoints[1]) < this.getMax_threshold()) {
					//candidate found, traverse direction from upper_out to lower_in
					candidatePointPair = new Coordinate[]{lift_out_endpoints[1], lift_in_endpoints[0]};
					xml_gid_start = lift_out.getAttribute("XML_GID").toString();
					xml_gid_end = lift_in.getAttribute("XML_GID").toString();
				} //check if upper_in matches lower_out 
				else if (lift_in_endpoints[1].distance(lift_out_endpoints[0]) < this.getMax_threshold()){
					//candidate found, traverse direction from upper_in to lower_out
					candidatePointPair = new Coordinate[]{lift_in_endpoints[1], lift_out_endpoints[0]};
					xml_gid_start = lift_in.getAttribute("XML_GID").toString();
					xml_gid_end = lift_out.getAttribute("XML_GID").toString();
				}
				
				if (candidatePointPair != null && candidatePointPair.length>0) {
					cand = new Candidate(candidatePointPair, "LiftLink", xml_gid_start, xml_gid_end, de_name);
					candidates.add(cand);
				} 
			} 
		}
		//Clean duplicate candidates.
		cleanDuplicates(candidates);
		
		//***** METHOD END ********************		
		return candidates;
	}
	
	/**
	 * This method returns lift features with the appropriate featureType and attributes compatible for the merging process with othe features
	 * @return
	 */
	public static SimpleFeatureCollection getLiftsToMerge( SimpleFeature[] lifts) {
		List<SimpleFeature> mergeLifts = new ArrayList<SimpleFeature>();
		SimpleFeatureType mergeType = FeatureOperations.makeLineStringFeatureType(lifts[0].getFeatureType().getCoordinateReferenceSystem(), "merged_pivots"); 
	   	//list to hold the assigned rids
	   	List<Integer> assigned = new ArrayList<Integer>();
		
		for (SimpleFeature lift: lifts) {
//if (!lift.getAttribute("DE_GR_L_1").equals("Sportgastein")) continue;			
			Coordinate[] coords =  GeometryOperations.getOrderedEndPoints(lift);
			SimpleFeature mergelift = FeatureMatching.featOps.getFeatureFromCoordinates(mergeType, coords);
		   	String deName = lift.getAttribute("DE_GR_L_0").toString() + lift.getAttribute("DE_GR_L_1").toString();
		   	double length = ((Geometry)lift.getDefaultGeometry()).getLength();
	   		//build unique r_id
		   	int segmentNumber = 1;
            int liftNumber = Integer.parseInt(lift.getAttribute("XML_GID").toString());
	   		int rid = Integer.parseInt("2"+ digitRectifier(liftNumber, 5) + digitRectifier(segmentNumber, 3));
	   		while (assigned.contains(rid)) {
	   			rid++;
	   			segmentNumber++;
	   		}
	   		assigned.add(rid);
	   		segmentNumber++;

			/* new feature type should contain "XML_TYPE", "de_name", "difficulty", "gid_start", "gid_end", 
			"duration", "length", "r_length", "height", "rate", "cost", "r_cost", "open", "start_z", "end_z", "r_id"*/ 	
		   	Map <String, Object> attrs = new LinkedHashMap<String, Object>();
		   	attrs.put("XML_TYPE", "lifts");
		   	attrs.put("de_name", deName);
		   	attrs.put("difficulty", (int) 0);
		   	attrs.put("source", String.valueOf(0));
		   	attrs.put("target", String.valueOf(0));
		   	attrs.put("duration", Double.parseDouble("0"));
		   	attrs.put("length", length);
		   	attrs.put("r_length", length);
            attrs.put("r_rev_c", length * 50);
            attrs.put("rev_c", length * 50);
		   	attrs.put("cost_1", 15 * length);
		   	attrs.put("cost_2", 15 * length);
		   	attrs.put("cost_3", 15 * length);
		   	attrs.put("r_cost_1", 15 * length);
		   	attrs.put("r_cost_2", 15 * length);
		   	attrs.put("r_cost_3",15 * length);
		   	attrs.put("open", (int) 0);
		   	attrs.put("start_z", coords[0].z);
		   	attrs.put("end_z", coords[coords.length-1].z);
		   	attrs.put("r_id", rid);
			//add attribute
			for (Map.Entry<String, Object> attribute : attrs.entrySet()){
				mergelift = FeatureOperations.addAttribute(mergelift, attribute.getKey(), attribute.getValue());
				mergelift.setAttribute(attribute.getKey(), attribute.getValue());
			}
			mergeLifts.add(mergelift);
		}
		
		//return feature collection
		SimpleFeatureCollection collectionReturn = new ListFeatureCollection(mergeLifts.get(0).getFeatureType(), mergeLifts);
		return collectionReturn;
	}

}
