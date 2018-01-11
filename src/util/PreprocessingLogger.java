package preprocessing.util;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * @author Thomas Kouseras
 * Logger class to log output in text file
 */
public class PreprocessingLogger {
	private static FileHandler txtFile;
	
	static public void setup(String logFileName) {
		//get global logger to configure it
		Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
		
		logger.setLevel(Level.INFO);
		
		try {
			txtFile = new FileHandler(logFileName);
			SimpleFormatter formater = new SimpleFormatter();
			txtFile.setFormatter(formater);
			logger.addHandler(txtFile);
			logger.setUseParentHandlers(false);
		} catch (SecurityException | IOException e) {
			e.printStackTrace();
		}
	} 
}
