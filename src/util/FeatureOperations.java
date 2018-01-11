package preprocessing.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.Hints;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

import preprocessing.StartConfiguration;
import preprocessing.connectionsAPI.AbstractPointPair;
import preprocessing.connectionsAPI.Candidate;

/**
 * 
 * @author Thomas Kouseras
 * description:
 * This class provides various SimpleFeature utility methods
 */

public class FeatureOperations {
	
	private GeometryFactory geometryFactory;
	
	public FeatureOperations () {
		int srid = StartConfiguration.getInstance().getSrid();
		initGeometryFactory(srid);
	}
	
	private void initGeometryFactory(int srid) {
		PrecisionModel precisionModel = new PrecisionModel();
		Hints hints = new Hints(Hints.JTS_PRECISION_MODEL, precisionModel);
		hints.put(Hints.JTS_SRID, srid);
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory(hints);	
	}
		
	/**
	 * This method creates a new SimpleFeatureType out of another one by keeping the geometry attribute, 
	 * discarding all others and adding the provided list of string attributes
	 * @param template  	Is a SimpleFeatureType whose geometry attribute is going to be copied
	 * @param typeName  	The name of the new SimpleFeatureType
	 * @param attributes 	A list of attributes as array of Strings to add to the new SimplpeFeatureType
	 * @return SimpleFeatureType
	 */
	public static SimpleFeatureType makeFeatureType (SimpleFeatureType template, String typeName, String[] attributes) {

		SimpleFeatureTypeBuilder stb = new SimpleFeatureTypeBuilder();
		stb.init(template);
		stb.setName(typeName);
		List<AttributeDescriptor> l = template.getAttributeDescriptors();

        for (int i = 0; i < l.size(); i++) {
            String name = l.get(i).getLocalName();
            	if (!name.equals("the_geom"))
                stb.remove(name);
        }
        if(attributes != null) {
            for (String attr: attributes) {
            	stb.add(attr, String.class);
            }
        }

		return stb.buildFeatureType();
	}
	
	/**
	 * This method build a SimpleFeatureType with one geometry attribute of com.vividsolutions.jts.geom.Point class
	 * @param crs		CoordinateReferenceSystem for the geometry
	 * @param typeName	Name of the FeatureType
	 * @return SimpleFeatureType
	 */
	private static SimpleFeatureType makePointFeatureType (CoordinateReferenceSystem crs, String typeName) {
		SimpleFeatureTypeBuilder stb = new SimpleFeatureTypeBuilder();
		stb.setName(typeName);	//set name
		stb.setCRS(crs);		//set crs
		stb.add("the_geom", Point.class); //set geometry attribute
		SimpleFeatureType TYPE = stb.buildFeatureType();
		return TYPE;
	}
	
    /**
     * This method build a SimpleFeatureType with one geometry attribute of com.vividsolutions.jts.geom.Point class
     * @param crs		CoordinateReferenceSystem for the geometry
     * @param typeName	Name of the FeatureType
     * @return
     */
	public static SimpleFeatureType makeLineStringFeatureType (CoordinateReferenceSystem crs, String typeName) {
		SimpleFeatureTypeBuilder stb = new SimpleFeatureTypeBuilder();
		stb.setName(typeName);	//set name
		stb.setCRS(crs);		//set crs
		stb.add("the_geom", LineString.class); //set geometry attribute
		SimpleFeatureType TYPE = stb.buildFeatureType();
		return TYPE;
	}
	
    /**
     * builds a point feature from a Coordinate object, using a provided feature type
     * @param: type {SimpleFeatureType} - a feature type template for the new feature to be created
     * @param: point {Coordinate}
     * @return: SimpleFeature
     */
    public SimpleFeature getFeatureFromCoordinates(SimpleFeatureType type, Coordinate point) {
		final SimpleFeatureType TYPE = type;
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
		Point pt = this.geometryFactory.createPoint(point);
		featureBuilder.set(0, pt);
		SimpleFeature sf = featureBuilder.buildFeature(null);
		return sf;
   }
	
    /**
     * builds a line feature from a Coordinate object sequence, using a provided feature type
     * @param: type {SimpleFeatureType} - a feature type template for the new feature to be created
     * @param: coords_in {Coordinate[]}
     * @return: SimpleFeature
     */
    public SimpleFeature getFeatureFromCoordinates(SimpleFeatureType type, Coordinate[] coords_in) {
		final SimpleFeatureType TYPE = type;
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
		LineString ls = this.geometryFactory.createLineString(coords_in);
		featureBuilder.set(0, ls);
		SimpleFeature sf = featureBuilder.buildFeature(null);
		return sf;
	 }
     
    /**
     * This method creates a new SimpleFeature Object as an exact copy of the method parameter
     * @param feat - the SimpleFeature to be duplicated
     * @return SimpleFeature - a new SimpleFeature reference that is an exact copy of the provided one 
     */
    public static SimpleFeature duplicateFeature(SimpleFeature feat) {
		//create and initialize the builder to create the second feature
		SimpleFeatureBuilder builder = new SimpleFeatureBuilder(feat.getFeatureType());
		builder.init(feat);
		//create the new feature
		SimpleFeature duplicate = builder.buildFeature(null);
		return duplicate;
    }
     
 	/**
 	 * This method returns a SimpleFeatureCollection of point features created from the intersection candidates of slopes
 	 * @param features	The slope candidates containing the intersection points
 	 * @param srid		The coordinate reference system used, needed to create SimpleFeatureType for point geometry
 	 * @param name		The name of the SimpleFeatureType that will be created
 	 * @return SimpleFeatureCollection
 	 */
    public SimpleFeatureCollection getIntersectionsFeatureCollection(List<Candidate> features, CoordinateReferenceSystem srid, String name) {
     	List<SimpleFeature> features_out = new ArrayList<SimpleFeature>();
     	SimpleFeatureType pointType =  makePointFeatureType(srid, name);
     	for (Candidate feat: features) {
     		if (!feat.getType().equals("Slope2Slope")) {
     			SimpleFeature sf_new = getFeatureFromCoordinates(pointType, feat.getStartPoint());
     			//add attributes
     			for (Map.Entry<String, Object> attribute : feat.getAttributes().entrySet()) {
     				sf_new = addAttribute(sf_new, attribute.getKey(), attribute.getValue());
     				sf_new.setAttribute(attribute.getKey(), attribute.getValue());
     			}
     			features_out.add(sf_new);
     		}
     	}
     	SimpleFeatureCollection new_features = new ListFeatureCollection(features_out.get(0).getFeatureType(), features_out);
     	return new_features;
     }
    
    /**
     * method which creates a lineString for each coordinate pair found in the links param and 
     * returns a SimpleFeatureCollection containing all newly created features from the pairs.
     * Link as common points (length = 0) are excluded
     * @param master_feat {Simple Feature} - used to copy a featureType from an existing feature
     * @param link {List<Coordinate[]} - a list of Coordinate pairs 
     * @return SimpleFeatureCollection
     */
    public SimpleFeatureCollection getFeatureCollectionFromLinks(SimpleFeatureType templateType, List<AbstractPointPair> links) {
		List<SimpleFeature> features_out_pairs = new ArrayList<SimpleFeature>();
		
		templateType = clearAttributes(templateType);
		
		//for each coordinate pair, whose distance > 0, create a feature
		for (AbstractPointPair link : links) {
			if (link.getStartPoint().distance(link.getEndPoint()) > 0.00) {
				Coordinate [] coords = {link.getStartPoint(), link.getEndPoint()};
				SimpleFeature sf_new = getFeatureFromCoordinates(templateType, coords);
				
				//add attribute
				for (Map.Entry<String, Object> attribute : link.getAttributes().entrySet()){
					sf_new = addAttribute(sf_new, attribute.getKey(), attribute.getValue());
					sf_new.setAttribute(attribute.getKey(), attribute.getValue());
				}
				
				//end of attribute building
				features_out_pairs.add(sf_new);
			}
		}
    	SimpleFeatureCollection new_features = new ListFeatureCollection(features_out_pairs.get(0).getFeatureType(), features_out_pairs);
		return new_features;
    }
  
    /**
     * This method takes a SimpleFeatureType as input and clear it from all its attributes except the geometry
     * @param template The SimpleFeatureType to be cleaned
     * @return SimpleFeatureType
     */
	public static SimpleFeatureType clearAttributes (SimpleFeatureType template) {
		SimpleFeatureTypeBuilder stb = new SimpleFeatureTypeBuilder();
		stb.init(template);
		List<AttributeDescriptor> l = template.getAttributeDescriptors();

        for (int i = 0; i < l.size(); i++) {
            String name = l.get(i).getLocalName();
            	if (!name.equals("the_geom"))
                stb.remove(name);
        }
		return stb.buildFeatureType();
	}
	
	/**
	 * 
     * @param: feat_in {SimpleFeature}
     * @param: attrubute_name {String}
     * @param: value {Object}
     * @description:
     * returns a new instance of a SimpleFeature which has exactly the same feature type, attributes and values as the input parameter,
     * and additionally has a new attribute with its provided value
     * CAUTION: the returned object should be assigned to the old reference to avoid confusion
     * @return: SimpleFeature
	 */
    public static SimpleFeature addAttribute(SimpleFeature feat_in, String attribute_name, Object value) {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        SimpleFeatureType type = feat_in.getFeatureType();
        builder.init(type);
        builder.add(attribute_name, value.getClass());
        type = builder.buildFeatureType();
        SimpleFeatureBuilder simpleFeatureBuilder = new SimpleFeatureBuilder(type);
        simpleFeatureBuilder.addAll(feat_in.getAttributes());
        SimpleFeature sf = simpleFeatureBuilder.buildFeature(null);
        sf.setAttribute(attribute_name, value);
        return sf;
    }
	
	
	/**
	 * This method check and removes geometric identical features
	 * @param listToCheck {List<SimpleFeature>} - A list of SimpleFeatures to check and remove duplicates
	 * @param outputPhrase {String} - Needed for output
	 */
	public static void cleanDuplicateFeatures(List<SimpleFeature> listToCheck, String feature) {
		
		for (int i=0; i<listToCheck.size(); i++) {
			List<Integer> indicesToRemove = new ArrayList<Integer>();
			Geometry current = (Geometry) listToCheck.get(i).getDefaultGeometry();
			for (int k=i+1; k<listToCheck.size(); k++) {
				Geometry next = (Geometry) listToCheck.get(k).getDefaultGeometry();
				if (current.equalsExact((Geometry)next)) {
					//duplicate found add index for removal
					indicesToRemove.add(k);
				}
			}
			
			if (indicesToRemove.size() > 0) {
				//Descending order of indices of the objects to be removed is crucial, not to affect remaining indices after each removal
				Collections.sort(indicesToRemove, Collections.reverseOrder());
				for (Integer index: indicesToRemove) {
					System.err.println("DELETING DUPLICATE " + feature + ": " + listToCheck.get(index));
					Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "DELETING DUPLICATE " + feature + ": " + listToCheck.get(index));
					listToCheck.remove(listToCheck.get(index));
				}
			}
		}
	}
	
	/**
	 * This method builds and returns a List of SimpleFeatures out of a SimpleFeatureCollection
	 * @param collection - The SimpleFeatureCollection that has to be transformed to a List<SimpleFeature>
	 * @return a List<SimpleFeature>
	 */
	public static List<SimpleFeature> featureCollectionToList(SimpleFeatureCollection collection) {
		List<SimpleFeature> list = new ArrayList<SimpleFeature>();
		SimpleFeatureIterator iterator = collection.features();
		try {
			while (iterator.hasNext()) {
				list.add(iterator.next());
			}
		} finally {
			iterator.close();
		}
		
		if (list.size() == collection.size()) {
			return list;
		} else {
			Logger.getLogger(FeatureOperations.class.getName()).log(Level.SEVERE, "featureCollectionToList() resolved List with smaller size than argument. Aborting...");
			Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.SEVERE, Thread.currentThread().getStackTrace()[1].getMethodName() + "resolved List with smaller size than argument. Aborting...");
			return null;
		}
		
	}
	
	/**
     * Splits a feature at its vertices, producing point pair line segments and returns those segments as a list of SimpleFeatures
	 * @param originalFeature -  feature whose geometry is to split
	 * @return
	 */
	public ArrayList<SimpleFeature> splitFeatureAtVertices(SimpleFeature originalFeature) {
        ArrayList<SimpleFeature> features_out = new ArrayList<>();
        Geometry geom = (Geometry) originalFeature.getDefaultGeometry();
        int length = geom.getNumPoints();
        Coordinate[] coords = geom.getCoordinates();
        for (int i = 0; i < length - 1; i++) {
            Coordinate[] c = {coords[i], coords[i + 1]};
            SimpleFeature sf = getFeatureFromCoordinates(originalFeature.getFeatureType(), c);
            features_out.add(sf);
        }
        return features_out;
	}
	
	/**
	 * Takes a feature and a list of coordinates are parameters. The list of coordinates represents a list of the input feature's vertices, 
	 * where the feature geometry should split. It splits the geometry at the given vertices and creates features for each derived geometry. 
	 * @param originalFeature - feature to be split. Must have a LineString geometry type
	 * @param segments_splitpoints - vertices where the feature should be split
	 * @return list of derived features representing the split geometries
	 */
	public ArrayList<SimpleFeature> splitFeatureAtCoordinates(SimpleFeature originalFeature, List<Coordinate> segments_coords) {
		//deep copy of segments_coords to avoid clash cause of object removal
		List<Coordinate> segments_splitpoints = new ArrayList<Coordinate>();
		for (Coordinate seg_coord: segments_coords) {
			segments_splitpoints.add((Coordinate) seg_coord.clone());
		}
		
		//set distance threshold to identify split points according to type
		String type = originalFeature.getAttribute("XML_TYPE").toString();
		double distForSplitPoint = (type.equals("buses")) ? 1 : 0.001;
		
		ArrayList<SimpleFeature> features_out = new ArrayList<SimpleFeature>();
		
		//fetch original feature vertices as LengthIndexedLine
		Geometry lineGeom = (Geometry)originalFeature.getDefaultGeometry();
		LengthIndexedLine line = new LengthIndexedLine(lineGeom);

		int segment_number = 1;
		double lastEndPointFlag = line.getStartIndex();
		//fetch line segment of LengthIndexedLine for each point pair of segments_splitpoints. Check distance to next point every time, in case they coincide remove.
		Iterator<Coordinate> iterator = segments_splitpoints.iterator();
		while (iterator.hasNext()) {
			//get split-point coordinate
			Coordinate nextEnd = iterator.next();
			
			if (type.equals("slopes")) {
				//if slope check if start point is in close proximity to split coordinate. Remove and move to the next split point.
				if (line.extractPoint(0.0).distance(line.extractPoint(line.project(nextEnd))) < distForSplitPoint) {
					iterator.remove();
					nextEnd = iterator.next();
				}
			} else {
				//if bus line check distance from last end point. Remove and move to the next split point.
				if (line.extractPoint(lastEndPointFlag).distance(line.extractPoint(line.project(nextEnd))) < distForSplitPoint) {
					iterator.remove();
					nextEnd = iterator.next();
				}
			}

			//extract line segment
			Geometry segment_geom = line.extractLine(lastEndPointFlag, line.project(nextEnd));
			//create feature and add it to the output list
			SimpleFeature segment = getFeatureFromCoordinates(originalFeature.getFeatureType(), segment_geom.getCoordinates());
			//add r_id attribute
			//TODO: method copy attributes needed to copy all original attributes to the derived features
			segment = addAttribute(segment, "r_id", segment_number);
			features_out.add(segment);
			segment_number++;
			//pass end-point to flag
			lastEndPointFlag = line.project(nextEnd);
		}

        return features_out;
	}
	
	/**
	 * 
	 * @param sfc1
	 * @param sfc2
	 * @return
	 */
    public static SimpleFeatureCollection mergeSimpleFeatureCollections(SimpleFeatureCollection sfc1, SimpleFeatureCollection sfc2) {
        if (!sfc1.getSchema().getTypes().equals(sfc2.getSchema().getTypes())) {
            System.err.println("FeatureOperations.mergeSimpleFeatureCollections Warning: Different SimpleFeatureTypes.");
        }
        List<SimpleFeature> allFeatures = new ArrayList<>();
        allFeatures.addAll((Collection<? extends SimpleFeature>) sfc1);
        allFeatures.addAll((Collection<? extends SimpleFeature>) sfc2);
        SimpleFeatureType type = sfc1.getSchema();
        return new ListFeatureCollection(type, allFeatures);
    }
}
