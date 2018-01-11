package preprocessing.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 * 
 * @author Thomas Kouseras
 * description:
 * This class provides file operations utility methods
 */

public class FileOperations {
	
    public static String readFile (String fileIn) {
    	String content = "";
    	try {
    		FileInputStream fis = new FileInputStream(fileIn);
    		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
    		StringBuilder sb = new StringBuilder();
    		String line = null;
    		while ((line = br.readLine()) != null) {
    			sb.append(line);
    			sb.append(System.lineSeparator());
    		}
    		br.close();
    		content = sb.toString();
    	} catch (FileNotFoundException ex) {
    		System.err.format("FileNotFoundException: %s%n", ex);
            //Logger.getLogger(FileOperations.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
        	System.err.format("IOException: %s%n", ex);
        	// Logger.getLogger(FileOperations.class.getName()).log(Level.SEVERE, null, ex);
        }    	
    	return content;
    }
	
    /**
     * Taken from: http://docs.geotools.org/stable/userguide/library/data/shape.html
     * @param str_shp a string corresponding to the path and file name of the shapefile to be read
     * @return SimpleFeatureCollection
     */
	public static SimpleFeatureCollection readShapeFile(String str_shp) {
        try {
            File shape_file = new File(str_shp);

            ShapefileDataStore dataStore = new ShapefileDataStore(shape_file.toURI().toURL());
            SimpleFeatureSource source = dataStore.getFeatureSource();
            SimpleFeatureCollection collection = source.getFeatures();
            return collection;
        } catch (MalformedURLException ex) {
            Logger.getLogger(FileOperations.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(FileOperations.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    /**
     * Taken from http://docs.geotools.org/latest/tutorials/feature/csv2shp.html
     * @param collection
     * @param shp_out_str
     */
    public static void createShapeFile(SimpleFeatureCollection collection, String shp_out_str) {
        try {
            File shape_out = new File(shp_out_str);
            SimpleFeatureType TYPE = collection.getSchema();

            ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

            Map<String, Serializable> params = new HashMap<>();
            params.put("url", shape_out.toURI().toURL());
            params.put("create spatial index", Boolean.TRUE);

            ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
            newDataStore.createSchema(TYPE);
            Transaction transaction = new DefaultTransaction("create");
            String typeName = newDataStore.getTypeNames()[0];

            SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);

            if (featureSource instanceof SimpleFeatureStore) {
                SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

                featureStore.setTransaction(transaction);
                try {
                    featureStore.addFeatures(collection);
                    transaction.commit();

                } finally {
                    transaction.close();
                }
            } else {
            	//log warning here
            	Logger.getLogger(FileOperations.class.getName()).log(Level.WARNING, typeName + " does not support read/write access");
                System.out.println(typeName + " does not support read/write access");
            }
        } catch (MalformedURLException ex) {
            Logger.getLogger(FileOperations.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(FileOperations.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
