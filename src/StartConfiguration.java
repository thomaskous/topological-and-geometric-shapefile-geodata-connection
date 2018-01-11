package preprocessing;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import preprocessing.util.PreprocessingLogger;
import preprocessing.util.FileOperations;


/**
 * @author Thomas Kouseras
 * @description a Singleton class which reads program configurations from file and makes them available throughout the whole software.
 * Create output directory if not present
 * Note: that links shapefile and cleanCoords array is not needed in this version
 */
public class StartConfiguration {
	//Instance Variables
    private String folder_in = "";
    private String folder_out = "";
    private String file_in_slopes = "";
    private String file_in_lifts = "";
    private String file_in_bus = "";
    private String file_in_busStops = "";
    private String logFile = "";
    private String resultFile = "";
    private boolean outputCandidates;
    private int srid = 0;
    private int[] lift_distances = null;
    private int[] slope_distances = null;
    private int[] lift_heights = null;
    private int[] slope_heights = null;
    private double slope_endpoint_dist = 0.0;
    private double [] slope_midpoint_dist = null;
    private int[] bus_distances = null;
    private int[] bus_heights = null;
    private String grades = "";
	
    
    private static String SELECTOR;
	private volatile static StartConfiguration newConfiguration;
	
	/*Accessor method of outside the class. getInstance() is static so that we can call it without instantiating the class. 
	 * The first time getInstance() is called it creates a new singleton object and after that it just returns the same object.*/
	public static StartConfiguration getInstance(){
		if (newConfiguration == null) {
			//to make it thread-safe
			synchronized (StartConfiguration.class) {
				//check again as multiple threads can reach above step
				if (newConfiguration == null) {
					if (SELECTOR != null) {
						newConfiguration = new StartConfiguration(SELECTOR);
					} else {
						newConfiguration = new StartConfiguration();
					}
				}
			}
		}
		return newConfiguration;
	}
	
	//private constructor (when configuration file exists) to force use of getInstance() to create newConfiguration object
    private StartConfiguration ( String configFile ) {
		System.out.println("Running configuration from file");
	    	initConfiguration(configFile);
	    	this.initLogger();
	    	checkConfiguration();
	    	showConfiguration();
    }
    
	//private default constructor to force use of getInstance() to create newConfiguration object
    private StartConfiguration () {
	    	System.out.println("Running hardcoded configuration");
	    	//initialize some default values
	    	this.folder_in = "/Users/Thomas/Projects/My_Project/data/First_Test_Area/";
	    	this.folder_out = "/Users/Thomas/Projects/My_Project/output/";
	    	this.file_in_slopes = "slopes.shp";
	    	this.file_in_lifts = "lifts.shp";
	    	this.file_in_bus = "buses.shp";
	    	this.file_in_busStops = "bus_stops.shp";
	    	this.logFile = "/Users/Thomas/Projects/My_Project/output/preprocessing.log";
	    	this.resultFile = "/Users/Thomas/Projects/My_Project/output/results.txt";
	    	this.outputCandidates = true;
	    	this.lift_distances = new int[]{200, 160, 95, 50};
	    	this.slope_distances = new int[]{160, 100, 80, 60, 40};
	    	this.bus_distances = new int[]{350, 200, 100};
	    	this.lift_heights = new int[]{35, 10, 5, 1};
	    	this.slope_heights = new int[]{30, 25, 20, 15, 10, 5};
	    	this.bus_heights = new int[]{20, 15, 10};
	    	this.srid = 323632;
	    	this.slope_endpoint_dist = 20.00;
	    	this.slope_midpoint_dist = new double[]{60, 10};
	    	this.grades = "AB";
	    	this.initLogger();
	    	checkConfiguration();
	    	showConfiguration();
    }
    
    /**
     * @param file
     * @description
     * Instantiates the instance variables with the right urls for the program to continue
     */
    private void initConfiguration ( String file ) {
        String content = FileOperations.readFile(file);
        String[] lines = content.split("\r");
        for (String line : lines) {
            if (!line.contains("#") && line.contains("=")) {
                String lineStart = line.substring(0, line.indexOf("=")).trim().toLowerCase();
                String lineContent = line.substring(line.indexOf("=") + 1).trim().toLowerCase();
                if (lineStart.contains("folder_in")) {
                    if (!lineContent.endsWith("\\")) {
                        lineContent += "\\";
                    }
                    this.folder_in = lineContent;
                } else if (lineStart.contains("folder_out")) {
                    if (!lineContent.endsWith("\\")) {
                        lineContent += "\\";
                    }
                    this.folder_out = lineContent;
                } else if (lineStart.contains("file_in_slopes")) {
                    this.file_in_slopes = lineContent;
                } else if (lineStart.contains("file_in_lifts")) {
                    this.file_in_lifts = lineContent;
                } else if (lineStart.contains("file_in_bus")) {
                    this.file_in_bus = lineContent;
                } else if (lineStart.contains("file_in_stops")) {
                	this.file_in_busStops = lineContent;
                } else if (lineStart.contains("log_file")){
                	this.logFile = folder_out + lineContent;
                } else if (lineStart.contains("results_file")) {
                	this.resultFile = folder_out + lineContent;
                } else if (lineStart.contains("output_candidates")) {
                	this.outputCandidates = Boolean.parseBoolean(lineContent.trim());
                } else if (lineStart.contains("lifts_dist")) {
            		String [] distances = lineContent.split(",");
            		this.lift_distances = new int[distances.length];
            		for (int i = 0; i < lift_distances.length; i++){
            			this.lift_distances[i] = Integer.parseInt(distances[i].trim());
            		}
                } else if (lineStart.contains("lifts_height_dif")) {
                	String [] heights = lineContent.split(",");
                	this.lift_heights = new int[heights.length];
                	for (int i=0; i < lift_heights.length; i++){
                		this.lift_heights[i] = Integer.parseInt(heights[i].trim());
                	}
                } else if (lineStart.contains("slopes_dist")) {
                	String [] slopeDistances = lineContent.split(",");
                	this.slope_distances = new int[slopeDistances.length];
                	for (int i=0; i < slope_distances.length; i++){
                		this.slope_distances[i] = Integer.parseInt(slopeDistances[i].trim());
                	}
                } else if (lineStart.contains("slopes_height_dif")){
                	String [] slopeHeights = lineContent.split(", ");
                	this.slope_heights = new int[slopeHeights.length];
                	for (int i=0; i<slope_heights.length; i++){
                		this.slope_heights[i] = Integer.parseInt(slopeHeights[i].trim());
                	}
                } else if (lineStart.contains("bus_dist")) {
                	String [] busDistances = lineContent.split(",");
                	this.bus_distances = new int[busDistances.length];
                	for (int i=0; i<bus_distances.length; i++) {
                		this.bus_distances[i] = Integer.parseInt(busDistances[i].trim());
                	}
                } else if (lineStart.contains("bus_height_dif")){
                	String [] busHeights = lineContent.split(", ");
                	this.bus_heights = new int[busHeights.length];
                	for (int i=0; i<bus_heights.length; i++){
                		this.bus_heights[i] = Integer.parseInt(busHeights[i].trim());
                	}
                } else if (lineStart.contains("slopes_endpoint")) {
                	this.slope_endpoint_dist = Double.parseDouble(lineContent.trim());
                } else if (lineStart.contains("slopes_midpoint")) { 
                	String [] slopeMidPointDists = lineContent.split(", ");
                	this.slope_midpoint_dist = new double[slopeMidPointDists.length]; 
                	for (int i=0; i<slope_midpoint_dist.length; i++) {
                		this.slope_midpoint_dist[i] = Double.parseDouble(slopeMidPointDists[i].trim());
                	}
                } else if (lineStart.contains("srid")) {
                    this.srid = Integer.parseInt(lineContent);
                } else if (lineStart.contains("link_grades")) {
                	String [] gradesArray = lineContent.split(",");
                	for (String grade: gradesArray) {
                		this.grades += grade.trim().toUpperCase();
                	}
                }
            }
        }
        
        //clear result file
        File resultF = new File(this.resultFile);
        PrintWriter contentEraser = null;
        if(resultF.exists()) {
        	try {
				contentEraser = new PrintWriter(resultF);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
            contentEraser.close();
        }
    }
    
    private void initLogger () {
    	if(this.getLogFile().isEmpty()) {
    		this.setLogFile(folder_out + "preprocessing.log");
    	}
        //clear log file
        File logF = new File(this.getLogFile());
        if(!logF.exists()) {
        	try {
				logF.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
    	PreprocessingLogger.setup(this.getLogFile());
    }
        
    /**
     * @description
     * Checks if the needed files (lifts and slopes) are present within the input folder
     */
    private void checkConfiguration () {
		//create folder out if not exist
		File folder_out = new File(this.folder_out);
		if ( !folder_out.exists() ) {
			folder_out.mkdirs();
			System.out.println("Created folder: " + folder_out.getAbsolutePath());
		}
		
		//if outputCandidates is TRUE create candidates output sub-folder (candidates) if not exists
		folder_out = new File(this.folder_out + "\\candidates\\");
		if(this.outputCandidates == true && !folder_out.exists() ) {
			folder_out.mkdir();
			System.out.println("Created folder: " + folder_out.getAbsolutePath());
		}
		
		//NOTICE!!!: lift distances, slope distances and lift height have to be present and include always 4 values
		if (this.lift_distances == null || this.lift_distances.length != 4) {this.lift_distances = new int[]{200, 160, 95, 50};}
		if (this.slope_distances == null || this.slope_distances.length < 5) {this.slope_distances = new int[]{160, 100, 80, 60, 40};}
		if (this.bus_distances != null && this.bus_distances.length < 3) {this.bus_distances = new int[]{350, 200, 100};}
		if (this.lift_heights == null || this.lift_heights.length != 4) {this.lift_heights = new int[]{35, 10, 5, 1};}
		if (this.slope_heights == null || this.slope_heights.length != 6) {this.slope_heights = new int[]{30, 25, 20, 15, 10, 5};}
		if (this.bus_heights != null && this.bus_heights.length != 3) {this.bus_heights = new int[]{20, 15, 10};}
		//if slope_endpoint and slope_midpoint not present start default
		if (this.slope_endpoint_dist == 0.00) { this.slope_endpoint_dist = 20.00; }
		if (this.slope_midpoint_dist == null || this.slope_midpoint_dist.length !=2) {this.slope_midpoint_dist  = new double[] {60.00, 10.00}; }
		if ((!this.grades.toUpperCase().contains("A") && !this.grades.toUpperCase().contains("B") && !this.grades.toUpperCase().contains("C") && !this.grades.toUpperCase().contains("D")) || this.grades.length()>4) {
			Logger.getLogger(StartConfiguration.class.getName()).log(Level.WARNING, "Wrong grade settings provided. Grades reset to A,B,C,D");
			this.grades = "ABCD";
		}
    	
        String str_slopes_url = this.getSlopes_in_url();
        File f_slope = new File(str_slopes_url);
        if (!f_slope.exists()) {
            System.err.println("Error: couldnt find slope shapefile (path: " + str_slopes_url + ")");
            System.err.println("Exiting program.");
            System.exit(1);
            Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.WARNING, "Slope shapefile not fount (path: " + str_slopes_url + ")");
            this.file_in_slopes = "not_existent";
            
        }
        String str_lifts_url = this.getLifts_in_url();
        File f_lifts = new File(str_lifts_url);
        if (!f_lifts.exists()) {
            System.err.println("Error: couldnt find lift shapefile (path: " + str_lifts_url + ")");
            System.err.println("Exiting program.");
            System.exit(1);
            Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.WARNING, "Slope shapefile not fount (path: " + str_slopes_url + ")");
            this.file_in_lifts = "not_existent";
        }
       String str_buses_url = this.getBuses_in_url();
       File f_buses = new File(str_buses_url);
       String str_stops_url = this.getStops_in_url();
       File f_stops = new File(str_stops_url);
       if (!f_buses.exists() || !f_stops.exists()) {
    	   this.file_in_bus = "";
    	   this.file_in_busStops = "";
    	   System.err.println("Couldn't find bus line or bus stop input shapefiles (path: " + str_buses_url + ")");
    	   System.err.println("Continuing without bus lines connection");
    	   Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.WARNING, "Slope bus shapefiles not fount");
       }
    }
    
	/**
     * @description Prints out the URLs of the input and output folder,
     * as well as the input files paths, plus the SRID value provided
     */
    private void showConfiguration () {
        System.out.println("#########################");
        System.out.println("---- CONFIGURATION ----");
        System.out.println("folder in: " + this.folder_in);
        System.out.println("folder out: " + this.folder_out);
        System.out.println("log file: " + this.logFile);
        System.out.println("results file: " + this.resultFile);
        System.out.println("create candidate shapefiles: " + this.outputCandidates);
        System.out.println("file (slopes): " + this.file_in_slopes);
        System.out.println("file (lifts): " + this.file_in_lifts);
        if (!this.file_in_bus.equals("") && !this.file_in_busStops.equals("")) {
            System.out.println("file (bus): " + this.file_in_bus);
            System.out.println("file (bus stops): " + this.file_in_busStops);
        }
        System.out.print("lift-link distance thresholds: ");
        for (int dist : this.lift_distances) { System.out.print(dist + ", ");}
        System.out.print("\nlift-link height thresholds: ");
        for (int height : this.lift_heights) { System.out.print(height + ", ");}
        System.out.print("\nslope-lift link distance thresholds:");
        for (int dist : this.slope_distances) {System.out.print(dist + ", ");}
        System.out.print("\nslope-lift link height thresholds: ");
        for (int height : this.slope_heights) { System.out.print(height + ", ");}
        System.out.print("\nbus link distance thresholds: ");
        if (this.bus_distances != null) {
            for (int dist : this.bus_distances) {System.out.print(dist + ", ");}
        }
        if (this.bus_heights !=null) {
        	 System.out.print("\nbus link height thresholds: ");
             for (int height : this.bus_heights) { System.out.print(height + ", ");}
        }
        System.out.println("\nslope end-point distance threshold: " + this.slope_endpoint_dist);
        System.out.print("slope mid-point distance thresholds: ");
        if (this.slope_midpoint_dist != null) {
        	for (double dist: this.slope_midpoint_dist) {System.out.print(dist + ", ");}
        }
        System.out.println("\nQualifying link grades for the final merged_pivots features are: " + this.grades);
        System.out.println("SRID: " + this.srid);
        System.out.println("#########################");
    }
	
	/**
	 * return the path name of the lifts input shapefile
	 * @return String
	 */
	public String getLifts_in_url() {
		return this.folder_in + this.file_in_lifts;
	}
	
	/**
	 * return the path name of the slopes input shapefile
	 * @return String
	 */
	String getSlopes_in_url() {
		return this.folder_in + this.file_in_slopes;
	}
	
	/**
	 * return the path name of the bus lines input shapefile
	 * @return String
	 */
	String getBuses_in_url() {
		return this.folder_in + this.file_in_bus;
	}
	
	/**
	 * return the path name of the bus stops input shapefile
	 * @return String
	 */
	String getStops_in_url() {
		return this.folder_in + this.file_in_busStops;
	}
	
    public boolean isOutputCandidates() {
		return outputCandidates;
	}
    
	public String getFolder_in() {
		return folder_in;
	}

	public String getFolder_out() {
		return folder_out;
	}

	public String getFile_in_slopes() {
		return file_in_slopes;
	}

	public String getFile_in_lifts() {
		return file_in_lifts;
	}

	public String getFile_in_bus() {
		return file_in_bus;
	}
	
	public int[] getLift_distances() {
		return this.lift_distances;
	}
	
	public int getSrid() {
		return srid;
	}

	public String getLogFile() {
		return logFile;
	}

	public void setLogFile(String logFile) {
		this.logFile = logFile;
	}
	
	public String getResultFile() {
		return resultFile;
	}
	
	public static void setSELECTOR(String configPresent) {
		SELECTOR = configPresent;
	}

	public int[] getSlope_distances() {
		return slope_distances;
	}
	
	public int[] getBus_distances() {
		return this.bus_distances;
	}

	public int[] getLift_heights() {
		return lift_heights;
	}

	public int[] getSlope_heights() {
		return slope_heights;
	}
	
	public int[] getBus_heights() {
		return this.bus_heights;
	}

	public double getSlope_endpoint_dist() {
		return slope_endpoint_dist;
	}

	public double[] getSlope_midpoint_dist() {
		return slope_midpoint_dist;
	}

	public String getFile_in_busStops() {
		return file_in_busStops;
	}

	public String getGrades() {
		return grades;
	}
    
}
