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
import com.vividsolutions.jts.index.SpatialIndex;
import com.vividsolutions.jts.index.kdtree.KdNode;
import com.vividsolutions.jts.index.kdtree.KdTree;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.linearref.LengthIndexedLine;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import preprocessing.StartConfiguration;
import preprocessing.connectionsAPI.Candidate;
import preprocessing.util.FeatureOperations;
import preprocessing.util.GeometryOperations;
import preprocessing.util.FileOperations;

/**
 * @author Thomas Kouseras
 * A class which specializes the FeatureMatching API for Slope to Slope and Slope to Lift Feature Matching
 */
public class SlopeLinkMatching extends FeatureMatching {
	
	private SimpleFeatureCollection intersections = null;
	private SimpleFeatureCollection slopeLinks;
	private double slopeThreshold;				//max distance tolerance to fetch slope end-point candidate links (SLOPES_ENDPOINT value in the config file)
	private double midPointThreshold;          //max distance tolerance to fetch slope mid-point candidate links (SLOPES_MIDPOINT 2nd value in the config file)

	public SlopeLinkMatching(SimpleFeature[] slopes, SimpleFeature[] lifts) {
		super(slopes, lifts);
		
		this.slopeThreshold = StartConfiguration.getInstance().getSlope_endpoint_dist() + 0.5; 
		this.midPointThreshold = StartConfiguration.getInstance().getSlope_midpoint_dist()[1] + 0.5;
		this.init();
	}
	
	@Override
	public void init() {
		//helper variables to measure method execution time
		long startTime = System.currentTimeMillis(), endTime, elapsed;
		//set id_prefix, used to build an r_id
		this.id_prefix = "1";
		System.out.println("------------------------\n");
		
		//--------- PROCESS SLOPES AND LINFS -----------
		System.out.println("Processing Slopes and Lifts ... ... ");
		matchingPath = "slopeToLiftLinks";
		Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Processing Slopes and Lifts");
		
		//set max distance threshold to fetch lift to slope candidates
		this.max_threshold = (double) StartConfiguration.getInstance().getSlope_distances()[0] + 0.5; //max distance tolerance to fetch slope-lift candidate links (SLOPES_DIST first value in the config file)
		
		//get slopeLiftCandidates and clean duplicates
		List<Candidate> slopeLiftCands = this.getSlopeLiftCandidates();

		cleanRedundant(slopeLiftCands);
		endTime = System.currentTimeMillis();
		elapsed = endTime - startTime;
		
		this.printAndFinalizeLinks(slopeLiftCands, elapsed);
		//-------------------------------------------
		System.out.print("\n");
		//--------- PROCESS SLOPES -----------
		System.out.println("Processing Slopes ... ... ... ... ... ... ... ");
		this.matchingPath = "slopeLinks";
		Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Proccessing Slopes");
				
		//get slopeCandidates and check for duplicates (cleanDuplicates)
		List<Candidate> slopeCandidates = this.getSlopeCandidates();
		endTime = System.currentTimeMillis();
		elapsed = endTime - startTime;
		
		//deviation to create intersections list and shapeFile
		this.intersections = featOps.getIntersectionsFeatureCollection(slopeCandidates, this.getFeatures_in()[0].getFeatureType().getCoordinateReferenceSystem(), "intersections");
		FileOperations.createShapeFile(intersections, StartConfiguration.getInstance().getFolder_out() + "\\candidates\\" + "slope_intersections.shp");
		
		this.printAndFinalizeLinks(slopeCandidates, elapsed);
		//------------------------------------
	}
	
	/**
	 * This method's goal to yield point to point slope to lift candidates and then check for lift to slope mid-point candidates.
	 * Lift to slope mid-point connection can exist to link a lift with a neighbor slope, provided there isn't an end-point to end-point one
	 * The Semantic-Topological condition for a slope-to-lift link is that user either heads from a lift end point to a slope start point or 
	 * from a slope end point to a lift start point. 
	 * Ergo: the slope-to-lift start point to end point relation is either upper to upper or lower to lower
	 * The geometric conditions for the point to point connection is that a slope is at farthest at max slope distance threshold from a lift and the maximum height_diff < height_difs[1]  
	 * @param simplifiedSlopes the list of the new single lineString slope SimpleFeature
	 * @return
	 */
	protected List<Candidate> getSlopeLiftCandidates() {
		String de_name, xml_gid_slope = null, xml_gid_lift = null;
		double liftMidPointThreshold = StartConfiguration.getInstance().getSlope_midpoint_dist()[0] + 0.5;
		
		Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Features_in size is: " + this.getFeatures_in().length);
		//print metaData info
		System.out.println("max allowed end-point distance is: " + getMax_threshold());
		System.out.println("max allowed mid-point distance is: " + liftMidPointThreshold);
		System.out.println("max allowed height difference for slope-lift Links is: " + (StartConfiguration.getInstance().getSlope_heights()[0] + 0.5));
		
		
		//List of out_features (lifts in this case)
		List<SimpleFeature> lifts = this.getFeat_match();
		List<Candidate> candidates = new ArrayList<Candidate>();
		Candidate cand = null;
		
		//topological check at top level. lower points connect with each other and upper points as well. A Slope can connect to a Lift at both ends
		/* for each Lift check upper - upper and lower - lower slope neighbors. For each Lift end-point check also possible slope mid point neighbors
		 * Traverse direction is:
		 * Upper-Upper: from lift to slope
		 * Lower-Lower: from slope to lift */
		for (SimpleFeature lift: lifts) {
			
//if (!lift.getAttribute("DE_GR_L_1").equals("Sportgastein")) continue;
			//fetch coordinate sequence and lower and upper point coords
			Coordinate [] lift_endpoints = GeometryOperations.getOrderedEndPoints(lift);		
			boolean hasLowerConnection = false, hasUpperConnection = false;
			
			//iterate over the slopes to be matched
			for (SimpleFeature slope: this.getFeatures_in()) {
				//fetch semantic info
				de_name = slope.getAttribute("DE_GR_L_0") + " - " + slope.getAttribute("DE_GR_L_1");
				xml_gid_slope = slope.getAttribute("XML_GID").toString();
				xml_gid_lift = lift.getAttribute("XML_GID").toString();
				
				//fetch slope end-points and geometry
				Coordinate [] slope_endpoints = GeometryOperations.getOrderedEndPoints(slope);
				Geometry slopeGeom = (Geometry) slope.getDefaultGeometry();
								
				//Case A - LOWER POINTS LINK: Checking lift lower point for neighbors.Check if lower_in matches lower_out
				if (slope_endpoints[0].distance(lift_endpoints[0]) < getMax_threshold()) {
					//candidate found for lower to lower case, traverse direction from slope to lift
					cand = new Candidate(slope_endpoints[0], lift_endpoints[0], "SlopeLift", xml_gid_slope, xml_gid_lift, de_name);
					candidates.add(cand);
					hasLowerConnection = true;
				} else {
					Coordinate closestPoint = getSlopeMidpointNeighborToLift(lift_endpoints[0], slopeGeom, slope_endpoints, liftMidPointThreshold);
					if (closestPoint != null) {
						//candidate found. Since connection is to lift lower point traverse direction is from slope (start) to lift (end)
						cand = new Candidate(closestPoint, lift_endpoints[0], "SlopeLift", xml_gid_slope, xml_gid_lift, de_name);
						candidates.add(cand);
						hasLowerConnection = true;
					}
				}
				
				//Case B - UPPER POINTS LINK: Checking slope upper point for neighbors. Check if upper_in matches upper_out 
				if (slope_endpoints[1].distance(lift_endpoints[1]) < getMax_threshold()){
					//candidate found for upper to upper case , traverse direction from lift to slope
					cand = new Candidate(lift_endpoints[1], slope_endpoints[1], "SlopeLift", xml_gid_lift, xml_gid_slope, de_name);
					candidates.add(cand);
					hasUpperConnection = true;
				} else {
					Coordinate closestPoint = getSlopeMidpointNeighborToLift(lift_endpoints[1], slopeGeom, slope_endpoints, liftMidPointThreshold);
					if (closestPoint != null) {
						//candidate found. Since connection is to lift upper point, traverse direction is from lift (start) to slope (end)
						cand = new Candidate(lift_endpoints[1], closestPoint, "SlopeLift", xml_gid_lift, xml_gid_slope, de_name);
						candidates.add(cand);
						hasUpperConnection = true;
					}
				}
				//reset candidate variable
				cand = null;
			}
			//Output unconnected links
			if (!hasUpperConnection) System.err.println("Lift " + lift.getAttribute("XML_GID").toString() + " found with no upper links");
			if (!hasLowerConnection) System.err.println("Lift " + lift.getAttribute("XML_GID").toString() + " found with no lower links");
		}
		//Clean duplicate candidates.
		cleanDuplicates(candidates);
		//***** METHOD END ********************		
		return candidates;
	}
	
	
	/**
	 * This method yields slope candidate links and intersections (used for slope simplifying)
	 * 	Each slope connects with another either by three different means:
	 *  A) Common end-point, either upper-lower or lower-upper
	 *  B) Common point, intersection node
	 *  C) Mid-point to end-point connection, if the geometric criteria are satisfied
	 *  D) End-point connection, if the geometric criteria are satisfied
	 * 
	 * The hierarchy of connections when researching a slope's end-points is as follows:
	 *  1st - common end-point
	 *  2nd - intersection node
	 *  3rd - mid-point connection
	 *  4th - end-point connection
	 *  
	 * In practice for each slope we follow the below work flow:
	 *  *For upper point:
	 *  	- check if there's a common endPoint with slope_out (if yes, add endPoint Node as dummy candidate, and proceed to lower point)
	 *  	- if not -> fetch the intersection points of the slopes. If there is an intersection point in a 20m distance from current endPoint 
	 *  		keep intersection point and proceed to lower point
	 *  	- if not -> check if there is a midPoint-to-endPoint connection. If yes add mid-point candidate and proceed to lower point
	 *  	- if not -> check if there is an endPoint connection. If yes add end-point candidate and proceed to lower point
	 *  *For lower point follow the exact same work flow
	 *  
	 *  Intersection points are going be reduced and used to simplify the slopes
	 * @return
	 */
	protected List<Candidate> getSlopeCandidates() {
		//method parameter declaration
		String de_name, xml_gid_in = null, xml_gid_out = null;
		
		//metaData logging
		Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Features_in size is: " + this.getFeatures_in().length);
		System.out.println("max allowed end-point distance is: " + this.slopeThreshold);
		System.out.println("max allowed mid-point distance is: " + midPointThreshold);
		System.out.println("max allowed height difference for slope Links is: " + (StartConfiguration.getInstance().getSlope_heights()[5] + 0.5));
		List<SimpleFeature> slopes_out = new ArrayList<SimpleFeature>(Arrays.asList(this.getFeatures_in()));
		List<Candidate> candidates = new ArrayList<Candidate>();
		Candidate cand = null;
				
		for (SimpleFeature slope_in: this.getFeatures_in()) {
			//remove first item of the out_feature list, to avoid checking a feature over itself
			slopes_out.remove(0);
			
//if (!slope_in.getAttribute("DE_GR_L_1").equals("Sportgastein")) continue;

			//fetch geometry, coordinate sequence and create start and end point
			Geometry geom_in = (Geometry) slope_in.getDefaultGeometry();
			Coordinate [] slope_in_endPoints = GeometryOperations.getOrderedEndPoints(slope_in);
			
			//iterate over the out set of features
			for (SimpleFeature slope_out: slopes_out) {		
				//fetch semantic info
				de_name = slope_in.getAttribute("DE_GR_L_0") + " - " + slope_in.getAttribute("DE_GR_L_1");
				xml_gid_in = slope_in.getAttribute("XML_GID").toString();
				xml_gid_out = slope_out.getAttribute("XML_GID").toString();
				
				String[] attributes = new String [] {xml_gid_in, xml_gid_out, de_name};
				
				//fetch geometry and slope_out end-points
				Coordinate [] slope_out_endPoints = GeometryOperations.getOrderedEndPoints(slope_out);
				Geometry geom_out = (Geometry) slope_out.getDefaultGeometry();
				
				/* fetch intersection points of two geometries. size null -> means no intersection. size >=1 -> means slope intersect at one or more points */
				List<Coordinate> intersectionPoints = geomOps.getIntersectionVertices(geom_in, geom_out);

				//Remove common upper or lower point, if there is one, from intersection list
				cleanIntersectionsFromCommonEndPoints(slope_in_endPoints, slope_out_endPoints, intersectionPoints);					
				
				/* 1 -  search slope_in upper for connection with slope_out lower or mid-point (distance < 20m) (traverse direction from slope_out to slope_in)  */
				Coordinate midPointNeighbor = getMidPointCandidate(slope_in_endPoints[1], geom_out, midPointThreshold); //fetch midPoint neighbor if one exists, for upper endPoint
				cand = fetchEndpointCandidate(slope_in_endPoints[1], slope_out_endPoints[0], intersectionPoints, midPointNeighbor, attributes, true);
				if (cand!=null) { candidates.add(cand); }
				
				/* 2 -  search slope_in lower for connection with slope_out upper or mid-point (distance < 20m) (traverse direction from slope_in to slope_out)  */
				midPointNeighbor = getMidPointCandidate(slope_in_endPoints[0], geom_out, this.midPointThreshold);	//fetch midPoint neighbor if one exists, for lower endPoint
				cand = fetchEndpointCandidate(slope_in_endPoints[0], slope_out_endPoints[1], intersectionPoints, midPointNeighbor, attributes, false);
				if (cand!=null) { candidates.add(cand); }
				
				/* 3 - search slope_out upper for connection with slope_in lower or mid-point (distance < 20m) (traverse direction from slope_in to slope_out)*/
				midPointNeighbor = getMidPointCandidate(slope_out_endPoints[1], geom_in, midPointThreshold); //fetch midPoint neighbor if one exists, for upper slope_out endPoint
				cand = fetchEndpointCandidate(slope_out_endPoints[1], slope_in_endPoints[0], intersectionPoints, midPointNeighbor, new String [] { xml_gid_out, xml_gid_in, de_name}, true);
				if (cand!=null) { candidates.add(cand); }
				
				/* 4 -  search slope_out lower for connection with slope_in upper or mid-point (distance < 20m) (traverse direction from slope_out to slope_in)  */
				midPointNeighbor = getMidPointCandidate(slope_out_endPoints[0], geom_in, this.midPointThreshold);	//fetch midPoint neighbor if one exists, for lower endPoint
				cand = fetchEndpointCandidate(slope_out_endPoints[0], slope_in_endPoints[1], intersectionPoints, midPointNeighbor, new String [] { xml_gid_out, xml_gid_in, de_name}, false);
				if (cand!=null) { candidates.add(cand);	}
				
				/* 5 - No candidate links are found and slopes DO NOT INTERSECT, search for mid-point links*/
				if (candidates.size() == 0 && intersectionPoints == null) {
					//Fetch closest point between two geometries, that is within allowed threshold (10.50)
					if (DistanceOp.isWithinDistance(geom_in, geom_out, midPointThreshold)) {
						//fetch nearest points
						Coordinate[] nearestPoints = DistanceOp.nearestPoints(geom_in, geom_out);
						Coordinate nearestGeom_in = nearestPoints[0];
						Coordinate nearestGeom_out = nearestPoints[1];
						//fetch interpolated Z ordinate in case it is NaN
						nearestGeom_in = GeometryOperations.get3DLinePoint(nearestGeom_in, geom_in);
						nearestGeom_out = GeometryOperations.get3DLinePoint(nearestGeom_out, geom_out);
						//create and add mid-point candidate. Traverse direction has to be from highest to lowest point
						if (nearestGeom_in.z > nearestGeom_out.z) {
							cand = new Candidate(nearestGeom_in, nearestGeom_out, "Slope2Slope", xml_gid_in, xml_gid_out, de_name);
							candidates.add(cand);
						} else {
							cand = new Candidate(nearestGeom_out, nearestGeom_in, "Slope2Slope", xml_gid_out, xml_gid_in, de_name);
							candidates.add(cand);
						}
					}	
				}
				
				//FINALLY add intersection points
				if (intersectionPoints != null && intersectionPoints.size() > 0) {
					//System.err.println("Still " + intersectionPoints.size() + " to add");
					for (Coordinate intersection: intersectionPoints) {
						cand = new Candidate(intersection, intersection, "Intersection", xml_gid_in, xml_gid_out, de_name);
						candidates.add(cand);
					}
				}
				
				cand = null; //reset candidate
			} 
		}

		//Clean duplicate candidates in case some exist, candidates with different start and end features are needed for the final slope nodding
		cleanDuplicates(candidates);
			
		//********* METHOD END ****************
		return candidates;
	}
	
	/**
	 * This method checks whether two slopes have common start or end-point and removes it from their intersection list
	 * @param endPointsA - Is the end-points of slope_in as Coordinates
	 * @param endPointsB - Is the end-points of slope_out as Coordinates
	 * @param intersectionPoints - the list of intersection points, in Coordinate representation, without the common start or end-point
	 */
	private void cleanIntersectionsFromCommonEndPoints (Coordinate[] endPointsA, Coordinate[] endPointsB, List<Coordinate> intersectionPoints) {
		if (intersectionPoints != null && intersectionPoints.size()>0) {
			for (int i = 0; i<endPointsA.length; i++) {
				if (endPointsA[i].equals2D(endPointsB[i])) {
					if (intersectionPoints.contains(endPointsA[i])) {
						intersectionPoints.remove(intersectionPoints.indexOf(endPointsA[i]));
					} 
				} 
			}
		} 
	}
	
	/**
	 * This method returns the closest slope intersection to the passed slope endPoint
	 * @param endPoint - is the coordinate to which we want to fetch the closest intersection
	 * @param intersectionPoints - is the list of intersection of the feature to whom the endPoint belongs
	 * @return Coordinate
	 */
	private Coordinate fetchClosestIntersection (Coordinate endPoint, List<Coordinate> intersectionPoints) {
		if (intersectionPoints!=null ) {
			if (intersectionPoints.size()>1) {
				return GeometryOperations.getNearestVertex(endPoint, intersectionPoints.toArray(new Coordinate[0]));
			}
			 if (intersectionPoints.size()==1) {
					return intersectionPoints.get(0);
			 }
		}  
		return null;
	}
	
	/**
	 * This method represents end-point slope matching. It matches two slope endPoints, if they are a lower-upper pair, 
	 * and returns the candidate link matched from this pair. In case there is a intersection near the slope_in end-point (including common end-point), 
	 * intersection is kept and candidate is discarded. In case there is a mid-point connection to slope_out from the slope_in end-point,
	 * end-to-end candidate is discarded and mid-point candidate is kept. If no end-to-end candidate is found, an end-to-midpoint connection is investigated
	 * and a mid-point candidate is returned. If none of the above null is returned
	 * @param endPointA - coordinate representing a slope's end-point (usually slope_in, except the cross-exam case where is slope_out)
	 * @param endPointB - coordinate representing a slope's end-point (usually slope_in, except the cross-exam case where is slope_out)
	 * @param intersectionPoints - list<coordinate> representing intersections between two slopes
	 * @param midPointNeighbor - mid-point neighbor vertex to end-point A or null if none is existent
	 * @param candAttributes - semantic attributes needed for the creation of a Candidate object 
	 * @param upperEndpoint - flag that designates whether an upper or a lower end-point is being investigated
	 * @return
	 */
	private Candidate fetchEndpointCandidate(Coordinate endPointA, Coordinate endPointB, List<Coordinate> intersectionPoints, 
			Coordinate midPointNeighbor, String[] candAttributes, boolean upperEndpoint) {
		
		Coordinate [] candPointOrder = new Coordinate[2];
		String type = null;
		String [] attributes = candAttributes; 
		Coordinate closestIntersection = fetchClosestIntersection(endPointA, intersectionPoints); //fetch closest intersection point to endPoint	
		//UPPER ENDPOINT LINK (distance < 20m) 
		if (endPointA.distance(endPointB) < this.slopeThreshold) {
			//check for common endPoint (case upper slope_in to be common with lower slope_out)
			if (commonEndPoint(endPointA, endPointB)){
				candPointOrder[0] = endPointB;
				candPointOrder[1] = endPointA;
				type = "Endpoint-Intersection";
				removePointFromIntersections(intersectionPoints, endPointA);
			}
			//check if closest intersection point is within a 20m distance from upper end point (if there's no common endPoint) 
			else if (closestIntersection!=null && closestIntersection.distance(endPointA) < this.slopeThreshold) {
				candPointOrder[0] = closestIntersection;
				candPointOrder[1] = closestIntersection;
				type = "Intersection";
				intersectionPoints.remove(intersectionPoints.indexOf(closestIntersection));
			}
			//check for mid-point connection with a 10m distance at 3rd Level (if there's no intersection)s
			else if (midPointNeighbor!=null) {
				candPointOrder[0] = midPointNeighbor;
				candPointOrder[1] = endPointA;
				type = "Slope2Slope";
			}
			//else create endPoint candidate at 4rth Level
			else {
				candPointOrder[0] = endPointB;
				candPointOrder[1] = endPointA;
				type = "Slope2Slope";
			}
		}
		//else if no end-point to end-point connection found investigate from end-point to midpoint connection
		else if (hasMidpointConnection(midPointNeighbor, endPointA, closestIntersection)) {
			candPointOrder[0] = midPointNeighbor;
			candPointOrder[1] = endPointA;
			type = "Slope2Slope";
		}
		
		if (type!=null) {
			//return upperEndpoint Candidate (traverse direction from slope_out to slope_in)
			if (upperEndpoint) {
				return new Candidate(candPointOrder[0], candPointOrder[1], type, attributes[1], attributes[0], attributes[2]);
			}
			//return lowerEndpoint Candidate (traverse direction from slope_in to slope_out)
			else {
				return new Candidate( candPointOrder[1], candPointOrder[0], type, attributes[0], attributes[1], attributes[2]);
			}
		}
		return null;		
	}

		
	/**
	 * This function returns true if endPoints are equal
	 * @param endPoint_in - slope_in endPoint
	 * @param endPoint_out - slope_out endPoint
	 * @return
	 */
	private boolean commonEndPoint (Coordinate endPoint_in, Coordinate endPoint_out) {
		if (endPoint_in.equals3D(endPoint_out)) {
			return true;
		} else return false;	
	}
	
	
	/**
	 * This method returns true if a slope's endPoint has a midPoint neighbor to another slope,
	 * while there is not intersection point between those slope within 20 meters from the endPoint
	 * under investigation
	 * @param midpointNeighbor - midPoint neighbor to the slope's endPoint
	 * @param endPoint - endPoint of a slope
	 * @param closestIntersection - intersection point, of the two slopes being investigated, to the current endPoint 
	 * @return
	 */
	private boolean hasMidpointConnection (Coordinate midpointNeighbor, Coordinate endPoint, Coordinate closestIntersection) {
		if (midpointNeighbor!=null && (closestIntersection == null || (!(closestIntersection.distance(endPoint) < this.slopeThreshold)))) {
			return true;
		}
		return false;
	}
	
	/**
	 * This method returns a slope's mid-point as neighbor to a lift's end-point
	 * @param liftEndpoint
	 * @param slopeGeom
	 * @param threshold - distance threshold which determines whether a slope midpoint is a neighbor or not
	 * @return
	 */
	private Coordinate getSlopeMidpointNeighborToLift(Coordinate liftEndpoint, Geometry slopeGeom, Coordinate[] slope_endpoints, double threshold) {
		if (DistanceOp.isWithinDistance(geomOps.coordinateToPointGeometry(liftEndpoint), slopeGeom, threshold)) {
			//create LengthIndexedLine
			LengthIndexedLine indexedSlope = new LengthIndexedLine(slopeGeom);
			//project lift end-point on indexed line
			double indexClosest = indexedSlope.project(liftEndpoint);
			//fetch closest point on slope
			Coordinate closestPoint = indexedSlope.extractPoint(indexClosest);
			//check if closest point is not within 100m from either slope end-points and create candidate (StartConfiguration.slope_distances could be used instead to pass soft coded threshold value)
			if (closestPoint.distance(slope_endpoints[0]) > Double.parseDouble("100.0") && closestPoint.distance(slope_endpoints[1]) > Double.parseDouble("100.0")){ 
				return closestPoint;
			}
		}
		return null;
	}
	
	
	/**
	 * This method removes a point, if included, from the list of intersection
	 * @param intersectionPoints - list of a slopes intersections
	 * @param toRemove - point to remove from intersection list
	 */
	private void removePointFromIntersections(List<Coordinate> intersectionPoints, Coordinate toRemove) {
		if (intersectionPoints != null) {
			if (intersectionPoints.size() == 1) {
				if (intersectionPoints.get(0).equals2D(toRemove)) {
					intersectionPoints.remove(0);
				}
			} else {
				if ( intersectionPoints.contains(toRemove)) {
					int removeIndex = intersectionPoints.indexOf(toRemove);
					intersectionPoints.remove(removeIndex);
				}
			}
		}	
	}
	
	
	/**
	 * This method simplifies and splits slope features. Each slope keeps its end-points and is split
	 * at the topology vertices, used for the graph creation, either an intersection or a link vertex.
	 * For each slope two types of segments are created: 
	 *  a) split segments, which is the same feature geometry and semantic attributes, but split at the topology vertices
	 *  b) simplified segments. Here original geometry is discarded and only end-points of each segment is kept
	 * @param bus_links - features linking bus lines with slopes
	 * @return A SimpleFeatureCollection containing the simplified slope segments, ready for the feature merge, needed for the graph creation
	 */
	public SimpleFeatureCollection simplify(SimpleFeatureCollection bus_links) {
		
		//lists hold simplified and split slope segments
		List<SimpleFeature> simpleSlopes = new ArrayList<SimpleFeature>();
	    List<SimpleFeature> segmentsSlopes = new ArrayList<SimpleFeature>();
		
		//list to hold the assigned rids of the segments
	   	List<Integer> assigned = new ArrayList<Integer>();
	   	
		//collections to lists
		List<SimpleFeature> intersections = FeatureOperations.featureCollectionToList(this.intersections);
		List<SimpleFeature> slopeLinks = FeatureOperations.featureCollectionToList(this.getSlopeLinks());
		List<SimpleFeature> slopeLiftLinks = FeatureOperations.featureCollectionToList(this.getLinks());
		List<SimpleFeature> busLinks = (bus_links != null) ? FeatureOperations.featureCollectionToList(bus_links) : null;
		
		//INTERSECTIONS USING KDTREE
		//create spatial index to contain all intersections
		final KdTree pointIndex = new KdTree();
		for (SimpleFeature intersection: intersections) {
			pointIndex.insert(((Geometry)intersection.getDefaultGeometry()).getCoordinate(), intersection);
		}
		if (pointIndex.isEmpty()) {
			Logger.getLogger(SlopeLinkMatching.class.getName()).log(Level.WARNING, "kdTree is Emtpy. No Intersections loaded while simplifying slopes");
			Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.WARNING, "kdTree is Emtpy. No Intersections loaded while simplifying slopes");
		}
		
		//SLOPE LINKS, SLOPE LIFT LINKS  AND BUS LINKS USING STRTREE
		final SpatialIndex lineIndex = new STRtree();
		//insert all slope links
		for (SimpleFeature link: slopeLinks) {
			Envelope linkEnvelope = new Envelope(((Geometry) link.getDefaultGeometry()).getEnvelopeInternal());
			lineIndex.insert(linkEnvelope, link);
		}
		//insert all slopeLift links
		for (SimpleFeature link: slopeLiftLinks) {
			Envelope linkEnvelope2 = new Envelope(((Geometry) link.getDefaultGeometry()).getEnvelopeInternal());
			lineIndex.insert(linkEnvelope2, link);
		}
		//insert all bus links
		if (busLinks!=null) {
			for (SimpleFeature link: busLinks) {
				Envelope linkEnvelope3 = new Envelope(((Geometry) link.getDefaultGeometry()).getEnvelopeInternal());
				lineIndex.insert(linkEnvelope3, link);
			}
		}
		
		//KEEP INTERSECTING LINKS FOR EACH SLOPE
		for (SimpleFeature slope_original: this.getFeatures_in()) {
			
//if (!slope_original.getAttribute("DE_GR_L_1").equals("Sportgastein")) continue;

			//fetch slope geometry as LengthIndexedLine
			Geometry slopeGeom = (Geometry) slope_original.getDefaultGeometry();
			LengthIndexedLine lengthLine = new LengthIndexedLine(slopeGeom);
			
			//get envelope and expand by 5.00 meters
			Envelope search = new Envelope(slopeGeom.getEnvelopeInternal()); 
			search.expandBy(5.00);

			//Vertex ordered Map as index on lengthLine and corresponding coordinate
			Map<Double, Coordinate> newVertices = new TreeMap<Double, Coordinate>();
			//insert start and end-point
			newVertices.put(lengthLine.getStartIndex(), lengthLine.extractPoint(lengthLine.getStartIndex()));
			newVertices.put(lengthLine.getEndIndex(), lengthLine.extractPoint(lengthLine.getEndIndex()));

			/*------------------------ INTERSECTIONS ------------------------*/
			//fetch intersections in the slope envelope
			List<?> neighbourIntersections = pointIndex.query(search);
			
/*			System.out.println("For slope " + slope.getAttribute("XML_GID") 
				+ " found following intersections: ");
			for (Object inters: neighbourIntersections) {
				System.out.println("\tintersection: " + ((SimpleFeature)((KdNode )inters).getData()).getAttribute("gid_start") + ", " + ((SimpleFeature)((KdNode )inters).getData()).getAttribute("gid_end"));
			}
			System.out.println("----------------------------------");
*/
			
			//iterate through intersections and keep only those on line (using distance threshold)
			for (Object inters: neighbourIntersections) {
				//get geometry and coordinate
				Geometry geom = (Geometry) ((SimpleFeature)((KdNode )inters).getData()).getDefaultGeometry();
				Coordinate coord = geom.getCoordinate();
				//check if intersection is on line (using distance with threshold 1cm)
				if (slopeGeom.distance(geom) < 0.001) {
					double index = lengthLine.indexOf(coord);
					//add it if not already contained
					if(!newVertices.containsKey(index) && !newVertices.containsValue(coord)) {
						newVertices.put(index, coord);
					}
//					System.out.println("\tintersection: " + ((SimpleFeature)((KdNode )inters).getData()).getAttribute("gid_start") + ", " 
//					+ ((SimpleFeature)((KdNode )inters).getData()).getAttribute("gid_end"));
				} 
			}
			/*-------------------------------------------------------*/
			
			/*------------------------ LINKS ------------------------*/
			//fetch links in the envelope
			@SuppressWarnings("unchecked")
			List<SimpleFeature> neighbourLinks = lineIndex.query(search);

/*			System.out.println("For slope " + slope.getAttribute("XML_GID") 
				+ " found following links: ");
			for (SimpleFeature link: neighbourLinks) {
				System.out.println("\tlink: " + link.getAttribute("gid_start") + ", " + link.getAttribute("gid_end"));
			}
			System.out.println("----------------------------------");
*/
			
			//iterate through links and keep only those on line (using distance threshold)
			for (SimpleFeature link : neighbourLinks) {				
				Geometry linkGeom = (Geometry) link.getDefaultGeometry();
				//filter out links not intersecting with slope
				if(slopeGeom.distance(linkGeom) < 0.05) {
					Coordinate[] linkCoords = linkGeom.getCoordinates();
					//check which link end-point is on line (using distance with threshold 5cm) and add if not already contained
					for (Coordinate linkCoord: linkCoords) {
						if (slopeGeom.distance(FeatureMatching.geomOps.coordinateToPointGeometry(linkCoord)) < 0.05 && !newVertices.containsValue(linkCoord)) {
							newVertices.put(lengthLine.indexOf(linkCoord), linkCoord);
							break;
							//System.out.println("\tlink: " + link.getAttribute("gid_start") + ", " + link.getAttribute("gid_end"));
						}
					}
				}
			}
			/*-------------------------------------------------------*/
			
			//new coordinate list from kept indices, for the creation of the simplified feature
			List<Coordinate> simplifiedCoords = new ArrayList<Coordinate>();
			for (Entry<Double, Coordinate> entry: newVertices.entrySet()){
				if (!simplifiedCoords.contains(entry.getValue()))
				simplifiedCoords.add(entry.getValue());
			}
			
			//use the simplified coordinates to split original bus geometry to its bus segments at topologic nodes
			//geometry must be preserved
			//semantic attributes must be preserved
			// r_id attribute must be added
			ArrayList<SimpleFeature> splitSegments = FeatureMatching.featOps.splitFeatureAtCoordinates(slope_original, simplifiedCoords);
			
			//create new feature and add to feature collection
			SimpleFeatureType simplifiedType = FeatureOperations.makeLineStringFeatureType(this.getFeatures_in()[0].getFeatureType().getCoordinateReferenceSystem(), "merged_pivots");
			SimpleFeature simplifiedSlope = FeatureMatching.featOps.getFeatureFromCoordinates(simplifiedType, simplifiedCoords.toArray(new Coordinate[0]));
		   	String deName = slope_original.getAttribute("DE_GR_L_0").toString() + " " + slope_original.getAttribute("DE_GR_L_1").toString();
		   	//split feature at remaining vertices to line segments (for each vertex pair)
		   	ArrayList<SimpleFeature> simpleSegments = FeatureMatching.featOps.splitFeatureAtVertices(simplifiedSlope);
		   	//rids building
		   	int segmentNumber = 1;
//if(splitSegments.size() != simpleSegments.size()) {		   	
//System.out.println(slope_original.getAttribute("XML_GID").toString() + ": " +splitSegments.size() +  " / " + simpleSegments.size());	
//System.exit(0);
//}			
		   	for (SimpleFeature simpleSegment: simpleSegments) {
		   		Geometry segment_geom = (Geometry) simpleSegment.getDefaultGeometry();
		   		Coordinate[] segment_coords = segment_geom.getCoordinates(); 
		   		//indexedLineGeom to getLength
		   		double length = lengthLine.extractLine(lengthLine.indexOf(segment_coords[0]), lengthLine.indexOf(segment_coords[1])).getLength();
		   		//in case segment has zero length ignore it and proceed to the next
		   		if (length == 0.0) continue;	//{System.err.println(length);}
		   		double cost_1, cost_2, cost_3, r_cost_1, r_cost_2, r_cost_3;
                if (Integer.parseInt(slope_original.getAttribute("difficulty").toString()) == 1) {
                    r_cost_1 = length; r_cost_2 = 10 * length; r_cost_3 = 15 * length;
                    cost_1 = length; cost_2 = 10 * length; cost_3 = 15 * length;
                } else if (Integer.parseInt(slope_original.getAttribute("difficulty").toString()) == 2) {
                    r_cost_1 = 5 * length; r_cost_2 = length; r_cost_3 = 5 * length;
                    cost_1 = 5 * length; cost_2 = length; cost_3 = 5 * length;
                } else if (Integer.parseInt(slope_original.getAttribute("difficulty").toString()) == 3) {
                    r_cost_1 = 15 * length; r_cost_2 = 10 * length; r_cost_3 = length;
                    cost_1 = 15 * length; cost_2 = 10 * length; cost_3 = length;
                } else {
                    r_cost_1 = length; r_cost_2 = length; r_cost_3 = length;
                    cost_1 = length; cost_2 = length; cost_3 = length;
                }
                
		   		//build unique r_id
                int slopeNumber = Integer.parseInt(slope_original.getAttribute("XML_GID").toString());
		   		int rid = Integer.parseInt("1"+ digitRectifier(slopeNumber, 5) + digitRectifier(segmentNumber, 3));
		   		while (assigned.contains(rid)) {
		   			rid++;
		   			segmentNumber++;
		   		}
		   		assigned.add(rid);
		   		segmentNumber++;
		   		//pass computed r_id to the corresponded split segment
		   		splitSegments.get(simpleSegments.indexOf(simpleSegment)).setAttribute("r_id", rid);
		   		splitSegments.get(simpleSegments.indexOf(simpleSegment)).setAttribute("difficulty", Integer.parseInt(slope_original.getAttribute("difficulty").toString()));
                
			   	Map <String, Object> attrs = new LinkedHashMap<String, Object>();
			   	attrs.put("XML_TYPE", "slopes");
			   	attrs.put("de_name", deName);
			   	attrs.put("difficulty", Integer.parseInt(slope_original.getAttribute("difficulty").toString()));
			   	attrs.put("source", String.valueOf(0));
			   	attrs.put("target", String.valueOf(0));
			   	attrs.put("duration", Double.parseDouble("0")); //duration 0 for slopes
			   	attrs.put("length", length); 
			   	attrs.put("r_length", length); 
                attrs.put("r_rev_c", (double) -1);
                attrs.put("rev_c", (double) -1);
			   	attrs.put("cost_1", cost_1);
			   	attrs.put("cost_2", cost_2);
			   	attrs.put("cost_3", cost_3);
			   	attrs.put("r_cost_1", r_cost_1);
			   	attrs.put("r_cost_2", r_cost_2);
			   	attrs.put("r_cost_3", r_cost_3);
			   	attrs.put("open", (int) 0);
			   	attrs.put("start_z", segment_coords[0].z/10);
			   	attrs.put("end_z", segment_coords[segment_coords.length-1].z/10);
				attrs.put("r_id", rid);
				//add attributes
				for (Map.Entry<String, Object> attribute : attrs.entrySet()){
					simpleSegment = FeatureOperations.addAttribute(simpleSegment, attribute.getKey(), attribute.getValue());
					simpleSegment.setAttribute(attribute.getKey(), attribute.getValue());
				}
				simpleSlopes.add(simpleSegment);
		   	}
		   	segmentsSlopes.addAll(splitSegments);

			//CONTROL METHOD PRINTS OUT SLOPE WITH NO CONNECTIONS
/*			if (neighbourLinks.size() == 0 && neighbourIntersections.size() == 0) {
				System.err.println("Slope with 'r_id' " + slope_original.getAttribute("XML_GID") + " was found with no links and no intersections");
			} else if (neighbourLinks.size() == 0) {
				System.err.println("Slope with 'r_id' " + slope_original.getAttribute("XML_GID") + " was found with no links");
			}*/
			//TODO:METHOD END PRINT RESULT TO FILE
			
		}
		
		//create segments_buses shapeFile
		SimpleFeatureCollection newSlopeSegments = new ListFeatureCollection(segmentsSlopes.get(0).getFeatureType(), segmentsSlopes);
		FileOperations.createShapeFile(newSlopeSegments, StartConfiguration.getInstance().getFolder_out() + "segments_slopes.shp");	
		//return feature collection
		SimpleFeatureCollection simplifiedSlopes = new ListFeatureCollection(simpleSlopes.get(0).getFeatureType(), simpleSlopes);
		
		
		return simplifiedSlopes;
	}
	
	
	public SimpleFeatureCollection getSlopeLinks() {
		return slopeLinks;
	}

	
	public void setSlopeLinks(SimpleFeatureCollection slopeLinks) {
		this.slopeLinks = slopeLinks;
	}

}
