package preprocessing.graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.graph.build.feature.FeatureGraphGenerator;
import org.geotools.graph.build.line.DirectedLineStringGraphGenerator;
import org.geotools.graph.build.line.LineStringGraphGenerator;
import org.geotools.graph.path.DijkstraShortestPathFinder;
import org.geotools.graph.path.Path;
import org.geotools.graph.structure.DirectedEdge;
import org.geotools.graph.structure.DirectedGraph;
import org.geotools.graph.structure.Edge;
import org.geotools.graph.structure.Graph;
import org.geotools.graph.structure.Node;
import org.geotools.graph.traverse.basic.BasicGraphTraversal;
import org.geotools.graph.traverse.basic.CountingWalker;
import org.geotools.graph.traverse.standard.DijkstraIterator;
import org.geotools.graph.traverse.standard.DirectedDijkstraIterator;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;

import preprocessing.util.FeatureOperations;
import preprocessing.util.GeometryOperations;

/**
 * @author Thomas Kouseras
 * class which introduces graph utility methods
 */
public class GraphUtilities {
	
	/**
	 * This method builds a graph from a feature collection made up of linear features
	 * (taken from: http://docs.geotools.org/stable/userguide.old/extension/graph/index.html)
	 * @param merged_features
	 * @return an undirected graph
	 */
	public static Graph getGraphFromFeatureCollection(SimpleFeatureCollection merged_features) {
		System.out.println("\n---- CREATING AND REVIEWING UNDIRECTED GRAPH AS TOPOLOGICAL LEVEL OF FINAL DATA OUTPUT ----");
		//create a linear graph generator
		LineStringGraphGenerator lineStrGen = new LineStringGraphGenerator();
		//and wrap it in a feature graph generator
		final FeatureGraphGenerator featureGen = new FeatureGraphGenerator(lineStrGen);
		//throw all the features into the graph generator
		try {
			merged_features.accepts(new FeatureVisitor() {
			    public void visit(Feature feature) {
			    	featureGen.add(feature);
			    }
			}, null);
		} catch (IOException e) {
			Logger.getLogger(GraphUtilities.class.getName()).log(Level.SEVERE, "Graph generation aborted: " + e);
			Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.SEVERE, "Graph generation aborted: " + e);
		}
		Graph graph = featureGen.getGraph();
		checkDuplicateNodes(graph);
		return graph;
	}
	
	/**
	 * Creates a directed graph from the merged_features dataSet. Edge direction depends on feature:
	 *  - slopes start from upper node end to lower node
	 *  - lifts start from lower node end to upper node
	 *  - buses and links are represented as bi-directional edges aka two edges, one for each direction 
	 * @param merged_features
	 * @return a directed graph
	 */
	public static DirectedGraph getDirectedGraph(SimpleFeatureCollection merged_features) {
		System.out.println("\n---- CREATING DIRECTED GRAPH AS TOPOLOGICAL LEVEL OF FINAL DATA OUTPUT ----");
		
		final GeometryOperations geomOps = new GeometryOperations();
		
		//create a directed lineString graph generator
		final DirectedLineStringGraphGenerator lineStrGen = new DirectedLineStringGraphGenerator();
		//System.out.println(lineStrGen.getGraphBuilder()); // is a BasicDirectedGraphBuilder
		//and wrap it in a feature graph generator
		final FeatureGraphGenerator featureGen = new FeatureGraphGenerator(lineStrGen);
		
		SimpleFeatureIterator iterator = merged_features.features(); //
		
		try {
			while(iterator.hasNext()) {
					SimpleFeature feature = iterator.next();
					String edgeType = feature.getAttribute("XML_TYPE").toString();
					Coordinate[] featureCoords = ((Geometry)((SimpleFeature) feature).getDefaultGeometry()).getCoordinates();
					Coordinate[] orderedCoords = GeometryOperations.getOrderedEndPoints(((SimpleFeature) feature));
					if(Arrays.asList(featureCoords).contains(null)) {
						System.out.println(((SimpleFeature) feature).getAttribute("r_id").toString());
						System.out.println(Arrays.asList(featureCoords));
						System.err.println("Feature has null vertex. Aborting .. ..");
						System.exit(0);
					}
					
					
					//check that segment has only two vertices
					Geometry segment = (Geometry)((SimpleFeature) feature).getDefaultGeometry();
					if (segment.getCoordinates().length != 2) { 
						String message = ("Problem " + segment.getCoordinates().length + " , TYPE " + edgeType + ", ID " 
								+ ((SimpleFeature) feature).getAttribute("r_id").toString()); 
						Logger.getLogger(GraphUtilities.class.getName()).log(Level.SEVERE, message);
						Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.SEVERE, message);
					}

					//Initialize variables for the switch cases
					Coordinate[] coords;
					Geometry directedLineSegment;
					DirectedEdge e;
					//build edge based on feature type of segment
					switch (edgeType) {
						case "slopes":
							//create new lineString segment to add as directed edge (direction upper -> lower)
							if(Arrays.asList(orderedCoords).contains(null)) {
								coords = new Coordinate[]{featureCoords[1], featureCoords[0]};
							} else {
								coords = new Coordinate[]{orderedCoords[1], orderedCoords[0]};
							}
							directedLineSegment = geomOps.coordinatesToLineGeometry(coords);
							//get decoraded.add()
							e = (DirectedEdge)lineStrGen.add(directedLineSegment);
							//get Graphable.setObject()
							e.setObject((SimpleFeature) feature);
							//check if directed geometry is correct
							if (!((Geometry) e.getInNode().getObject()).equals(geomOps.coordinateToPointGeometry(coords[0])) || 
									!((Geometry) e.getOutNode().getObject()).equals(geomOps.coordinateToPointGeometry(coords[1]))) {
								System.err.println("Slope segment " + ((SimpleFeature)e.getObject()).getAttribute("r_id") + " was falsly entered in directed graph");
							}
						break;
						case "lifts":
							//create new lineString segment to add as directed edge (direction lower -> upper)
							coords = new Coordinate[]{orderedCoords[0], orderedCoords[1]};
							directedLineSegment = geomOps.coordinatesToLineGeometry(coords);
							e = (DirectedEdge)lineStrGen.add(directedLineSegment);
							e.setObject((SimpleFeature) feature);
							//check if directed geometry is correct
							if (!((Geometry) e.getInNode().getObject()).equals(geomOps.coordinateToPointGeometry(orderedCoords[0])) || 
									!((Geometry) e.getOutNode().getObject()).equals(geomOps.coordinateToPointGeometry(orderedCoords[1]))) {
								String message = ("Lift segment " + ((SimpleFeature)e.getObject()).getAttribute("r_id") + " was falsly entered in directed graph");
								Logger.getLogger(GraphUtilities.class.getName()).log(Level.SEVERE, message);
								Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.SEVERE, message);
							}
						break;
						case "buses":
							//add one direction as directed edge (direction lower -> upper)
							coords = new Coordinate[]{orderedCoords[0], orderedCoords[1]};
							directedLineSegment = geomOps.coordinatesToLineGeometry(coords);
							e = (DirectedEdge)lineStrGen.add(directedLineSegment);
							e.setObject((SimpleFeature) feature);
							
							//add opposite direction as directed edge (direction lower -> upper)
							coords = new Coordinate[]{orderedCoords[1], orderedCoords[0]};
							directedLineSegment = geomOps.coordinatesToLineGeometry(coords);
							e = (DirectedEdge)lineStrGen.add(directedLineSegment);
							e.setObject((SimpleFeature) feature);
						break;
						case "links":
							/* for slope and lift links one edge is created and direction is preserved 
							 * (link is created with geometry pointing from feature_in to feature_out) 
							 * (first coordinate of link geometry comes from to feature_in and second from feature_out)
							 */							
							coords = new Coordinate[]{featureCoords[1], featureCoords[0]};
							directedLineSegment = geomOps.coordinatesToLineGeometry(coords);
							e = (DirectedEdge)lineStrGen.add(directedLineSegment);
							e.setObject((SimpleFeature) feature);
							
							coords = new Coordinate[]{featureCoords[0], featureCoords[1]};
							directedLineSegment = geomOps.coordinatesToLineGeometry(coords);
							e = (DirectedEdge)lineStrGen.add(directedLineSegment);
							e.setObject((SimpleFeature) feature);
						break;
					}			
				}
		} 
		catch (Exception e) {
			Logger.getLogger(GraphUtilities.class.getName()).log(Level.SEVERE, "Directed graph generation aborted: " + e);
			Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.SEVERE, "Directed graph generation aborted: " + e);
		} 
		finally {
			iterator.close();
		}
		
		DirectedGraph graph = (DirectedGraph)featureGen.getGraph();
		checkDuplicateNodes(graph);
		return  graph;
	}
	
	/**
	 * This method prepares the merged_pivots featureCollection for the directedGraph creation. It corrects the features and creates new ones,
	 *  so that each feature in the returned collection will correspond to a directed edge in the final graph
	 * @param features - the collection of SimpleFeatures to be prepared for the directed graph
	 * @return
	 */
	private static DefaultFeatureCollection prepareFeatures (SimpleFeatureCollection features) {
		DefaultFeatureCollection collection = new DefaultFeatureCollection("internal");
		
		SimpleFeatureIterator iterator = features.features();
		GeometryOperations geomOps = new GeometryOperations();
		try {
			while (iterator.hasNext()) {
				SimpleFeature feature = iterator.next();
				String type = feature.getAttribute("XML_TYPE").toString();				
				Coordinate[] featureCoords = ((Geometry)feature.getDefaultGeometry()).getCoordinates();
				Coordinate[] orderedCoords = GeometryOperations.getOrderedEndPoints(feature);
				
				//check that segment has only two vertices, none of which is null
				if(Arrays.asList(featureCoords).contains(null)) {
					System.out.println(((SimpleFeature) feature).getAttribute("r_id").toString());
					System.out.println(Arrays.asList(featureCoords));
					System.err.println("Feature has null vertex. Aborting .. ..");
					System.exit(0);
				} else if (featureCoords.length !=2 ) {
					String message = ("Problem " + featureCoords.length + " , TYPE " + type + ", ID " 
							+ (feature).getAttribute("r_id").toString()); 
					Logger.getLogger(GraphUtilities.class.getName()).log(Level.SEVERE, message);
					Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.SEVERE, message);
				}
				
				//if type is "LIFTS" new geometry LineString from lower to upper
				if (type.equals("lifts")) {
					LineString segmentToEdgeGeom = (LineString) geomOps.coordinatesToLineGeometry(orderedCoords);
					feature.setAttribute("the_geom", segmentToEdgeGeom);
					collection.add(feature);
					continue;
				}
				//if type is "SLOPES" new geometry LineString from lower to upper
				if (type.equals("slopes")) {
					LineString segmentToEdgeGeom = null;
					if (orderedCoords != null) {
						segmentToEdgeGeom = (LineString) geomOps.coordinatesToLineGeometry(orderedCoords);
						feature.setAttribute("the_geom", segmentToEdgeGeom);
						collection.add(feature);
					} else {//if there is no height difference between slope's endPoints create two features for each direction
						//first one direction
						segmentToEdgeGeom = (LineString) geomOps.coordinatesToLineGeometry(new Coordinate[]{featureCoords[0], featureCoords[1]});
						feature.setAttribute("the_geom", segmentToEdgeGeom);
						collection.add(feature);
						//then second direction as a new feature
						segmentToEdgeGeom = (LineString) geomOps.coordinatesToLineGeometry(new Coordinate[]{featureCoords[1], featureCoords[0]});
						SimpleFeature copy = FeatureOperations.duplicateFeature(feature);
						copy.setAttribute("the_geom", segmentToEdgeGeom);
						collection.add(copy);
					}
					continue;
				}
				//if type is buses or links one feature has to be created for each direction
				if (type.equals("buses") || type.equals("links")) {
					//first one direction
					LineString segmentToEdgeGeom = (LineString) geomOps.coordinatesToLineGeometry(new Coordinate[]{featureCoords[0], featureCoords[1]});
					feature.setAttribute("the_geom", segmentToEdgeGeom);
					collection.add(feature);
					
					//then second direction as a new feature
					segmentToEdgeGeom = (LineString) geomOps.coordinatesToLineGeometry(new Coordinate[]{featureCoords[1], featureCoords[0]});
					SimpleFeature copy = FeatureOperations.duplicateFeature(feature);
					copy.setAttribute("the_geom", segmentToEdgeGeom);
					collection.add(copy);
					continue;
				}
			}
		} finally {
			iterator.close();
		}
		
		return collection;
	}

	public static DirectedGraph getDirectedGraphII(SimpleFeatureCollection merged_features) {
		System.out.println("\n---- CREATING DIRECTED GRAPH AS TOPOLOGICAL LEVEL OF FINAL DATA OUTPUT ----");
		
		//prepare features for directed graph
System.out.println(merged_features.size() + " features before preparation");
		DefaultFeatureCollection preparedFeatures = prepareFeatures(merged_features);
System.out.println(preparedFeatures.size() + " features after preparation");
		
		//create a directed lineString graph generator
		final DirectedLineStringGraphGenerator lineStrGen = new DirectedLineStringGraphGenerator();
		//and wrap it in a feature graph generator
		final FeatureGraphGenerator featureGen = new FeatureGraphGenerator(lineStrGen);
		SimpleFeatureIterator iterator = preparedFeatures.features();
		try {
			while(iterator.hasNext()) {
					SimpleFeature feature = iterator.next();
					featureGen.add(feature);
				}
		} catch (Exception e) {
			Logger.getLogger(GraphUtilities.class.getName()).log(Level.SEVERE, "Directed graph generation aborted: " + e);
			Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.SEVERE, "Directed graph generation aborted: " + e);
		} finally {
			iterator.close();
		}
		
		DirectedGraph graph = (DirectedGraph)featureGen.getGraph();

		List<DirectedEdge> allEdges = new ArrayList<DirectedEdge>(graph.getEdges());
		for (Edge e : allEdges) {
			SimpleFeature sf = (SimpleFeature) e.getObject();
			Coordinate start = ((Geometry)((DirectedEdge)e).getInNode().getObject()).getCoordinate() ;
			Coordinate end = ((Geometry)((DirectedEdge)e).getOutNode().getObject()).getCoordinate();
			String type = (String) sf.getAttribute("XML_TYPE");
			Coordinate[] edgeGeom = ((Geometry) ((SimpleFeature) e.getObject()).getDefaultGeometry()).getCoordinates();
			if (type == "lifts") {
				if( (start.z > end.z) || (edgeGeom[0].z > edgeGeom[1].z) || !start.equals2D(edgeGeom[0]) || !end.equals2D(edgeGeom[1])) {
					System.err.println("Lift edge not valid");
				}
			} else if (type == "slopes") {
				if ( (end.z > start.z) || (edgeGeom[1].z > edgeGeom[0].z) || !start.equals2D(edgeGeom[0]) || !end.equals2D(edgeGeom[1])  ) {
					System.err.println(" Slope edge not valid ");
				}
			} else if (type == "buses") {
				System.out.println(Arrays.asList(edgeGeom));
			}
		}

		checkDuplicateNodes(graph);
		return  graph;
	}
	
	/**
	 * This method examines a graph for duplicate nodes and prints them out
	 * @param graph
	 */
	public static void checkDuplicateNodes (Graph graph) {
		Collection<Integer> duplicates = new ArrayList<Integer>();
		
		Collection<?> allNodes = graph.getNodes();
		Object[] nodes = allNodes.toArray();
		for (int i=0; i <nodes.length; i++) {
			Node current = (Node) nodes[i];
			Geometry currentGeom = (Geometry)current.getObject();
			for (int k=i+1; i<nodes.length; i++) {
				Node next = (Node) nodes[k];
				Geometry nextGeom = (Geometry)next.getObject();
				if(currentGeom.equals(nextGeom)) {
					duplicates.add(next.getID());
				}
			}
		}
		if (duplicates.size()>1) {
			System.out.println("Graph has " + duplicates.size() + " duplicate Nodes:");
			System.out.println(duplicates.toString());	
		}
	}
	
	/**
	 * An EdgeWeighter which returns each edge length as weight
	 */
	private static DijkstraIterator.EdgeWeighter lengthWeighter = new DijkstraIterator.EdgeWeighter() {
		   public double getWeight(Edge e) {
		      SimpleFeature feature = (SimpleFeature) e.getObject();
		      LineString geometry = (LineString) feature.getDefaultGeometry();
		      return geometry.getLength();
		   }
	};
		
	/**
	 * A method to create different Dijkstra edgeWeighter based on the slope difficulty, used as a factor when asking for a route
	 * @param difficulty - is the slope difficulty to be preferred when calculating the route
	 * @return
	 */
	private static DirectedDijkstraIterator.EdgeWeighter getDifficultyWeighter(int difficulty) {
		final int difficultyPreference = difficulty;
		DirectedDijkstraIterator.EdgeWeighter difficultyWeighter = new DirectedDijkstraIterator.EdgeWeighter() {
			public double getWeight(Edge e) {
				SimpleFeature feature = (SimpleFeature) e.getObject();
				double cost = 0;
				switch (difficultyPreference) {
					case 1:
						cost = (double) feature.getAttribute("cost_1");
					break;
					case 2:
						cost = (double) feature.getAttribute("cost_2");
					break;
					case 3:
						cost = (double) feature.getAttribute("cost_3");
					break;
					case 0:
						cost = (double) feature.getAttribute("length");
					break;
				}
				return cost;
			}
		};
		return difficultyWeighter;
	};
	
	/**
	 * 
	 * @param graph - directed graph on which Dijkstra algorithm will operate
	 * @param r_idSource - feature id, whose start vertex (inNode) will serve as path start
	 * @param r_idDestination - feature id, whose start vertex (inNode) will serve as path destination
	 * @param difficulty - which difficulty should be used as weight factor
	 * @return
	 */
	public static List<Integer> getPath (DirectedGraph graph, int r_idSource, int r_idDestination, int difficulty) {
		Node start = null, destination = null;
		
		//find start Node from r_idSource
		@SuppressWarnings("unchecked")
		List<Edge> allEdges = new ArrayList<Edge>(graph.getEdges());
		for (Edge e : allEdges) {
			SimpleFeature sf = (SimpleFeature) ((DirectedEdge)e).getObject();
			int rid = (int) sf.getAttribute("r_id");
			if (rid == r_idSource) {
				start = ((DirectedEdge)e).getInNode();
				break;
			}
		}
		//find destination Node from r_idSource
		for (Edge e : allEdges) {
			SimpleFeature sf = (SimpleFeature) ((DirectedEdge)e).getObject();
			int rid = (int) sf.getAttribute("r_id");
			if (rid == r_idDestination) {
				destination = ((DirectedEdge)e).getInNode();
				break;
			}
		}

		DirectedDijkstraIterator iterator = new DirectedDijkstraIterator(getDifficultyWeighter(difficulty));
		iterator.setSource(start);

        // Create GraphWalker - in this case DijkstraShortestPathFinder
		DijkstraShortestPathFinder pf = new DijkstraShortestPathFinder(graph, iterator); //new DijkstraShortestPathFinder( graph, start, getDifficultyWeighter(difficulty) );
		pf.calculate();
		
		//Path  to return, as list of feature's r_id.
		List<Integer> path = new ArrayList<Integer>();
		
		Path shortestPath = pf.getPath( destination );
		 
		//return feature r_ids that participate in the path
		if (shortestPath!=null && shortestPath.isValid()) {
			@SuppressWarnings("unchecked")
			List<Edge> pathEdges= shortestPath.getEdges();
//			List<Integer> pathFeaturesIds = new ArrayList<Integer>();
			for (Edge pathEdge: pathEdges) {
				Integer rid = (Integer) ((SimpleFeature) pathEdge.getObject()).getAttribute("r_id");
				if (!path.contains(rid)) { 
					path.add(Integer.valueOf(rid)); } 
				}
		}
		
		return path;
	}
	
	/**
	 * This method calculates the shortest path from one starting Node to each Node of the graph, using a Dijkstra algorithm implementation
	 * The DijkstraIterator is using a simple length edgeWeighter which assigns to each edge its length as weight
	 * @param graph The graph to calculate paths for.
	 */
	public static List<List<Integer>> reachAllFromOne(Graph graph) {
		System.out.println("\n---- Route calculation from a single starting node ----");
		
		Node start = null;
		//List of  destinations to calculate paths to
		List<Node> destinations = new ArrayList<Node>();
		
		//check that no Node is visited before traversing the Graph
		@SuppressWarnings("unchecked")
		Collection<Node> allNodes = graph.getNodes();
		int count = 0;
		for (Node n : allNodes) {
			if (!n.isVisited()) {
				count++;
			}
		}
		if (allNodes.size() == count) {
			System.out.println("No Node is Visited");
		}
		
		@SuppressWarnings("unchecked")
		Collection<Edge> allEdges = graph.getEdges();
		//use a bus stop as a starting node (NodeA of feature with r_id	300001003)
		for (Edge e : allEdges) {
			SimpleFeature sf = (SimpleFeature) e.getObject();
			int rid = (int) sf.getAttribute("r_id");
			if (rid == 300001003) {
				start = e.getNodeA();
				System.out.println("Setting start point at: " + start.getObject().toString());
				break;
			}
		}
		
		//iterate through all edges and add all nodes to a list of destinations
		for (Edge e : allEdges) {
			Node a = e.getNodeA();
			Node b = e.getNodeB();
			if (!destinations.contains(a)) {
				destinations.add(a);
			}
			if (!destinations.contains(b)){
				destinations.add(b);
			}
		}

		// Create GraphWalker - in this case DijkstraShortestPathFinder
		DijkstraShortestPathFinder pf = new DijkstraShortestPathFinder( graph, start, lengthWeighter );
		pf.calculate();
		
		//List of paths to return. Each list contains a list of feature ids included in path
		List<List<Integer>> paths = new ArrayList<List<Integer>>();
		
		//calculate the paths
		for ( Iterator<Node> d = destinations.iterator(); d.hasNext(); ) {
		  Node destination = (Node) d.next();
		  Path path = pf.getPath( destination );
		 
		  //return feature ids that participate in the path
		  if (path!=null) {
			  @SuppressWarnings("unchecked")
			  List<Edge> pathEdges= path.getEdges();
			  List<Integer> pathFeaturesIds = new ArrayList<Integer>();
			  for (Edge pathEdge: pathEdges) {
				  Integer rid = (Integer) ((SimpleFeature) pathEdge.getObject()).getAttribute("r_id");
				  if (!pathFeaturesIds.contains(rid)) { pathFeaturesIds.add(Integer.valueOf(rid)); } 
			  }
			  paths.add(pathFeaturesIds);
		  }
		}
		
		System.out.println(paths.size() + " paths returned for " + destinations.size() + " destinations");
		
		for (Node n : allNodes) {
			if (!n.isVisited()) {
				System.out.println("Node " + n.getID() + " not visited!");
			}
		}
		return paths;
	}
	
	/**
	 * This method making a single traversal from a starting Node and returns true if all Nodes were visited during this traversal. Otherwise returns false
	 * @param graph The graph to calculate paths for.
	 * @return A flag which indicates whether all graph Nodes were visited during the traversal
	 */
	public static boolean isConnected (Graph graph, int startNodeFeatureRid) {
		System.out.println("---- Checking Graph connectivity ----");
		
		CountingWalker walker = new CountingWalker();
		DijkstraIterator  iterator =  new DijkstraIterator (
		        new DijkstraIterator.EdgeWeighter() {
		            public double getWeight(Edge e) {
		            	//all edges have equal weight (1)
		              return(1); 
		            }
	            }
        );
		
		Node start = null;
		@SuppressWarnings("unchecked")
		Collection<Edge> allEdges = graph.getEdges();
		//use a bus stop as a starting node (NodeA of feature with r_id	300001003)
		for (Edge e : allEdges) {
			SimpleFeature sf = (SimpleFeature) e.getObject();
			int rid = (int) sf.getAttribute("r_id");
			if (rid == startNodeFeatureRid) {
				start = e.getNodeA();
			}
		}
		if (start != null) { iterator.setSource(start);}
		BasicGraphTraversal traversal = new BasicGraphTraversal(graph, walker, iterator);
		traversal.init();
		traversal.traverse();
		System.out.println("Traversal traversed " + walker.getCount() + " Nodes");
		
		List<Integer> unvisited = new ArrayList<Integer>();
		Collection<Node> nodes = (Collection<Node>)graph.getNodes();
		for (Node n : nodes) {
			if (!n.isVisited()) { 
				unvisited.add(n.getID());
				//System.out.println("Node " + n.getID() + " not visited!");
			}
		}
		
		//uncomment to print unvisited feature nodes
/*		for (Edge e : allEdges) {
			if (unvisited.contains(e.getNodeA().getID()) || unvisited.contains(e.getNodeB().getID())) {
				SimpleFeature sf = (SimpleFeature) e.getObject();
				int rid = (int) sf.getAttribute("r_id");
				System.err.println("A Node of feature " + rid + " was not visited");
			}
		}*/
	
		if (walker.getCount() == graph.getNodes().size()) {
			return true;
		}
		
		return false;
	}
	
}
