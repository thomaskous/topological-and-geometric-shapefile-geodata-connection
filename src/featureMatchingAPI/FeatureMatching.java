package preprocessing.featureMatchingAPI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.linearref.LengthIndexedLine;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import preprocessing.StartConfiguration;
import preprocessing.connectionsAPI.AbstractPointPair;
import preprocessing.connectionsAPI.Candidate;
import preprocessing.connectionsAPI.Link;
import preprocessing.util.FeatureOperations;
import preprocessing.util.GeometryOperations;
import preprocessing.util.FileOperations;

/**
 * 
 * @author Thomas Kouseras
 * A class which represents the FeatureMatching abstract type. Provides a basic API, plus helper methods
 */
public abstract class FeatureMatching {
	static FeatureOperations featOps = new FeatureOperations();
	static GeometryOperations geomOps = new GeometryOperations();
	//list to hold the assigned rids
   	static List<Integer> assigned = new ArrayList<Integer>();
	
	//instance parameters
	private SimpleFeature[] features_in;
	private List<SimpleFeature> feat_match;
	private SimpleFeatureCollection links;
	protected String id_prefix;
	protected double max_threshold;
	protected String matchingPath;
	
	
	public FeatureMatching (SimpleFeature[] shp_in, SimpleFeature[] shp_match) {
		this.features_in = Arrays.copyOf(shp_in, shp_in.length);
		if (shp_match != null) {
			//Matching features as ArrayList, to be able to add and delete checked items
			this.feat_match = new ArrayList<SimpleFeature>(Arrays.asList(shp_match));
		} else {
			this.feat_match = null;
		}
	}
	
	public FeatureMatching (SimpleFeature[] lifts) {
		this(lifts, null);
	}
	
	protected abstract void init();
	
	/**
	 * This method outputs candidates, links and time of execution to terminal and result file and creates links from candidates
	 * @param cands
	 * @param timeOfExecution
	 */
	protected void printAndFinalizeLinks(List<Candidate> cands, long timeOfExecution) {
		//**** PRINT EXECUTION TIME ******************
		Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, matchingPath +"getCandidate method's execution time: " + timeOfExecution + " milliseconds");
		System.out.println(matchingPath + "getCandidate method's execution time: " + timeOfExecution + " msecs");
		
		//***** PRINT CANDIDATE RESULTS*********
		String header = matchingPath + " processing yielded: " + cands.size() + " candidate links";
		try {
			printCandidates(header, cands);
		} catch (IOException e) {
			Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.SEVERE, "Print Candidates Exception: " + e);
			e.printStackTrace();
		}
		
		System.out.println(header);
		
/*		System.out.println("XML_GID_START, XML_GID_END, HEIGHT DIFF, DIST");
		for (Candidate item: cands) {
			//print only not dummy candidates (exclude intersections)
			if (!item.getStartPoint().equals2D(item.getEndPoint())) {
				System.out.printf("%11s  %11s  %9.2f %10.2f \n", item.getXml_gid_start(),item.getXml_gid_end(), item.getHeightDiff(), item.getDistance());
			}
		}*/
		
		//***** CREATE LINKS ******************
		createLinks(cands);
		
		
		//**** CREATE CANIDATE SHAPEFILE *****
		if (StartConfiguration.getInstance().isOutputCandidates() && cands != null) {
			SimpleFeatureCollection candidateCollection = featOps.getFeatureCollectionFromLinks(this.features_in[0].getFeatureType(), new ArrayList<AbstractPointPair>(cands));
			FileOperations.createShapeFile(candidateCollection, StartConfiguration.getInstance().getFolder_out()+"\\candidates\\"+matchingPath+"_candidates.shp");
		}
	}
	

	/**
	 * This method creates the Link objects out of candidates and SimpleFeatureCollection out of them 
	 * which passes to the relevant instance variable
	 * @param candidates
	 */
	private void createLinks (List<Candidate> candidates) {
		//List of links
		List<Link> finalLinks = new ArrayList<Link>();
		//create links from the candidates
		for (Candidate cand: candidates) {
			//create Link
			Link newLink = cand.createLink();
			if (newLink == null || newLink.getGrade() == 'E')  continue; //move to next iteration if createLink returned null or grade evaluated to E
			//finalize attributes and add to List
			newLink.setAttributeValue("gid_start", cand.getXml_gid_start());
			newLink.setAttributeValue("gid_end", cand.getXml_gid_end());
			newLink.setAttributeValue("de_name", cand.getDe_name());
			
			//build r_id and added to Link's attributes
			String r_id = this.id_prefix  + cand.getXml_gid_start();
			newLink.setAttributeValue("r_id", Integer.parseInt(r_id));
			
			finalLinks.add(newLink);
		}
		
		//clear links from null values
		finalLinks.removeAll(Collections.singleton(null));
		
		//***** PRINT LINK RESULTS *********
		String header = matchingPath + " processing yielded: " + finalLinks.size() + " links";
		try {
			printLinks(header, finalLinks);
		} catch (IOException e) {
			Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.SEVERE, "Print Links Exception :" + e);
			e.printStackTrace();
		}
/*		System.out.println(header);
		System.out.println(" R_ID,   XML_GID_START, XML_GID_END, LENGTH, HEIGHT DIFF, RATE");
		for (Link item: finalLinks) {
			System.out.printf("%4s  %9s     %8s      %6.2f  %9.2f    %3s \n", item.getAttributeValue("r_id"),
					item.getAttributeValue("gid_start"), item.getAttributeValue("gid_end"), item.getDistance(), item.getHeightDiff(), item.getGrade());
		}*/
		
		//***** CREATE LINKS FEATURE COLLECTION *********
		if (this.matchingPath.equals("slopeLinks")) {
			((SlopeLinkMatching) this).setSlopeLinks(featOps.getFeatureCollectionFromLinks(this.features_in[0].getFeatureType(), new ArrayList<AbstractPointPair>(finalLinks)));
		} else
		this.links = featOps.getFeatureCollectionFromLinks(this.features_in[0].getFeatureType(), new ArrayList<AbstractPointPair>(finalLinks));
	}
	
	
	/* HELPER METHODS */
	
	/**
	 * This method checks if the given point lies within the given distance from a slope (line) and returns the slopes closest point to the given point 
	 * or null if distance between geometries is bigger than the given
	 * @param point		  Is a coordinate representation of a point (endPoint of a slope)	
	 * @param line		  Is the linear geometry representing a slope
	 * @param distance	  Distance threshold, within which the slope should be from the point to return its closest point
	 * @return Coordinate Is the closest point of line to point, if point is within given distance from line. If not it is null
	 */
	protected Coordinate getMidPointCandidate (Coordinate point, Geometry line, double distance) {
		if (DistanceOp.isWithinDistance(geomOps.coordinateToPointGeometry(point), line, distance)) {
			//create LengthIndexedLine
			LengthIndexedLine indexedSlope = new LengthIndexedLine(line);
			//project lift end-point on indexed line
			double indexClosest = indexedSlope.project(point);
			//fetch closest point on slope
			Coordinate closestPoint = indexedSlope.extractPoint(indexClosest);
			//check that closest point is not one of the endPoints;
			Coordinate endPointA = line.getBoundary().getGeometryN(0).getCoordinate();
			Coordinate endPoinB = line.getBoundary().getGeometryN(1).getCoordinate();
			//return closest point only if its an interior line point
			if (!(closestPoint.equals2D(endPointA) || closestPoint.equals2D(endPoinB))) {
				return closestPoint;
			}
		}
		return null;
	}
	
	/**
	 * This method check for duplicate candidates (same start and end point and same start and end feature) and removes them.
	 * @param candidates - List of candidate point pairs, whose duplicates will be removed
	 */
	//TODO: should not be static?
	protected static void cleanDuplicates(List<Candidate> candidates) {
		Candidate current = null, next = null;
		System.out.println("\nCLEANING DUPLICATES: " + candidates.size());
		if (candidates.size()>2) { 
			for (int i=0; i<candidates.size()-2 ; i++) {
				current = candidates.get(i);
				//compare current with all other candidates
				for (int k=i+1; k<candidates.size(); k++) {
					next = candidates.get(k);
					if (current.getXml_gid_start().equals(next.getXml_gid_start()) && current.getXml_gid_end().equals(next.getXml_gid_end())) {
						if (current.getStartPoint().equals2D(next.getStartPoint()) && current.getEndPoint().equals2D(next.getEndPoint())) {
							candidates.remove(next);
						}
					}
				}
			}
		} else if (candidates.size() == 2) {
			current = candidates.get(0);
			next = candidates.get(1);
			if (current.getXml_gid_start().equals(next.getXml_gid_start()) && current.getXml_gid_end().equals(next.getXml_gid_end())) {
				if (current.getStartPoint().equals2D(next.getStartPoint()) && current.getEndPoint().equals2D(next.getEndPoint())) {
					candidates.remove(next);
				}
			}
		}
	}
	
	/**
	 * Iterates through the List of Candidates and compares each element with the following two to find redundant Candidates with the same start-end slope
	 * Once such a pair is found the slopeLiftCandCompare method takes over to find out which of both Candidates will be removed
	 * @param candidates
	 */
	protected void cleanRedundant(List<Candidate> candidates) {
		Candidate current = null, next = null, toDelete;
		System.out.println("CLEANING REDUNDANT CANDIDATES: " + candidates.size());
		//iterate and compare an item with the next two to check if its a duplicate link (to find if start and end points are the same)
		if (candidates.size()>2) {
			for (int i=0; i<candidates.size()-2; i++){
				current = candidates.get(i);
				next = candidates.get(i+1);
				//compare current with next
				if (current.getXml_gid_start().equals(next.getXml_gid_start())) {
					if (current.getXml_gid_end().equals(next.getXml_gid_end())) {
//						System.out.println(i + " ->Duplicates found! Candidates: " + (i+1) + " and " + (i+2));
						toDelete = slopeLiftCandCompare(current, next);
						if (toDelete != null) {
							candidates.remove(toDelete);
							toDelete = null;
						}
					}
				}
				next = candidates.get(i+2);
				//compare current with one after that
				if (current.getXml_gid_start().equals(next.getXml_gid_start())) {
					if (current.getXml_gid_end().equals(next.getXml_gid_end())) {
//						System.out.println(i + " ->Duplicates found! Candidates: " + (i+1) + " and " + (i+3));
						toDelete = slopeLiftCandCompare(current, next);
						if (toDelete != null) {
							candidates.remove(toDelete);
							toDelete = null;
						}
					}
				} 
			}
			//Compare the last candidate pair if size is still adequate (>2)
			if (candidates.size()>2) {
				current = candidates.get(candidates.size()-2);
				next = candidates.get(candidates.size()-1);
				if(current.getXml_gid_start().equals(next.getXml_gid_start())) {
					if (current.getXml_gid_end().equals(next.getXml_gid_end())) {
//						System.out.println("Duplicates found! Last candidate pair!");
						toDelete = slopeLiftCandCompare(current, next);
						if (toDelete != null){
							candidates.remove(toDelete);
						}
					}
				} 
			}
		}
	}
	
	
	/**
	 * Compares two slope@Lift Candidates that has common start and end feature xml_id. 
	 * Returns a reference of the longer candidate if its height is larger than 15m OR
	 * if its length is larger than 60m AND at the same time length difference of both candidates is larger than 30m 
	 * @param cand1
	 * @param cand2
	 * @return {Candidate}
	 */
	private Candidate slopeLiftCandCompare(Candidate cand1, Candidate cand2) {
		//TODO: Check if candidates are duplicate and remove one of them?
		double lengthDiff = cand1.getEndPoint().distance(cand1.getStartPoint()) - cand2.getEndPoint().distance(cand2.getStartPoint());
		Candidate longer = (lengthDiff > 0)? cand1 : cand2;
		if (Math.abs(longer.getHeightDiff()) >= 15 || (Math.abs(lengthDiff)>=30 && longer.getEndPoint().distance(cand2.getStartPoint())>=60)) {
			return longer;
		} else return null;
	}
		
	/**
	 * outputs the result point pair list to a result text file.
	 * @param txtFile {String} - pathname of the result file
	 * @param header {String}
	 * @param links {List<Link>} 
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static void printCandidates (String header, List<Candidate> candidates) throws IOException, FileNotFoundException {
		String resultFile = StartConfiguration.getInstance().getResultFile();
		File fout = new File(resultFile);
		FileWriter fw = new FileWriter(fout, true);
		BufferedWriter bw = new BufferedWriter(fw);
		//write header
		bw.write(header);
		bw.newLine();
		bw.write("##, GID_START, GID_END, HEIGHT_DIF, DIST");
		bw.newLine();
		int counter = 1;
		for (Candidate cand : candidates) {
			String line = String.format("%-3d, %8s, %8s, %8.2f, %6.2f", counter, cand.getXml_gid_start(), cand.getXml_gid_end(), cand.getHeightDiff(), cand.getEndPoint().distance(cand.getStartPoint()));
			bw.write(line);
			bw.newLine();
			counter++;
		}
		bw.newLine();
		bw.close();
	}
	
	
	/**
	 * This method splits slopes with multiple lineString geometries to the simple lineStrings they consist of 
	 * and returns them as new List of SimpleFeature objects. Identical slopes are removed
	 * @param polylineSlopes
	 * @return List<SimpleFeature> - Is a List of the split slope feature with a single lineString as Geometry
	 */
	//TODO: move method to slopeMatching?
	public static List<SimpleFeature> splitMultiLineSlopes (SimpleFeature[] polylineSlopes) {
		List<SimpleFeature> simplifiedSlopes = new ArrayList<SimpleFeature>();
		
		//lets build an appropriate SimpleFeatureType
		SimpleFeatureType type = FeatureOperations.makeFeatureType(polylineSlopes[0].getFeatureType(), "TYPE", new String[]{"XML_TYPE", "DE_GR_L_0", "DE_GR_L_1", "XML_GID", "difficulty"});
		
		for (SimpleFeature slope: polylineSlopes) {
			//Checks if slope geometry is a multiLineString
			if (!MultiLineString.class.isAssignableFrom(slope.getDefaultGeometry().getClass())) {
				Logger.getLogger(FeatureMatching.class.getName()).log(Level.SEVERE, "Slope's " + slope.getAttribute("XML_GID") + " Geometry not a MultiLineString");
				Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.SEVERE, "Slope's " + slope.getAttribute("XML_GID") + " Geometry not a MultiLineString");
			} else {
				MultiLineString mls = (MultiLineString) slope.getDefaultGeometry();
				int n = mls.getNumGeometries();
				for (int i = 0; i<n; i++) {
					LineString line = (LineString) mls.getGeometryN(i);
					SimpleFeature newSlope = featOps.getFeatureFromCoordinates(type, line.getCoordinates());
					newSlope.setAttribute("XML_TYPE", slope.getAttribute("XML_TYPE"));
					newSlope.setAttribute("DE_GR_L_0", slope.getAttribute("DE_GR_L_0"));
					newSlope.setAttribute("DE_GR_L_1", slope.getAttribute("DE_GR_L_1"));
					newSlope.setAttribute("XML_GID", slope.getAttribute("XML_GID"));
					long difficulty = 1;
					if (slope.getAttribute("DE_GR_L_3").toString().contains("Blau")) {
						difficulty = 1;
					} else if (slope.getAttribute("DE_GR_L_3").toString().contains("Rot")) {
						difficulty = 2;
					} else if (slope.getAttribute("DE_GR_L_3").toString().contains("Schwarz")) {
						difficulty = 3;
					} else difficulty = 1;
					newSlope.setAttribute("difficulty", difficulty);
					
					Coordinate[] coords = ((Geometry) newSlope.getDefaultGeometry()).getCoordinates();
					//Validate geometry to correct digitization errors
					if (coords[0].equals2D(coords[coords.length-1])) {
						Logger.getLogger(FeatureMatching.class.getName()).log(Level.SEVERE, "A segment of slope " + slope.getAttribute("XML_GID") + " is a closed Linear Ring. Removing LineString segment");
						continue;
					} else if ( (Math.floor(coords[0].x) == Math.floor(coords[coords.length-1].x)) && (Math.floor(coords[0].y) == Math.floor(coords[coords.length-1].y)) ) {
						Logger.getLogger(FeatureMatching.class.getName()).log(Level.SEVERE, "A segment of slope " + slope.getAttribute("XML_GID") + " is a closed Linear Ring. Removing LineString segment");
						continue;
					} else {
						simplifiedSlopes.add(newSlope);
					}
				}
			}
		}
		//check and remove identical slopes
		FeatureOperations.cleanDuplicateFeatures(simplifiedSlopes, "SLOPE");
		return simplifiedSlopes;
	}
	
    /**
     * This method takes a SimpleFeatureCollection and re-formats it to have a suitable FeatureType and attributes for merging.
     * It excludes links which have a not qualifying grade
     * @param links - the SimpleFeatureCollection containing the Links to be re-formated for merging
     * @param qualifyingGrades 
     * @return
     */
    public static SimpleFeatureCollection prepareLinksToMerge(SimpleFeatureCollection links, String qualifyingGrades) {
		List<SimpleFeature> mergeFeatures = new ArrayList<SimpleFeature>();
		SimpleFeatureType mergeType = FeatureOperations.makeLineStringFeatureType(links.getSchema().getCoordinateReferenceSystem(), "merged_pivots");
		SimpleFeatureIterator iterator = links.features();
		
		try {
			while (iterator.hasNext()) {
				SimpleFeature link = iterator.next();
				//continue to the next iteration if link doesn't have a qualifying grade
				if (!qualifyingGrades.contains((CharSequence) link.getAttribute("rate"))) continue;
				
				Coordinate[] linkCoords = ((Geometry)link.getDefaultGeometry()).getCoordinates();
				SimpleFeature mergeLink = featOps.getFeatureFromCoordinates(mergeType, linkCoords);
				//initialize attributes
				String xml_type = null;
				double length = ((Geometry)link.getDefaultGeometry()).getLength();
				double r_rev_c, rev_c, r_cost_1, r_cost_2, r_cost_3, cost_1, cost_2, cost_3;
				int difficulty;
				String r_id = link.getAttribute("r_id").toString();
				if (r_id.length() == 5) { r_id += "1";}
				//case slope links
				if (r_id.charAt(0) == '1') {
					xml_type = "links";
					difficulty = (int) 1; //we suppose all slope links are blue slopes (difficulty = 1)
					r_rev_c = (double) -1; rev_c= (double) -1;
					r_cost_1 = length; r_cost_2 = 10 * length; r_cost_3 = 15 * length;
                    cost_1 = length; cost_2 = 10 * length; cost_3 = 15 * length; 
				}
				//case lift links
				else if (r_id.charAt(0) == '2') {
					xml_type = "links";
					difficulty = (int) 0;
					r_rev_c = length * 50; rev_c= length * 50;
					r_cost_1 = 15 * length; r_cost_2 = 15 * length; r_cost_3 = 15 * length;
                    cost_1 = 15 * length; cost_2 = 15 * length; cost_3 = 15 * length;
				} 
				//case bus links
				else {
					xml_type = "buses";
					difficulty = (int) 0;
					r_rev_c = length * 50; rev_c= length * 50;
					r_cost_1 = 20 * length; r_cost_2 = 20 * length; r_cost_3 = 20 * length;
                    cost_1 = 20 * length; cost_2 = 20 * length; cost_3 = 20 * length;
				}
			   	//rids building
			   	int segmentNumber = 1;
		   		//build unique r_id
                int featureIdPart = Integer.parseInt(r_id);
		   		int rid = Integer.parseInt(featureIdPart + digitRectifier(segmentNumber, 3));
		   		while (assigned.contains(rid)) {
		   			rid++;
		   			segmentNumber++;
		   		}
		   		assigned.add(rid);
		   		segmentNumber++;
						
			   	Map <String, Object> attrs = new LinkedHashMap<String, Object>();
			   	attrs.put("XML_TYPE", xml_type);
			   	attrs.put("de_name", link.getAttribute("de_name"));
			   	attrs.put("difficulty", difficulty);
			   	attrs.put("source", String.valueOf(0));				//attrs.put("gid_start", link.getAttribute("gid_start"));
			   	attrs.put("target", String.valueOf(0));				//attrs.put("gid_end", link.getAttribute("gid_end"));
			   	attrs.put("duration", Double.parseDouble("0"));
			   	attrs.put("length", length);
			   	attrs.put("r_length", length);
                attrs.put("r_rev_c", r_rev_c);
                attrs.put("rev_c", rev_c);
			   	attrs.put("cost_1", cost_1);
			   	attrs.put("cost_2", cost_2);
			   	attrs.put("cost_3", cost_3);
			   	attrs.put("r_cost_1", r_cost_1);
			   	attrs.put("r_cost_2", r_cost_2);
			   	attrs.put("r_cost_3", r_cost_3);
			   	attrs.put("open", (int) 0);
			   	attrs.put("start_z", linkCoords[0].z);
			   	attrs.put("end_z", linkCoords[linkCoords.length-1].z);
			   	attrs.put("r_id", rid);
			   	
				//add attribute
				for (Map.Entry<String, Object> attribute : attrs.entrySet()){
					mergeLink = FeatureOperations.addAttribute(mergeLink, attribute.getKey(), attribute.getValue());
					mergeLink.setAttribute(attribute.getKey(), attribute.getValue());
				}
				mergeFeatures.add(mergeLink);
			}
		} finally {
			iterator.close();
		}
		SimpleFeatureCollection collectionReturn = new ListFeatureCollection(mergeFeatures.get(0).getFeatureType(), mergeFeatures);
    	return collectionReturn;	
    }
	
	public static void printLinks (String header, List<Link> links) throws IOException, FileNotFoundException {
		String resultFile = StartConfiguration.getInstance().getResultFile();
		File fout = new File(resultFile);
		FileWriter fw = new FileWriter(fout, true);
		BufferedWriter bw = new BufferedWriter(fw);
		//write header
		bw.write(header);
		bw.newLine();
		bw.write(String.format("%5s  , %s, %8s, %6s, %s, %6s", "R_ID", "GID_START", "GID_END", "DIST", "HEIGHT_DIF", "RATE"));
		bw.newLine();
		for (Link link : links) {
			String line = String.format("%6s, %9s, %8s, %6.2f, %9.2f, %6s", link.getAttributeValue("r_id"), link.getAttributeValue("gid_start"), link.getAttributeValue("gid_end"), 
					link.getDistance(), link.getHeightDiff(), link.getGrade());
			bw.write(line);
			bw.newLine();
		}
		bw.newLine();
		bw.close();
	}
	
	protected static String digitRectifier(int counter, int digits) {
		String counterToStr = String.valueOf(counter);
		if(counterToStr.length() == digits) {
			return counterToStr;
		} else {
			int remain = digits - counterToStr.length();
			for (int i = 0; i < remain ; i++){
				counterToStr = "0" + counterToStr;
			}
		}
		if (counterToStr.length() > digits) {
			Logger.getLogger(FeatureMatching.class.getName()).log(Level.SEVERE, "digitRectifier method return counter with digits " + counterToStr.length());
		}
		return counterToStr;
	}

	public SimpleFeature[] getFeatures_in() {
		return features_in;
	}

	public List<SimpleFeature> getFeat_match() {
		return feat_match;
	}
	
	public SimpleFeatureCollection getLinks() {
		return links;
	}

	public double getMax_threshold() {
		return max_threshold;
	}
	
}
