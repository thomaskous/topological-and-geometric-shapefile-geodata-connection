package preprocessing.featureMatchingAPI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.index.kdtree.KdNode;
import com.vividsolutions.jts.index.kdtree.KdTree;
import com.vividsolutions.jts.linearref.LengthIndexedLine;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import preprocessing.StartConfiguration;
import preprocessing.connectionsAPI.Candidate;
import preprocessing.util.FeatureOperations;
import preprocessing.util.GeometryOperations;
import preprocessing.util.FileOperations;

/**
 * @author Thomas Kouseras
 * A class which specializes the FeatureMatching API for Bus to every other feature matching
 */
public class BusLinkMatching extends FeatureMatching {
	
	private List<SimpleFeature> busStops = null;
	private List<SimpleFeature> lifts = null;
	
	public BusLinkMatching(SimpleFeature[] buses, SimpleFeature[] stops, SimpleFeature[] slopes, SimpleFeature[] lifts) {
		super(buses, slopes);
		
		//Features as ArrayList, to be able to add and delete checked items
		this.busStops = new ArrayList<SimpleFeature>(Arrays.asList(stops));
		this.lifts = new ArrayList<SimpleFeature>(Arrays.asList(lifts));
		
		System.out.println("------------------------\n");
		//clean bust stops
		System.out.println("Cleaning Bus Stop Duplicates:");
		FeatureOperations.cleanDuplicateFeatures(busStops, "BUS STOP");
		
		this.init();
	}

	@Override
	public void init() {
		//set max distance threshold according to which candidates are found.
		this.max_threshold = (double) StartConfiguration.getInstance().getBus_distances()[0] + 0.5;
		//set id_prefix, used to build an r_id
		this.id_prefix = "3";

		System.out.println("\nProcessing bus lines ... ...");
		Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Proccessing Buses");
		this.matchingPath = "busLinks";
		//helper variables to measure method execution time
		long startTime = System.currentTimeMillis(), endTime, elapsed;
		List<Candidate> candidates = ((BusLinkMatching) this).getBusCandidateLinks();
		endTime = System.currentTimeMillis();
		elapsed = endTime - startTime;
				
		this.printAndFinalizeLinks(candidates, elapsed);
	}
	
	/**
	 * This method finds bus stop connections to lifts and slopes
	 * Bus stop without elevation info are not processed
	 * @return A list of candidates, representing possible connections of bus stop features with lifts and slopes is returned
	 */
	protected List<Candidate> getBusCandidateLinks() {

		//method parameters declaration
		String de_name, id_feature = null, id_busStop = null;
		
		Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Features_in size is: " + this.getFeatures_in().length);
		//print metaData info
		System.out.println("max allowed distance is: " + getMax_threshold());
		System.out.println("max allowed height diffrenece for is: " + StartConfiguration.getInstance().getBus_heights()[0]);
			
		//List of candidate Links to return
		List<Candidate> candidates = new ArrayList<Candidate>();
		Candidate cand;
		
		//create bus-stop connections with lifts and slopes
		for (SimpleFeature busStop : this.busStops) {
//if (!busStop.getAttribute("DE_GR_L_1").toString().contains("Sportgastein")) continue;	
			Coordinate busStopCoord = ((Geometry) busStop.getDefaultGeometry()).getCoordinate();
			
			//disregard bus stop if no elevation info present (z value)
			if(busStopCoord.z == 0.0) {continue;}
			
			de_name = busStop.getAttribute("DE_GR_L_0") + " - " + busStop.getAttribute("DE_NAME");
			id_busStop = busStop.getAttribute("PT_ID").toString();

			//iterate over lifts and fetch bus-lifts candidates. Bus stops connect both to lower and upper lift points		
			for (SimpleFeature lift : this.lifts) {		
				//fetch geometry, coordinate sequence and create start and end point
				Coordinate [] lift_endpoints = GeometryOperations.getOrderedEndPoints(lift);
				id_feature = lift.getAttribute("XML_GID").toString();
				//search for connection to lower lift point. Traverse direction from bus stop to lift
				if (busStopCoord.distance(lift_endpoints[0]) < getMax_threshold()) {
					cand = new Candidate(busStopCoord, lift_endpoints[0], "BusLink", id_busStop, id_feature, de_name);
					candidates.add(cand);
				}//search for connection to upper lift point. Traverse direction from lift to bus stop 
				else if (busStopCoord.distance(lift_endpoints[1]) < getMax_threshold()) {
					cand = new Candidate(lift_endpoints[1], busStopCoord, "BusLink", id_feature, id_busStop, de_name);
					candidates.add(cand);
				}
				
			}
			//iterate over slopes and fetch bus-slopes candidates. Bus stops connect to both to lower and upper slope points
			for (SimpleFeature slope : this.getFeat_match()) {
				//fetch geometry, coordinate sequence and create start and end point
				Coordinate [] slope_endpoints = GeometryOperations.getOrderedEndPoints(slope);
				id_feature = slope.getAttribute("XML_GID").toString();
				//search for connection to lower slope point. Traverse direction from slope to bus stop
				if (busStopCoord.distance(slope_endpoints[0]) < getMax_threshold()) {
					cand = new Candidate(slope_endpoints[0], busStopCoord, "BusLink", id_feature, id_busStop, de_name);
					candidates.add(cand);
				} //search for connection to upper slope point Traverse direction from bus stop to slope 
				else if (busStopCoord.distance(slope_endpoints[1]) < getMax_threshold()) {
					cand = new Candidate(busStopCoord, slope_endpoints[1], "BusLink", id_busStop, id_feature, de_name);
					candidates.add(cand);
				}
				
			}
			//bus stops slope mid-point links?
		}
		//Clean duplicate candidates.
		cleanDuplicates(candidates);
		//********** METHOD END ***************
		return candidates;
	}
	
	/**
	 * This method simplifies the bus line features. It projects bus stops on bus lines and creates a simplified and bus segment 
	 * for each bus line segment from one bus stop to the next. The simplified feature expresses the topology layer and is used
	 * for the graph construction. The bus segment feature represents a segment of the original bus line, but split into segments
	 * at bus stops.  
	 * Attributes simplified: ?
	 * Attributes segments: new length as segment length, new r_id as segment id identical with simplified id, plus all semantic attributes from original feature
	 * @return A SimpleFeatureCollection containing the simplified bus line features
	 */
	public SimpleFeatureCollection simplify() { 
		
		List<SimpleFeature> simpleBuses = new ArrayList<SimpleFeature>();
		List<SimpleFeature> segmentsBuses = new ArrayList<SimpleFeature>();
	   	
		//list to hold the assigned rids
	   	List<Integer> assigned = new ArrayList<Integer>();
	   	
		//create spatial index containing all bus stop points (coordinates)
		final KdTree index = new KdTree();
		for (SimpleFeature stop: this.busStops) {
			index.insert(((Geometry)stop.getDefaultGeometry()).getCoordinate(), stop);
		}
		if (index.isEmpty()) {
			System.err.println("kdTree is Emtpy!!!");
		}
		
		//iterate through bus lines
		for (SimpleFeature busLine : this.getFeatures_in()) {
//if (!busLine.getAttribute("DE_GR_L_1").toString().contains("Gasteinertal")) continue;
			//get its envelope and expand by 5 meters
			Envelope search = new Envelope(((Geometry)busLine.getDefaultGeometry()).getEnvelopeInternal());
			search.expandBy(5.00);
			//query spatial index for bus stops in the envelope
			List<?> neighbourStops = index.query(search);
						
			//fetch bus line vertices as LengthIndexedLine
			Geometry lineGeom = (Geometry)busLine.getDefaultGeometry();
			LengthIndexedLine line = new LengthIndexedLine(lineGeom);
			
			//map of vertex index on lengthLine and corresponding bus stop coordinate
			Map<Double, Coordinate> newBusLineVertices = new TreeMap<Double,Coordinate>();
			boolean hasStart = false, hasEnd = false;
			for (Object stop: neighbourStops) {
				Coordinate stopCoord = ((KdNode) stop).getCoordinate();
				//filter out bus stops that are farther than 50 meter distance from bus line
				if (DistanceOp.isWithinDistance(lineGeom,  FeatureMatching.geomOps.coordinateToPointGeometry(stopCoord), 50.00)) {
					
//System.out.println("\tFound! " + ((SimpleFeature)((KdNode )stop).getData()).getAttribute("PT_ID"));
					
					//fetch index of closest point of bus lineString to bus stop and add both to vertex map
					double indexOfClosestOnLine = line.project(stopCoord);
					newBusLineVertices.put(indexOfClosestOnLine, stopCoord);				
					
					//check if fetched index is close to start or end point
					if ( (int)indexOfClosestOnLine == (int)line.getStartIndex() ) {
						hasStart = true;
					} else if ((int)indexOfClosestOnLine == (int)line.getEndIndex()) {
						hasEnd = true;
					}
				}
			}
			//add start and/or end vertex if neighbor not present
			if (!hasStart) {
				newBusLineVertices.put(line.getStartIndex(), line.extractPoint(line.getStartIndex()));
			}  
			if (!hasEnd) {
				newBusLineVertices.put(line.getEndIndex(), line.extractPoint(line.getEndIndex()));
			}
			//new coordinate list for the simplified feature to be created
			List<Coordinate> simplifiedCoords = new ArrayList<Coordinate>();  
			for (Entry<Double, Coordinate> entry: newBusLineVertices.entrySet()){
				simplifiedCoords.add(entry.getValue());
			}
			
			//use the simplified coordinates to split original bus geometry to its bus segments at topologic nodes
			//geometry must be preserved
			//semantic attributes must be preserved
			// r_id attribute must be added
			ArrayList<SimpleFeature> splitSegments = FeatureMatching.featOps.splitFeatureAtCoordinates(busLine, simplifiedCoords);

			//use the simplified coordinates to create new simplified feature and add to feature collection
			SimpleFeatureType simplifiedType = FeatureOperations.makeLineStringFeatureType(this.getFeatures_in()[0].getFeatureType().getCoordinateReferenceSystem(), "merged_pivots");
			SimpleFeature simplifiedBus = FeatureMatching.featOps.getFeatureFromCoordinates(simplifiedType, simplifiedCoords.toArray(new Coordinate[0]));
		   	String deName = busLine.getAttribute("DE_GR_L_0").toString() + " " + busLine.getAttribute("DE_GR_L_1").toString();
		   	//split feature at new vertices to fetch simplified segments
		   	ArrayList<SimpleFeature> simpleSegments = FeatureMatching.featOps.splitFeatureAtVertices(simplifiedBus);

//  System.out.println(splitSegments.size() +  " / " + simpleSegments.size());		
		   	
		   	//used to build segment r_ids
		   	int segmentNumber = 1;
		   			   			
		   	for (SimpleFeature simpleSegment : simpleSegments) {
		   		Geometry segment_geom = (Geometry) simpleSegment.getDefaultGeometry();
		   		Coordinate[] segment_coords = segment_geom.getCoordinates(); 
		   		//indexedLineGeom to getLength
		   		double length = line.extractLine(line.indexOf(segment_coords[0]), line.indexOf(segment_coords[segment_coords.length-1])).getLength();		    
		   		
		   		//build unique r_id
		   		int rid = Integer.parseInt("3"+ digitRectifier(Integer.parseInt(busLine.getAttribute("DB_ID").toString()),5) + digitRectifier(segmentNumber, 3));
		   		while (assigned.contains(rid)) {
		   			rid++;
		   			segmentNumber++;
		   		}
		   		assigned.add(rid);
		   		segmentNumber++;
		   		//pass computed r_id to the corresponded split segment
		   		splitSegments.get(simpleSegments.indexOf(simpleSegment)).setAttribute("r_id", rid);
		   		
		   	
			   	Map <String, Object> attrs = new LinkedHashMap<String, Object>();
			   	attrs.put("XML_TYPE", busLine.getAttribute("XML_TYPE"));
			   	attrs.put("de_name", deName);
			   	attrs.put("difficulty", (int) 0);
			   	attrs.put("source", String.valueOf(0));
			   	attrs.put("target", String.valueOf(0));
			   	attrs.put("duration", Double.parseDouble("0")); //duration 0 for slopes
			   	attrs.put("length", length); 
			   	attrs.put("r_length", length); 
			   	attrs.put("r_rev_c", length * 50);
			   	attrs.put("rev_c", length * 50);
			   	attrs.put("cost_1", 20 * length);
			   	attrs.put("cost_2", 20 * length);
			   	attrs.put("cost_3", 20 * length);
			   	attrs.put("r_cost_1", 20 * length);
			   	attrs.put("r_cost_2", 20 * length);
			   	attrs.put("r_cost_3", 20 * length);			   	
			   	attrs.put("open", (int) 0);
			   	attrs.put("start_z", segment_coords[0].z/10);
			   	attrs.put("end_z", segment_coords[segment_coords.length-1].z/10);
			   	attrs.put("r_id", rid);
				//add attribute
				for (Map.Entry<String, Object> attribute : attrs.entrySet()){
					simpleSegment = FeatureOperations.addAttribute(simpleSegment, attribute.getKey(), attribute.getValue());
					simpleSegment.setAttribute(attribute.getKey(), attribute.getValue());
				}
			   	
				simpleBuses.add(simpleSegment);
		   	}
		   	
		   	segmentsBuses.addAll(splitSegments);
		}
		
		//create segments_buses shapefile
		SimpleFeatureCollection newBusesSegments = new ListFeatureCollection(segmentsBuses.get(0).getFeatureType(), segmentsBuses);
		FileOperations.createShapeFile(newBusesSegments, StartConfiguration.getInstance().getFolder_out() + "segments_buses.shp");	

		SimpleFeatureCollection simplifiedBuses = new ListFeatureCollection(simpleBuses.get(0).getFeatureType(), simpleBuses);
		return simplifiedBuses;
	}
}
