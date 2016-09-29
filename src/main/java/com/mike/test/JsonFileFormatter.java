package com.mike.test;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;

public class JsonFileFormatter {
	private static final Logger LOG = Logger.getLogger(JsonFileFormatter.class.getName());
	private static final String PROPERTIES_FILE = "config.xml";
	private static final SimpleDateFormat SDF = new SimpleDateFormat("EEE MMM dd HH:mm:ss SSS z yyyy");
	private static final SimpleDateFormat TIMESTAMP = new SimpleDateFormat("yyyyMMddHHmmssSSS");
	
	Properties properties = new Properties();
	Calendar lastCheck;
	
	public JsonFileFormatter() {
		loadProperties();
		
		// check last update
		lastCheck = findLastUpdate();
		
		// find new files
		List<File> unformattedFiles = findUnformattedFiles();
		
		LOG.log(Level.INFO, "Found " + unformattedFiles.size() + " files to format.");
		
		// find new last update
		lastCheck = getLastFileStamp(unformattedFiles);
		
		// save the last update
		saveLastUpdate(lastCheck);
		
		// format new files
		formatFiles(unformattedFiles);
	}

	private void loadProperties() {
		if(!new File(PROPERTIES_FILE).exists()) {
			defaults();
			saveProperties();
		} else {
			try(FileInputStream fin = new FileInputStream(new File(PROPERTIES_FILE))) {
				properties.loadFromXML(fin);
			} catch (IOException e) {
				LOG.log(Level.SEVERE, "Couldn't load the properties file from " + new File(PROPERTIES_FILE).toString(), e);
			}
		}
	}
	
	private void saveProperties() {
		try (FileOutputStream fos = new FileOutputStream(new File(PROPERTIES_FILE))) {
			properties.storeToXML(fos, "Saved " + SDF.format(new Date()));
		} catch (FileNotFoundException e) {
			LOG.log(Level.SEVERE, "Couldn't find the file that I'm saving to? I don't think this should happen", e);
		} catch (IOException e) {
			LOG.log(Level.SEVERE, "Couldn't save the properties file to " + new File(PROPERTIES_FILE).toString(), e);
		}
	}

	private void defaults() {
		properties.put("lastUpdate", SDF.format(Calendar.getInstance().getTime()));
		properties.put("unformattedDirectory", "C:\\Temp\\data\\jboss\\aem\\inbound");
		properties.put("formattedDirectory", "C:\\Temp\\data\\jboss\\aem\\inboundFormatted");
	}

	private List<File> findUnformattedFiles() {
		File unformattedDirectory = new File(properties.getProperty("unformattedDirectory"));
		
		return Arrays.asList(unformattedDirectory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				if(pathname.lastModified() > lastCheck.getTimeInMillis()
				&& pathname.getName().endsWith(".json")) {
					return true;
				} else {
					return false;
				}
			}
		}));
	}


	private Calendar getLastFileStamp(List<File> unformattedFiles) {
		long maxTime = lastCheck.getTimeInMillis();
		for(File file : unformattedFiles) {
			if(file.lastModified() > maxTime) {
				maxTime = file.lastModified();
			}
		}
		Calendar last = Calendar.getInstance();
		last.setTimeInMillis(maxTime);
		return last;
	}

	private void formatFiles(List<File> unformattedFiles) {
		File formattedDirectory = new File(properties.getProperty("formattedDirectory"));
		if(!formattedDirectory.exists()) {
			formattedDirectory.mkdirs();
		}
		for(File file : unformattedFiles) {
			formatJsonFile(file, formattedDirectory);
		}
	}
	
	void formatJsonFile(File jsonFile, File directory) {
		try {
			JsonReader reader = Json.createReader(new FileReader(jsonFile));
			File newfile = getNewFilename(jsonFile, directory);
			JsonStructure st = reader.read();

			Map<String, Object> props = new HashMap<String, Object>();
			props.put(JsonGenerator.PRETTY_PRINTING, true);

			JsonWriter writ = Json.createWriterFactory(props).createWriter(
					new FileWriter(newfile));
			writ.write(st);
			writ.close();
		} catch (IOException e) {
			LOG.log(Level.SEVERE, "Couldn't format the file " + jsonFile, e);
		}
	}
	
	File getNewFilename(File jsonFile, File directory) {
		try(FileReader fr = new FileReader(jsonFile);
				JsonReader reader = Json.createReader(fr);) {
			String path = reader.readArray().getJsonObject(1).getString("X-AEM-PATH");
			System.out.println("Found path " + path);
			return Paths.get(directory.toString(), path.replace("\\", "-").replace("/", "-") + TIMESTAMP.format(new Date()) + ".json").toFile();
		} catch (Exception e) {
			return Paths.get(directory.toString(), jsonFile.getName() + "_FORMATTED").toFile();
		}
	}
	
	private Calendar findLastUpdate() {
		try {
			Calendar cal = Calendar.getInstance();
			cal.setTime(SDF.parse(properties.getProperty("lastUpdate")));
			return cal;
		} catch (ParseException e) {
			throw new RuntimeException("Couldn't parse the time string " + properties.getProperty("lastUpdate"), e);
		}
	}

	private void saveLastUpdate(Calendar lastCheck) {
		properties.put("lastUpdate", SDF.format(lastCheck.getTime()));
		saveProperties();
	}
}
