package preprocessing;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.graph.structure.DirectedGraph;
import org.geotools.graph.structure.Graph;
import org.opengis.feature.simple.SimpleFeature;

import preprocessing.featureMatchingAPI.BusLinkMatching;
import preprocessing.featureMatchingAPI.FeatureMatching;
import preprocessing.featureMatchingAPI.LiftLinkMatching;
import preprocessing.featureMatchingAPI.SlopeLinkMatching;
import preprocessing.graph.GraphUtilities;
import preprocessing.util.FeatureOperations;
import preprocessing.util.FileOperations;


public class StartPreprocessing {
	
	public static void main(String[] args) {

/*PART 1: LOAD CONFIGURATION ********************************************************************************************/
		//If no configuration file is provided in the path arguments -> start hard-coded configuration
		String configFile = args.length == 0 ? null : args[0];
		StartConfiguration.setSELECTOR(configFile);
		
		//files in
		String lifts_in_url = StartConfiguration.getInstance().getLifts_in_url();
		String slopes_in_url = StartConfiguration.getInstance().getSlopes_in_url();
		String buses_in_url = StartConfiguration.getInstance().getBuses_in_url();
		String stops_in_url = StartConfiguration.getInstance().getStops_in_url();
		
		//create link folder if not existent
		File linkDir = new File(StartConfiguration.getInstance().getFolder_out()+"\\links\\");
		if (!linkDir.exists()) {
			linkDir.mkdirs();
		}
		
		//files out
		String lift_links_shp_out = StartConfiguration.getInstance().getFolder_out() + "\\links\\lift_to_lift.shp"; 
		String lift_slopes_shp_out = StartConfiguration.getInstance().getFolder_out() + "\\links\\lift_to_slope.shp";
		String slope_links_shp_out = StartConfiguration.getInstance().getFolder_out() + "\\links\\slope_to_slope.shp";
		String bus_links_shp_out = StartConfiguration.getInstance().getFolder_out() + "\\links\\bus_links.shp";
		String simplified_slopes_shp_out = StartConfiguration.getInstance().getFolder_out() + "\\links\\simplified_slopes.shp";
		String simplified_buses_shp_out = StartConfiguration.getInstance().getFolder_out() + "\\links\\simplified_buses.shp";
		String merged_pivots_shp_out = StartConfiguration.getInstance().getFolder_out() + "merged_pivots.shp";

/*PART 2: LOAD AND READ FEATURES TO BE LINKED********************************************************************/
		//fetch the features to be linked, load and read the lifts shapeFile
		System.out.println("---- LOADING LIFTS ----");
		SimpleFeatureCollection lifts_in = FileOperations.readShapeFile(lifts_in_url);
		SimpleFeature[] lifts = (SimpleFeature[])lifts_in.toArray();
		System.out.println("Feature collection lifts contains " + lifts_in.size() + " features");
	
		//fetch the features to be linked, load and read the slope shapeFile
		System.out.println("---- LOADING SLOPES ----");
		SimpleFeatureCollection slopes_in = FileOperations.readShapeFile(slopes_in_url);
		SimpleFeature[] slopes = (SimpleFeature[])slopes_in.toArray();
		System.out.println("Feature collection slopes contains " + slopes_in.size() + " features");
		//split current slopes in single lineString ones
		List<SimpleFeature> splitSlopes = FeatureMatching.splitMultiLineSlopes(slopes);
		//replacing slopes with split slopes in instant parameter Array
		slopes = splitSlopes.toArray(new SimpleFeature[0]);
		System.out.println("New single LineString slopes collection contains: " + slopes.length + " features");
	
		//create 'splitSlopes'folder if not existent
		File splitSlopesDir = new File(StartConfiguration.getInstance().getFolder_out()+"\\splitSlopes\\");
		if (!splitSlopesDir.exists()) {
			splitSlopesDir.mkdirs();
		}
		//create new shapeFile for single LineString slopes if needed
		SimpleFeatureCollection splitSlopesCollection = new ListFeatureCollection(splitSlopes.get(0).getFeatureType(), splitSlopes);
		//create splitSlopes shapeFile
		FileOperations.createShapeFile(splitSlopesCollection, StartConfiguration.getInstance().getFolder_out()+"\\splitSlopes\\splitted_slopes.shp");

		//fetch the features to be linked, load and read the bus shapeFile, only if it exists
		SimpleFeature[] buses = null, stops = null;
		if (!StartConfiguration.getInstance().getFile_in_bus().equals("") && !StartConfiguration.getInstance().getFile_in_busStops().equals("")) {
			System.out.println("---- LOADING BUS LINES & STOPS ----");
			SimpleFeatureCollection buses_in = FileOperations.readShapeFile(buses_in_url);
			SimpleFeatureCollection stops_in = FileOperations.readShapeFile(stops_in_url);
			System.out.println("Feature collection buses contains: " + buses_in.size() + " features and stops contains: " + stops_in.size());
			buses = (SimpleFeature [])buses_in.toArray();
			stops = (SimpleFeature [])stops_in.toArray();
		}
		
		
/*PART 3: CREATING LINKS ***************************************************************************************/

		LiftLinkMatching lift_to_lift = new LiftLinkMatching(lifts);
			 
		SlopeLinkMatching slope_links = new SlopeLinkMatching(slopes, lifts);
				
		BusLinkMatching bus_links = null;
		if (buses != null && stops != null) {
			bus_links = new BusLinkMatching(buses, stops, slopes, lifts);
		} else {
			Logger.getLogger(StartPreprocessing.class.getName()).log(Level.SEVERE, "Could not load bus_links and/or bus stops");
			Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.SEVERE, "Could not load bus_links and/or bus stops");
		}
	
/*PART 4: CREATE OUTPUT SHAPEFILES *************************************************************************/
			
		SimpleFeatureCollection lifts_out = lift_to_lift.getLinks();
		FileOperations.createShapeFile(lifts_out, lift_links_shp_out);
		
		SimpleFeatureCollection lift_slopes_out = slope_links.getLinks();
		FileOperations.createShapeFile(lift_slopes_out, lift_slopes_shp_out);
	
		SimpleFeatureCollection slopes_out = slope_links.getSlopeLinks();
		FileOperations.createShapeFile(slopes_out, slope_links_shp_out);
		
		SimpleFeatureCollection bus_out = null;
		if (bus_links != null) {
			bus_out = bus_links.getLinks();
			FileOperations.createShapeFile(bus_out, bus_links_shp_out);
		}
	
/*PART 5: SIMPLIFY BUS LINE AND SLOPE FEATURES ************************************************************************/
		SimpleFeatureCollection simplifiedSlopes = slope_links.simplify(bus_out); 
	
		FileOperations.createShapeFile(simplifiedSlopes, simplified_slopes_shp_out); 
	
		SimpleFeatureCollection simplifiedBuses = null;
		if (bus_links!=null) {
			simplifiedBuses = bus_links.simplify();
			FileOperations.createShapeFile(simplifiedBuses, simplified_buses_shp_out);	
		}

/*PART 5: CREATE MERGED PIVOTS (FEATURES) ************************************************************************/		
		SimpleFeatureCollection lifts_merge = LiftLinkMatching.getLiftsToMerge(lift_to_lift.getFeatures_in());
		SimpleFeatureCollection lift_slope_links_merge = FeatureMatching.prepareLinksToMerge(lift_slopes_out, StartConfiguration.getInstance().getGrades()); 
		SimpleFeatureCollection lift_links_merge = FeatureMatching.prepareLinksToMerge(lifts_out, StartConfiguration.getInstance().getGrades()); 
		SimpleFeatureCollection bus_links_merge = null;
		if (bus_out!= null) {
			bus_links_merge = FeatureMatching.prepareLinksToMerge(bus_out, StartConfiguration.getInstance().getGrades());
		}
		SimpleFeatureCollection slopes_links_merge = FeatureMatching.prepareLinksToMerge(slopes_out, StartConfiguration.getInstance().getGrades());
		
		//merge links and simplified features and create graph
		SimpleFeatureCollection merge_pivots = FeatureOperations.mergeSimpleFeatureCollections(simplifiedSlopes, lifts_merge);
		if (bus_links!=null) {
			merge_pivots = FeatureOperations.mergeSimpleFeatureCollections(merge_pivots, simplifiedBuses);
			merge_pivots = FeatureOperations.mergeSimpleFeatureCollections(merge_pivots, bus_links_merge);
		}
		merge_pivots = FeatureOperations.mergeSimpleFeatureCollections(merge_pivots, slopes_links_merge);
		merge_pivots = FeatureOperations.mergeSimpleFeatureCollections(merge_pivots, lift_slope_links_merge);
		merge_pivots = FeatureOperations.mergeSimpleFeatureCollections(merge_pivots, lift_links_merge);
		FileOperations.createShapeFile(merge_pivots, merged_pivots_shp_out);

/*PART 6: CREATE AND CHECK GRAPH **********************************************************************************/
		//build directed graph from merged_features
		DirectedGraph directedGraph = GraphUtilities.getDirectedGraph(merge_pivots);
		System.out.println("Created directed graph with " + directedGraph.getNodes().size() + " Nodes and " + directedGraph.getEdges().size() + " Edges");
		//check if connected
		int startNode = 300001003;
		if (GraphUtilities.isConnected(directedGraph, 300001003)) {
			System.out.println("Final Graph is connected");
		} else {
			System.out.println("Final Graph is not connected");
		}

/*		GraphUtilities.reachAllFromOne(directedGraph);

		 TESTDATASET ONE PATHS 
		//return path from bus stop to slope start with difficulty factor 0
		List<Integer> firstPath = GraphUtilities.getPath(directedGraph, 300003003, 300001001, 0);
		System.out.println(firstPath);
		//previous source is now destination with difficulty factor 3
		List<Integer> secondPath = GraphUtilities.getPath(directedGraph, 300001001, 300003003, 3);
		System.out.println(secondPath);
		//return path from slope start to bus stop with difficulty factor 2
		List<Integer> thirdPath = GraphUtilities.getPath(directedGraph, 300000001, 108295002, 2);
		System.out.println(thirdPath);
		//return path from slope start to bus stop with difficulty factor 1
		List<Integer> fourthPath = GraphUtilities.getPath(directedGraph, 300000001, 108295002, 1);
		System.out.println(fourthPath);
		//return path from bus stop to another bus stop with difficulty factor 0
		List<Integer> fifthPath = GraphUtilities.getPath(directedGraph, 108054001, 108295002, 2);
		System.out.println(fifthPath);*/
		
		/*SKI AMADE PATH*/
		//return path in Schladming-Dachstein from lift start to bus stop with difficulty factor 0
		List<Integer> firstPath = GraphUtilities.getPath(directedGraph, 207743001, 108228001, 0);
		System.out.println(firstPath);
		//return path in Schladming-Dachstein from lift start to bus stop with difficulty factor 0
		List<Integer> secondPath = GraphUtilities.getPath(directedGraph, 207795001, 207777001, 0);
		System.out.println(secondPath);
		
		/* SPORTGASTEIN PATHS 
		//return path from bus stop to slope start with difficulty factor 0
		List<Integer> firstPath = GraphUtilities.getPath(directedGraph, 300001003, 107934002, 0);
		System.out.println(firstPath);
		//return path from slope start to bus stop with difficulty factor 1
		List<Integer> secondPath = GraphUtilities.getPath(directedGraph, 107934002, 300001003, 0);
		System.out.println(secondPath);
		//return path from slope start to bus stop with difficulty factor 2
		List<Integer> thirdPath = GraphUtilities.getPath(directedGraph, 108271001, 300001003, 0);
		System.out.println(thirdPath);
		//return path from slope start to bus stop with difficulty factor 3
		List<Integer> fourthPath = GraphUtilities.getPath(directedGraph, 108271001, 300001003, 3);
		System.out.println(fourthPath);
		/*---------------------*/

		//code for undirected graph
/*		Graph graph = GraphUtilities.getGraphFromFeatureCollection(merge_pivots);
		System.out.println("Created undirected graph with " + graph.getNodes().size() + " Nodes and " + graph.getEdges().size() + " Edges");
		GraphUtilities.reachAllFromOne(graph);
		if (GraphUtilities.isConnected(graph)) {
			System.out.println("Final Graph is connected");
		} else {
			System.out.println("Final Graph is not connected");
		}*/
		
	}
}