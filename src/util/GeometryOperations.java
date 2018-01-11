package preprocessing.util;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.factory.Hints;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.linearref.LengthIndexedLine;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;

import preprocessing.StartConfiguration;

/**
 * 
 * @author Thomas Kouseras
 * description:
 * This class provides certain geometry operations utility methods
 */

public class GeometryOperations {
	
	private GeometryFactory geometryFactory;
	private int srid;
	
	public GeometryOperations () {
		this.srid = StartConfiguration.getInstance().getSrid();
		initGeometryFactory(srid);
	}
	
	private void initGeometryFactory (int srid) {
		PrecisionModel precisionModel = new PrecisionModel();
		Hints hints = new Hints(Hints.JTS_PRECISION_MODEL, precisionModel);
		hints.put(Hints.JTS_SRID, srid);
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory(hints);
	}
	
    /**
     * @param: coord {Coordinate}
     * @return: Geometry
     * description: creates a point Geometry object out of the given Coordinate object
     */
	public Geometry coordinateToPointGeometry(Coordinate coord) {
		Geometry geom = geometryFactory.createPoint(coord);
		return geom;
	}
	
	/**
	 * 
	 * @param coords {Coordinate []}
	 * @return: Geometry
	 * description: turn a Coordinate array to a linear geometry
	 */
    public Geometry coordinatesToLineGeometry(Coordinate[] coords) {
        WKTReader wktr = new WKTReader(this.geometryFactory);
        Geometry geom = null;
        try {
            LineString ls = this.geometryFactory.createLineString(coords);
            geom = wktr.read(ls.toString());
        } catch (ParseException ex) {
            Logger.getLogger(GeometryOperations.class.getName()).log(Level.SEVERE, null, ex);
        } 
        return geom;
    }
	
	/**
	 * @param geom_in {Geometry}
	 * @param geom_out {Geometry}
	 * @return: List<Point> or null
	 * description: checks if two input geometries intersect and returns the vertices of their intersection Geometry as a List of Coordinates. 
	 * In case no intersection exists, it returns null.
	 * If the intersection is a single Point, but a GeometryCollection (MultiPoint, LineString) it applies the DouglasPeuckerSimplifier method 
	 * to remove intersection points within a 100m distance from the previous
	 */
	public List<Coordinate> getIntersectionVertices(Geometry geom_in, Geometry geom_out) {
		
		//check if two geometries intersect
		boolean intersects =  geom_in.intersects(geom_out); 
		
		if (intersects) {
			//fetch intersection geometry
			Geometry intersection = geom_in.intersection(geom_out);
			//create array of Vertices to return
			List<Coordinate> vertices = new ArrayList<Coordinate>();
			//check geometry class and return List or Array of Points or Point or LineString			       
	        boolean isPoint = intersection != null  && (Point.class.isAssignableFrom(intersection.getClass()));
	        
	        if (isPoint) {
	        	Coordinate vertex = get3DLinePoint(((Point)intersection).getCoordinate(), geom_in);
	        	vertices.add(vertex);
	        } else {
	        	//Simplify intersection coordinate list
	        	Geometry simplifiedGeom = DouglasPeuckerSimplifier.simplify(coordinatesToLineGeometry(intersection.getCoordinates()), 100.00);
	        	for (Coordinate intersectionVertex: simplifiedGeom.getCoordinates()) {
	        		//interpolate z coordinate if it is NaN
	        		Coordinate vertex = get3DLinePoint(intersectionVertex, geom_in);
	        		vertices.add(vertex);	        		
	        	}
	        }
	        return vertices;
		}
        return null;
	}
	
	/**
	 * This method is used to find the closest point out of an array of coordinates
	 * <b>Note:</b> Euclidean distance is used to compute the distance
	 * @param c_in {Coordinate} Coordinate representation of a point, to fetch nearest vertex to
	 * @param coords_in {Coordinate[]} Coordinate representations of a geometries vertices
	 * @return Coordinate Is the vertex with the smallest distance to c_in
	 */
    public static Coordinate getNearestVertex(Coordinate c_in, Coordinate[] coords_in) {
        double minDistance = Double.POSITIVE_INFINITY;
        int minIndex = -1;
        for (int i = 0; i < coords_in.length; i++) {
            Coordinate c = coords_in[i];
            double distance =c_in.distance(c);
            if (distance < minDistance) {
                minDistance = distance;
                minIndex = i;
            }
        }
        if (minIndex >= 0) {
            return coords_in[minIndex];
        }
        return null;
    }
    
    /**
     * This method is used to find the closest vertex to a lineString
	 * <p>
	 * <b>Note:</b> Euclidean distance is used to compute the distance
     * @param c_in {Coordinate} Coordinate representation of a point, to fetch nearest vertex to
     * @param lineString {Geometry} Linear geometry whose closest vertex should be returned
     * @return Coordinate Is the vertex with the smallest distance to c_in
     */
    public static Coordinate getNearestVertex(Coordinate c_in, Geometry lineString) {
        Coordinate[] vertices = lineString.getCoordinates();
        double minDistance = Double.POSITIVE_INFINITY;
        int minIndex = -1;
        for (int i = 0; i < vertices.length; i++) {
            Coordinate vertex = vertices[i];
            double distance = c_in.distance(vertex);
            if (distance < minDistance) {
                minDistance = distance;
                minIndex = i;
            }
        }
        if (minIndex >= 0) {
            return vertices[minIndex];
        }
        return null;
    }
    
    /**
     * This method return the same point as the one passed in, but with an interpolated Z coordinate
     * @param point2D - A Coordinate representation of a point on a 3D Line with Z = NaN 
     * @param line - A 3D Line whose point, with a Z ordinate, we need to extract
     * @return {Coordinate} The point2D with an interpolated Z ordinate
     */
    public static Coordinate get3DLinePoint(Coordinate point2D, Geometry line) {
    	if (Double.isNaN(point2D.z)) {
    		//create LenthgIndexedLine to get point on line with interpolated Z coordinate
        	LengthIndexedLine indexedLine = new LengthIndexedLine(line);
        	double index = indexedLine.indexOf(point2D);
        	Coordinate point3D = indexedLine.extractPoint(index);
        	return point3D;
    	} else
    		return point2D;
    }
    
	/**
	 * this method returns an array with the end points of the feature, 
	 * the lower end being in the first slot of the array and the upper end on the second
	 * @param {Simplefeature} feature
	 * @return {Coordinate []}
	 */
	public static Coordinate[] getOrderedEndPoints (SimpleFeature feature) {
		Coordinate [] orderedEndPoints = new Coordinate[2];
		Geometry geom_in = (Geometry) feature.getDefaultGeometry();
		Coordinate [] coords = geom_in.getCoordinates();
		Coordinate coordA = coords[0];
		Coordinate coordB = coords[coords.length-1];
		Coordinate lower = null, upper = null; 
		if (coordA.z/10 < coordB.z/10) {
			lower = coordA;
			upper = coordB;
		} else if (coordA.z/10 > coordB.z/10) {
			lower = coordB;
			upper = coordA;
		}
		orderedEndPoints[0] = lower;
		orderedEndPoints[1] = upper;
		return orderedEndPoints;
	}

}
