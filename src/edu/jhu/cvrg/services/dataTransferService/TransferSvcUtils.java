package edu.jhu.cvrg.services.dataTransferService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.net.ftp.FTP;
import org.apache.log4j.Logger;

import edu.jhu.cvrg.waveform.model.ApacheCommonsFtpWrapper;
import edu.jhu.cvrg.waveform.service.ServiceUtils;


public class TransferSvcUtils {
	private OMFactory omFactory; 	 
	private OMNamespace omNs; 	 
	private OMElement omeReturn;
	private String errorMessage = "";
	public String sFtpHost,sFtpUser, sFtpPassword, sFTPRelativePath;

	/** uri parameter for OMNamespace.createOMNamespace() - the namespace URI; must not be null, <BR>e.g. http://www.cvrgrid.org/physionetAnalysisService/ **/
	private String sOMNameSpaceURI = "http://www.cvrgrid.org/physionetAnalysisService/";  
	
	/** prefix parameter for OMNamespace.createOMNamespace() - the prefix<BR>e.g. physionetAnalysisService **/
	private String sOMNameSpacePrefix =  "physionetAnalysisService";  
	
	private String sep = File.separator;
	public String sJobID;
	
	Logger log = Logger.getLogger(TransferSvcUtils.class);
	private static Map<String, String[]> tempFilesMap = new HashMap<String, String[]>();
	
	// folders to be created:
	private String publicRoot; //       = remoteFtpRoot + sep  + userId + sep + "public"; //  + sep + subjectId + sep + "input";
	private String input; //            = remoteFtpRoot + sep  + userId + sep + "public"  + sep + subjectId + sep + "input";
	private String privateInput; //     = remoteFtpRoot + sep  + userId + sep + "private" + sep + subjectId + sep + "input";
	private String privatechesnokov; // = remoteFtpRoot + sep  + userId + sep + "private" + sep + subjectId + sep + "output" + sep + "chesnokov";
	private String privateberger; //    = remoteFtpRoot + sep  + userId + sep + "private" + sep + subjectId + sep + "output" + sep + "berger";
	private String privateqrsscore; //  = remoteFtpRoot + sep  + userId + sep + "private" + sep + subjectId + sep + "output" + sep + "qrsscore";
			
	private String original; // = remoteFtpRoot + sep  + userId + sep + publicPrivate + subjectId + sep + "input" + sep + originalFileName;
	private String target; //   = remoteFtpRoot + sep  + userId + sep + publicPrivate + subjectId + sep + "input" + sep;

	/** Constructor
	 * @param verbose - if true, generate debugging messages to the console.
	 */
	public TransferSvcUtils(){
		debugPrintLocalln("Initializing brokerSvcUtils() in Verbose mode.");
	}
	
	/** Creates a unique string based on the current timestamp, plus a pseudorandom number between zero and 1000
	 * 	In the form YYYYyMMmDDdHH_MM_SSxRRRR
	 * @return - a mostly unique string
	 */
	public String generateTimeStamp() {		
			Calendar now = Calendar.getInstance();
			int year = now.get(Calendar.YEAR);
			int month = now.get(Calendar.MONTH) + 1;
			int day = now.get(Calendar.DATE);
			int hour = now.get(Calendar.HOUR_OF_DAY); // 24 hour format
			int minute = now.get(Calendar.MINUTE);
			int second = now.get(Calendar.SECOND);
			
			String date = new Integer(year).toString() + "y";
			
			if(month<10)date = date + "0"; // zero padding single digit months to aid sorting.
			date = date + new Integer(month).toString() + "m"; 

			if(day<10)date = date + "0"; // zero padding to aid sorting.
			date = date + new Integer(day).toString() + "d";

			if(hour<10)date = date + "0"; // zero padding to aid sorting.
			date = date + new Integer(hour).toString() + "_"; 

			if(minute<10)date = date + "0"; // zero padding to aid sorting.
			date = date + new Integer(minute).toString() + "_"; 

			if(second<10)date = date + "0"; // zero padding to aid sorting.
//			date = date + new Integer(second).toString() + "|";
			date = date + new Integer(second).toString() + "x";
			
			// add the random number just to be sure we avoid name collisions
			Random rand = new Random();
			date = date + rand.nextInt(1000);
			return date;
		}

	/** Make final destination directories and transfer the file into it.<br>
	 * Parses userId, subjectId and originalFileName from fileName.<br>
	 * <br>
	 * Six created directories:<br>
	 * <i>
	 *  remoteFtpRoot/userId/public/subjectId/input<br>
	 *  remoteFtpRoot/userId/private/subjectId/input<br>
	 *  <br>
	 *  remoteFtpRoot/userId/public/subjectId/output/chesnokov<br>
	 *  remoteFtpRoot/userId/private/subjectId/output/chesnokov<br>
	 *  <br>
	 *  remoteFtpRoot/userId/public/subjectId/output/berger<br>
	 *  remoteFtpRoot/userId/private/subjectId/output/berger<br>
	 *  <br>
	 *  </i>
	 *  The original filename is restored, and the directory path is:<br>
	 *  <i>remoteFtpRoot/userId/public/subjectId/input<br>
	 *    or<br>
	 *  remoteFtpRoot/userId/private/subjectId/input</i><br>
	 *  <br>
	 * If the file exists there, is it replaced.<br>
	 *
	 * @param parentFolder
	 * @param fileName - of local file to be moved to ftp server, contains other info so it must be in the form: userId_subjectId_originalFileName.ext
	 * @param ftpHost
	 * @param ftpUser
	 * @param ftpPassword
	 * @param remoteFtpRoot - root directory on the ftp server, final directories will be built on this.
	 * @param isPublic
	 * @return true on successful copying
	 * @throws IOException 
	 */
	public boolean routeToFolder(String parentFolder, String fileName,
			String ftpHost, String ftpUser, String ftpPassword, String remoteFtpRoot, 
			boolean isPublic) throws IOException {
		boolean success = true;
		
		// parse the temporary file name.
		StringTokenizer tokenizer = new StringTokenizer(fileName, "_");
		String userId = tokenizer.nextToken();
		String subjectId = tokenizer.nextToken().toLowerCase();
		String originalFileName = tokenizer.nextToken("").substring(1); 
		
		success = routeToFolder(parentFolder, fileName, userId, subjectId, originalFileName,
				ftpHost, ftpUser, ftpPassword, remoteFtpRoot,
				isPublic);
	    
	    return success;
	}
	
	/** Make final destination directories and transfer the file into it.<br>
	 * Parses userId, subjectId and originalFileName from fileName.<br>
	 * <br>
	 * Six created directories:<br>
	 * <i>
	 *  remoteFtpRoot/userId/public/subjectId/input<br>
	 *  remoteFtpRoot/userId/private/subjectId/input<br>
	 *  <br>
	 *  remoteFtpRoot/userId/public/subjectId/output/chesnokov<br>
	 *  remoteFtpRoot/userId/private/subjectId/output/chesnokov<br>
	 *  <br>
	 *  remoteFtpRoot/userId/public/subjectId/output/berger<br>
	 *  remoteFtpRoot/userId/private/subjectId/output/berger<br>
	 *  <br>
	 *  </i>
	 *  The original filename is restored, and the directory path is:<br>
	 *  <i>remoteFtpRoot/userId/public/subjectId/input<br>
	 *    or<br>
	 *  remoteFtpRoot/userId/private/subjectId/input</i><br>
	 *  <br>
	 * If the file exists there, is it replaced.<br>
	 *
	 * @param parentFolder
	 * @param fileName - of local file to be moved to ftp server, contains other info so it must be in the form: userId_subjectId_originalFileName.ext
	 * @param userId 
	 * @param subjectId 
	 * @param originalFileName 
	 * @param ftpHost
	 * @param ftpUser
	 * @param ftpPassword
	 * @param remoteFtpRoot - root directory on the ftp server, final directories will be built on this.
	 * @param isPublic
	 * @return true on successful copying
	 * @throws IOException 
	 */
	public boolean routeToFolder(String parentFolder, String fileName, String userId, String subjectId, String originalFileName,
			String ftpHost, String ftpUser, String ftpPassword, String remoteFtpRoot, 
			boolean isPublic) throws IOException {
		boolean success = true;
		String publicPrivate = isPublic ?  "public": "private";
		
		ApacheCommonsFtpWrapper ftpWrap = new ApacheCommonsFtpWrapper(ftpHost, ftpUser, ftpPassword);
		
		createDirectoryNames(remoteFtpRoot, userId, subjectId, publicPrivate, originalFileName);
		success = makeInOutDirectories(ftpWrap, originalFileName, fileName);

		try {
			String inputPath = isPublic ?  input: privateInput;
	        success =  moveFileFTP(ftpWrap, inputPath, parentFolder, originalFileName, fileName);
	    } catch (Exception ex) {
	        System.out.println("Failed to write file: " + originalFileName + " isPublic: " + isPublic);
	    	ex.printStackTrace();
	    	success = false;
	    }
	    
	    return success;
	}

	
	/** Transfer the file into the input folder.<br>
	 * Parses userId, subjectId and originalFileName from fileName.<br>
	 * <br>
 	 *  The original filename is restored, and the directory path is:<br>
	 *  <i>remoteFtpRoot/userId/public/subjectId/input<br>
	 *  or<br>
	 *  remoteFtpRoot/userId/private/subjectId/input</i><br>
	 *  <br>
	 * If the file exists there, is it replaced.<br>
	 *
	 * @param localInPath
	 * @param localInFilename - of local file to be moved to ftp server, contains other info so it must be in the form: userId_subjectId_originalFileName.ext
	 * @param ftpHost
	 * @param ftpUser
	 * @param ftpPassword
	 * @param remoteFtpRoot - root directory on the ftp server, final directories will be built on this.
	 * @param isPublic
	 * @throws IOException 
	 */
	public boolean routeToExistingFolder(String localInPath, String localInFilename,
			String ftpHost, String ftpUser, String ftpPassword, String remoteFtpRoot, 
			boolean isPublic) throws IOException {
		boolean success = true;
		String ftpOutFilename="", userId="",subjectId="";
		try {
			debugPrintLocalln("routeToExistingFolder: "); 
			debugPrintLocalln("# parentFolder: " + localInPath); 
			debugPrintLocalln("# fileName: " + localInFilename); 
			debugPrintLocalln("# remoteFtpRoot: " + remoteFtpRoot); 
			debugPrintLocalln("# isPublic: " + isPublic); 
			
			String publicPrivate = isPublic ?  "public": "private";
			debugPrintLocalln("## publicPrivate: " + publicPrivate);
	
			// parse the temporary file name.
			StringTokenizer tokenizer = new StringTokenizer(localInFilename, "_");
			userId = tokenizer.nextToken();
			subjectId = tokenizer.nextToken().toLowerCase();
			ftpOutFilename = tokenizer.nextToken("").substring(1); 
	
			debugPrintLocalln("## userId: " + userId); 
			debugPrintLocalln("## subjectId: " + subjectId); 
			debugPrintLocalln("## originalFileName: " + ftpOutFilename); 
			
			ApacheCommonsFtpWrapper ftpWrap = new ApacheCommonsFtpWrapper(ftpHost, ftpUser, ftpPassword);
			createDirectoryNames(remoteFtpRoot, userId, subjectId, publicPrivate, ftpOutFilename);
		
			String ftpOutPath = isPublic ?  input: privateInput;
			debugPrintLocalln("## inputPath: " + ftpOutPath); 
	        success =  moveFileFTP(ftpWrap, ftpOutPath, localInPath, ftpOutFilename, localInFilename);
	    } catch (Exception ex) {
	        System.out.println("Failed to write file: " + ftpOutFilename + " isPublic: " + isPublic);
	    	ex.printStackTrace();
	    	success = false;
	    }
	    
	    return success;
	}
	
	/** Copy the file into the input folder.<br>
	 * Does not delete the orignal file..<br>
	 * <br>
 	 *  The same filename is used at ftp destination, and the directory path is:<br>
	 *  <i>remoteFtpRoot/userId/public/subjectId/input<br>
	 *  or<br>
	 *  remoteFtpRoot/userId/private/subjectId/input</i><br>
	 *  <br>
	 * If the file exists there, is it replaced.<br>
	 *
	 * @param localInPath - complete local path the file is in.
	 * @param localInFilename - of local file to be copied to ftp server. File name only, no path fragments
	 * @param userId
	 * @param subjectId
	 * @param ftpHost
	 * @param ftpUser
	 * @param ftpPassword
	 * @param remoteFtpRoot - root directory on the ftp server, final directories will be built on this.
	 * @param isPublic - boolean 
	 * @return success/fail of copying.
	 * @throws IOException 
	 */
	public boolean routeToExistingFolder(String localInPath, String localInFilename, String userId, String subjectId, 
			String ftpHost, String ftpUser, String ftpPassword, String remoteFtpRoot, 
			boolean isPublic) throws IOException {
		boolean success = false;
		String ftpOutFilename="";

		try {
			debugPrintLocalln("routeToExistingFolder: "); 
			debugPrintLocalln("# localInPath: " + localInPath); 
			debugPrintLocalln("# localInFilename: " + localInFilename); 
			debugPrintLocalln("# userId: " + userId); 
			debugPrintLocalln("# subjectId: " + subjectId); 
			debugPrintLocalln("# remoteFtpRoot: " + remoteFtpRoot); 
			debugPrintLocalln("# isPublic: " + isPublic); 
			
			String publicPrivate = isPublic ?  "public": "private";
			debugPrintLocalln("## publicPrivate: " + publicPrivate);
	
			// output file name is now the same as the input filename, no parsing required.
			ftpOutFilename = localInFilename; 	
			debugPrintLocalln("## ftpOutFilename: " + ftpOutFilename); 
			
			ApacheCommonsFtpWrapper ftpWrap = new ApacheCommonsFtpWrapper(ftpHost, ftpUser, ftpPassword);
			createDirectoryNames(remoteFtpRoot, userId, subjectId, publicPrivate, ftpOutFilename);
		
			String ftpOutPath = isPublic ?  input: privateInput;
			debugPrintLocalln("## ftpOutPath: " + ftpOutPath); 
	        success =  moveFileFTP(ftpWrap, ftpOutPath, localInPath, ftpOutFilename, localInFilename);
	    } catch (Exception ex) {
	        System.out.println("Failed to write file: " + ftpOutFilename + " isPublic: " + isPublic);
	    	ex.printStackTrace();
	    	success = false;
	    }
	    
	    return success;
	}

	
	private void createDirectoryNames(String remoteFtpRoot, String userId, String subjectId, String publicPrivate, String originalFileName){
		debugPrintLocalln("createDirectoryNames()");
		publicRoot       = remoteFtpRoot + sep  + userId + sep + "public"; //  + sep + subjectId + sep + "input";
		input            = remoteFtpRoot + sep  + userId + sep + "public"  + sep + subjectId + sep + "input";
		privateInput     = remoteFtpRoot + sep  + userId + sep + "private" + sep + subjectId + sep + "input";
		privatechesnokov = remoteFtpRoot + sep  + userId + sep + "private" + sep + subjectId + sep + "output" + sep + "chesnokov";
		privateberger    = remoteFtpRoot + sep  + userId + sep + "private" + sep + subjectId + sep + "output" + sep + "berger";
		privateqrsscore  = remoteFtpRoot + sep  + userId + sep + "private" + sep + subjectId + sep + "output" + sep + "qrsscore";
				
		original = remoteFtpRoot + sep  + userId + sep + publicPrivate + subjectId + sep + "input" + sep + originalFileName;
		target   = remoteFtpRoot + sep  + userId + sep + publicPrivate + subjectId + sep + "input" + sep;

	}
	
	public boolean makeInOutDirectories(ApacheCommonsFtpWrapper ftpWrap, String originalFileName, String fileName) throws IOException {
		boolean success = true, isCreated=false;
		debugPrintLocalln("makeInOutDirectories()");
		
		String warning = "*** WARNING: could not create the new folder ";
		
		if(new File(original).exists()) {
			// file already exists, destroy from target area, prep for replacement
			debugPrintLocalln("File \"" + originalFileName + "\" already exists on ftp server, removing so it can be replaced with newer version");
			removeFromStagingArea(target, fileName);
		} else {
			try {// create input directories path.
				if(!(new File(publicRoot).exists())) { //make the public dir as a place keeper, not used at the moment. will be used when "make public" function is added.
					isCreated = ftpWrap.mkdirs(publicRoot);
					
					if(!isCreated) System.out.println(warning + publicRoot);
				}
				isCreated = ftpWrap.mkdirs(privateInput);
				if(!isCreated) { 
					System.out.println(warning + privateInput);
					success = false;
				}
			} catch(Exception ex) {
				ex.printStackTrace();
				success = false;
			}
			
			try {// create chesnokov directories path.
				isCreated = ftpWrap.mkdirs(privatechesnokov);
				if(!isCreated){
					System.out.println(warning + privatechesnokov);
					success = false;
				}
			} catch(Exception ex) {
				ex.printStackTrace();
				success = false;
			}
	
			try {// create berger directories path.
				isCreated = ftpWrap.mkdirs(privateberger);
				if(!isCreated){
					System.out.println(warning + privateberger);
					success = false;
				}
			} catch(Exception ex) {
				ex.printStackTrace();
				success = false;
			}
	
			try {// create QRS Score directories path.
				isCreated = ftpWrap.mkdirs(privateqrsscore);
				if(!isCreated){
					System.out.println(warning + privateqrsscore);
					success = false;
				}
			} catch(Exception ex) {
				ex.printStackTrace();
				success = false;
			}
			//*******************************************
			debugPrintLocalln("Directory creation completed.");
		}

		return success;
	}

	/** Copies the file to the FTP server, does not remove the original file.
	 * 
	 * @param ftpWrap
	 * @param ftpOutPath
	 * @param localInPath
	 * @param ftpOutFileName
	 * @param localInFilename
	 * @return true on successful coping
	 */
	public boolean moveFileFTP(ApacheCommonsFtpWrapper ftpWrap, String ftpOutPath, String localInPath, String ftpOutFileName, String localInFilename){
		boolean success = true; 
		debugPrintLocalln("moveFileFTP()");
		
		try {
	        ftpWrap.setFileType(FTP.BINARY_FILE_TYPE);
	        System.out.println("writing file " + localInPath + "/" + localInFilename + " to ftp as: " + ftpOutPath + "/" + ftpOutFileName);
	        success = ftpWrap.uploadFile(localInPath, localInFilename, ftpOutPath, ftpOutFileName);

	        if(success){
	        	debugPrintLocalln("...Done");
	        }else{
	        	debugPrintLocalln("...failed!");	        	
	        }
	    } catch (Exception ex) {
	    	debugPrintLocalln("Failed to write file: " + ftpOutFileName + " inputPath: " + ftpOutPath);
	    	ex.printStackTrace();
	    	success = false;
	    }
	    debugPrintLocalln("File move completed.");
		return success;
	}
		
		
	/** Deletes the file from the specified directory.
	 * 
	 * @param path - path to prepend, must include file separator (e.g. "/")
	 * @param file - file to check for, without path.
	 */
	public void removeFromStagingArea(String path, String file) {
		debugPrintLocalln("removeFromStagingArea()");
		try {
			new File(path + sep + file).delete();
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		debugPrintLocalln("Remove From Staging completed");
	}


	 /** Wrapper around the 3 common functions for adding a child element to a parent OMElement.
	  * 
	  * @param name - name/key of the child element
	  * @param value - value of the new element
	  * @param parent - OMElement to add the child to.
	  * @param factory - OMFactory
	  * @param dsNs - OMNamespace
	  */
	 private void addOMEChild(String name, String value, OMElement parent, OMFactory factory, OMNamespace dsNs){
		 OMElement child = factory.createOMElement(name, dsNs);
		 child.addChild(factory.createOMText(value));
		 parent.addChild(child);
	 }

	 
	 /** Set standard OME options and create a new ServiceClient, and creates the required EndpointReference.
	  * 
	  * @param brokerURL - URL of the service to send the request to.
	  * @return a ServiceClient instance.
	  */
	 public ServiceClient getSender(String brokerURL){
			EndpointReference targetEPR = new EndpointReference(brokerURL);
			return getSender(targetEPR, brokerURL);
	 }
	 
	 /** Set standard OME options and create a new ServiceClient.
	  * 
	  * @param targetEPR
	  * @param brokerURL - URL of the service to send the request to.
	  * @return a ServiceClient instance.
	  */
	 private ServiceClient getSender(EndpointReference targetEPR, String brokerURL){
			Options options = new Options();
			options.setTo(targetEPR);
			options.setProperty(HTTPConstants.SO_TIMEOUT,new Integer(18000000));
			options.setProperty(HTTPConstants.CONNECTION_TIMEOUT,new Integer(18000000));
			options.setTransportInProtocol(Constants.TRANSPORT_HTTP);
			options.setAction(brokerURL);
			
			ServiceClient sender = null;
			try {
				sender = new ServiceClient();
			} catch (AxisFault e) {
				e.printStackTrace();
			}
			sender.setOptions(options);
			
			return sender;
	 }

	 
	/** Parses a service's incoming XML and builds a Map of all the parameters for easy access.
	 * @param param0 - OMElement representing XML with the incoming parameters.
	 */
	private Map<String, String> buildParamMap(OMElement param0){
		debugPrintln(" - buildParamMap()");
	
		String key,value;
		Map<String, String> paramMap = new HashMap<String, String>();
		try {
			@SuppressWarnings("unchecked")
			Iterator<OMElement> iterator = param0.getChildren();
			
			while(iterator.hasNext()) {
				OMElement param = iterator.next();
				key = param.getLocalName();
				value = param.getText();
				debugPrintln(" -- paramMap.put(k,v) " + key + ", " + value);
				paramMap.put(key,value);
			}
		} catch (Exception e) {
			e.printStackTrace();
			errorMessage = " -- buildParamMap() failed.";
			return null;
		}
		return paramMap;
	}
	
	 /** Parses the data transfer parameters contained in the OMElement and stores the results in public variables sJobID, sFtpHost, sFtpUser, sFtpPassword, and sFTPRelativePath;.
	  * 
	  * @param param0 - parameters as recieved by the web service.
	  * @param localInputRoot -- local Root path to be added to the relative paths of the filenames. 
	  * @return - array of input file names
	  */
	public String[] parseTransferParameters(OMElement param0, String localInputRoot){
		debugPrintln("parseTransferParameters()");
		String[] asInputFileNames = null;
		omFactory = OMAbstractFactory.getOMFactory(); 	 
		omNs = omFactory.createOMNamespace(sOMNameSpaceURI, sOMNameSpacePrefix); 	 
		
		try {
			// parse the input parameter's OMElement XML into a Map.
			Map<String, String> paramMap = buildParamMap(param0);
			// Assign specific input parameters to local variables.
			int iFileCount      = Integer.parseInt(paramMap.get("fileCount")); 
			String sFileNameList   = paramMap.get("fileNameList"); 
			sFTPRelativePath   = paramMap.get("relativePath"); 
			
			sFtpHost = paramMap.get("ftpHost"); 	 
			sFtpUser = paramMap.get("ftpUser"); 	 
			sFtpPassword = paramMap.get("ftpPassword"); 
			sJobID = paramMap.get("jobID");

			// parse the "^" delimited list of relative file paths/names into a String Array of absolute paths/names.
			asInputFileNames = getFileNameArray(iFileCount, sFileNameList, localInputRoot);

		} catch (Exception e) {
			e.printStackTrace();
			errorMessage = "genericWrapperType1 failed.";
		}
		
		return asInputFileNames;
	}
	
	
	/** Converts the "^" delimited list of relative file paths/names into a String Array of absolute paths/names.
	 * @param iFileCount - number of files which should be listed.
	 * @param fileNames - "^" separated list of file name to fetch, full paths.
	 * @param sLocalRootPrefix - local Root path to be added to the relative paths of the filenames.
	 * @return - String array containing the filenames, null if wrong number of names are found.
	 */
	private String[] getFileNameArray(int iFileCount, String sFileNames, String sLocalRootPrefix){
		String[] asFileNames= new String[0];
		String sTemp="";
		debugPrintln("getFileNameArray(): parsing file names from parameter string: ");
		debugPrintln(" >  " + sFileNames);

		try {
			Vector<String> vFileNames = new Vector<String>();
			StringTokenizer sTokNames = new StringTokenizer(sFileNames, "^");
			while (sTokNames.hasMoreTokens()) {
				sTemp = sTokNames.nextToken();
				debugPrintln(" - " + sTemp);
				sTemp = sLocalRootPrefix + sTemp;
				sTemp = sTemp.replace("//", "/"); //allows the passed in file names to be prefixed with "/" or not.
				vFileNames.add(sTemp);
			}			
			asFileNames = vFileNames.toArray(asFileNames);
		} catch (Exception e) {  
			e.printStackTrace();
			errorMessage = "getFileNameArray() failed.";
			return null;
		}
		if(iFileCount != asFileNames.length){
			debugPrintln("Wrong number of file names found. Expected:" + iFileCount + " Found:" + asFileNames.length);
			errorMessage = "Wrong number of file names found. Expected:" + iFileCount + " Found:" + asFileNames.length;
			return null;
		}
		debugPrintln(" - file names found: " + asFileNames.length);
		return asFileNames;
	}
	

	/** Builds the list of transfered files using the local path relative to the localTranferRoot (ftp root).
	 * 
	 * @param asReceivedFileHandleList - array of filenames as stored locally.
	 * @param sReturnOMEName - root Name to use when building the OMElement.
	 * @param localTranferRoot - absolute path of the localTranferRoot.
	 * @return - OMElement to be returned by the web service
	 */
	public OMElement buildOmeTransferToAnalysisReturn(String[] asReceivedFileHandleList, String sReturnOMEName){
		try{
			omeReturn = omFactory.createOMElement(sReturnOMEName, omNs); 
	
			addOMEChild("jobID", 
					sJobID, 
					omeReturn,omFactory,omNs);
			
			addOMEChild("filecount", 
					new Long(asReceivedFileHandleList.length).toString(), 
					omeReturn,omFactory,omNs);
			
			omeReturn.addChild(makeOutputOMElement(asReceivedFileHandleList, "filehandlelist", "filehandle"));

		} catch (Exception e) {
			errorMessage = "genericWrapperType1 failed.";
			log.error(errorMessage + " " + e.getMessage()); 
		}
		
		return omeReturn;
	}

	/** Builds the list of result files using the local path relative to the localTranferRoot (ftp root).
	 * 
	 * @param asReceivedFileHandleList - array of filenames as stored locally.
	 * @param sReturnOMEName - root Name to use when building the OMElement.
	 * @param localTranferRoot - absolute path of the localTranferRoot.
	 * @return - OMElement to be returned by the web service
	 */
	public OMElement buildOmecopyResultFilesFromAnalysisReturn(String[] asReceivedFileHandleList, String sReturnOMEName){
		try{
			omeReturn = omFactory.createOMElement(sReturnOMEName, omNs); 
	
			addOMEChild("jobID", 
					sJobID, 
					omeReturn,omFactory,omNs);
			
			addOMEChild("filecount", 
					new Long(asReceivedFileHandleList.length).toString(), 
					omeReturn,omFactory,omNs);
			
			omeReturn.addChild(makeOutputOMElement(asReceivedFileHandleList, "filenamelist", "filename"));

		} catch (Exception e) {
			e.printStackTrace();
			errorMessage = "buildOmecopyResultFilesFromAnalysisReturn failed.";
		}
		
		return omeReturn;
	}
	
	public OMElement buildAnalysisReturn(String returnOMEName, boolean status){
		OMElement omeReturn = null;
		OMFactory omFactory = OMAbstractFactory.getOMFactory(); 	 
		OMNamespace omNs = omFactory.createOMNamespace(sOMNameSpaceURI, sOMNameSpacePrefix);
		omeReturn = omFactory.createOMElement(returnOMEName, omNs);
		try{
			
			OMElement omeAnalysis = omFactory.createOMElement("status", omNs);
			omeAnalysis.setText(String.valueOf(status));
			
			omeReturn.addChild(omeAnalysis);
		} catch (Exception e) {
			errorMessage = returnOMEName + " failed. "+ e.getMessage();
			addOMEChild("status", errorMessage, omeReturn, omFactory, omNs);
			log.error(errorMessage);
		}
		return omeReturn;
	}
	
	/** Converts the array of output (relative) filenames to a single OMElement whose sub-elements are the filenames.
	 * 
	 * @param asFileNames - array of (relative) file path/name strings.
	 * @return - a single OMElement containing the path/names.
	 */
	private OMElement makeOutputOMElement(String[] asFileNames, String sParentOMEName, String sChildOMEName){
		debugPrintln("makeOutputOMElement()" + asFileNames.length + " file names");
		OMElement omeArray = null;
		if (asFileNames != null) {
			try {
				omeArray = omFactory.createOMElement(sParentOMEName, omNs); 
				
				for(int i=0; i<asFileNames.length;i++){
					addOMEChild(sChildOMEName,
							asFileNames[i], 
							omeArray,omFactory,omNs);					
				}
			}catch(Exception ex){
				ex.printStackTrace();
			}
		}
		return omeArray;
	}
	/** performs the core functions of transferDataFilesToAnalysis(), 
	 * Gets the listed files from the ftp server and puts them in a local directory
	 * 
	 * @param sourceDir - FTP source sub-directory from which to get the files.
	 * @param fileNameList - an array of file names to fetch, without paths.
	 * @param destinationDir - Temporary local directory that the files will be copied to. 
	 * @param ftpClient - ftp client (ApacheCommonsFtpWrapper) that is already logged in and ready to transfer files.
	 */
	public void getFilesFromFTP(String sourceDir, String[] fileNameList, String destinationDir, ApacheCommonsFtpWrapper ftpClient){
		debugPrintln(" > getFilesFromFTP(): Fetching " + fileNameList.length + " files from FTP directory: '" + sourceDir + "' and putting them in :" + destinationDir);

		String currentFile;
		try {
			debugPrintln("Creating directory: " + destinationDir);
			File f = new File(destinationDir); // create destination directory 	        
			if(f.mkdir()){
				debugPrintln("Creating directory succeeded.");
			}else{
				debugPrintln("Creating directory failed");
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		try {
			debugPrintln(" > FTP source directory:" + sourceDir);
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
			
			for(int i=0;i<fileNameList.length;i++){
				currentFile = fileNameList[i];
				debugPrintln("> File: " + currentFile);
				ftpClient.downloadFile(sourceDir, currentFile, destinationDir, currentFile);
			}
		} catch (FileNotFoundException e) {  
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}



	
	/** performs the core functions of transferDataFilesToAnalysis(), 
	 * Gets the listed files from the ftp server and puts them in a local directory
	 * 
	 * @param destinationFTPDir - relative directory on the remote machine to place the files in.
	 * @param fileNameList - String array of file path/name to fetch, relative paths.
	 * @param sourceLocalRoot - local ftp root directory that the files to be copied are in.
	 * @param ftpClient - ftp client that is already logged in and ready to transfer files.
	 */
	public void sendFilesToFTP(String destinationFTPDir, String[] fileNameList, String sourceLocalRoot, ApacheCommonsFtpWrapper ftpClient){
		debugPrintln("+++++++++++++++++++++++++++++++++++++++++++++++");
		debugPrintln(" + sendFilesToFTP(): sending " + fileNameList.length + " files from local root dir:" + sourceLocalRoot + " to ftp dir:" + destinationFTPDir);

		String currentFile;
		try {
			if(destinationFTPDir.startsWith("/")){
				destinationFTPDir = destinationFTPDir.substring(1);
			}
			debugPrintln(" + FTP destination directory: " + destinationFTPDir);
			boolean sft = ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
			debugPrintln(" + ftpClient.setFileType() returned: " + sft);
			String status = ftpClient.getStatus();
			debugPrintln(" + ftpClient.getStatus() returned: " + status);
			
			for(int i=0;i<fileNameList.length;i++){
				currentFile = fileNameList[i];
				int iNameIndex = currentFile.lastIndexOf("/") + 1;
				String ftpOutFilename = currentFile.substring(iNameIndex);
				String localRelativeDir = currentFile.substring(0, iNameIndex);
				debugPrintln(" ++ File " + i + ": " + ftpOutFilename);
				debugPrintln(" ++ relative dir: " + localRelativeDir);
				boolean cwd = ftpClient.changeWorkingDirectory(destinationFTPDir);
				debugPrintln(" ++ ftpClient.changeWorkingDirectory() returned: " + cwd);
				
				String localFile = sourceLocalRoot + currentFile;
				boolean ret = ftpClient.uploadFile(localFile, ftpOutFilename);
				debugPrintln(" ++ ftpClient.uploadFile() returned: " + ret);
				
				debugPrintln(" - - - - - - - - - - - - - ");
			}
		} catch (FileNotFoundException e) {  
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		debugPrintln("+++++++++++++++++++++++++++++++++++++++++++++++");
	}

	
	/** Moves the files listed in the array from the source root directory to the destination root directory.
	 * 
	 * @param asFileNames - array of full file path/name strings.
	 * @param sSourceRoot - root directory to move the files from.
	 * @param sDestinationRoot - root directory to move the files to.
	 * @return - array of the new full file path/name strings.
	 */
	public String[] moveFiles(String[] asFileNames, String sSourceRoot, String sDestinationRoot){
		debugPrintln("moveFiles() from: '" + sSourceRoot + "' to: '" + sDestinationRoot + "'");
		if (asFileNames != null) {
			int iMovedCount=0;
			String sSource="", sDestination = "", sFileName="", sSubDirectory="";
		
			try {
				if(sSourceRoot.compareTo(sDestinationRoot) == 0){ // nop if source and destination are identical.
					debugPrintln(" - Source and Destination are identical, no moving needed.");
				}else{
					for(int i=0;i<asFileNames.length;i++){
						int pos = asFileNames[i].lastIndexOf(sep);
						sFileName = asFileNames[i].substring(pos+1);;
						sSubDirectory = asFileNames[i].substring(0, pos+1);
						sSource       = sSourceRoot +      sep + sSubDirectory + sep + sFileName;
						sDestination  = sDestinationRoot + sep + sSubDirectory + sep + sFileName;
						
						if(!(new File(sDestinationRoot + sep + sSubDirectory).exists())) { //make the public dir as a place keeper, not used at the moment. will be used when "make public" function is added.
							debugPrintln("moveFiles(" + sDestinationRoot + sep + sSubDirectory + ");");
							// Create one directory
							boolean isCreated = (
									new File(sDestinationRoot + sep + sSubDirectory)).mkdir();
							if (isCreated) {
								debugPrintln("Directory created: '" 
										+ sDestinationRoot + sep + sSubDirectory + "' ");
							}else{
								debugPrintln("Directory create FAILED: '" 
										+ sDestinationRoot + sep + sSubDirectory + "' ");
							}
						}
						
						File fSource = new File(sSource);						
						boolean bSuccess = fSource.renameTo(new File(sDestination));
						debugPrintln(" - rename: '" + sSource + "' to: '" + sDestination+ "' - success: '" + bSuccess + "'");
						if(bSuccess) iMovedCount++;
					}
					if(iMovedCount != asFileNames.length){
						errorMessage += "moveFiles() failed. " + iMovedCount + " of " + asFileNames.length + " moved successfully.";
						debugPrintln(errorMessage);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				errorMessage += "moveFiles() failed.";
				return null;
			}
	    }
		return asFileNames;		
	}


	/** Deletes the files listed in the array from the root directory.
	 * 
	 * @param asFileNames - array of full file path/name strings.
	 * @param sRoot - root directory to move the files from.
	 * @return - array of the new full file path/name strings.
	 */
	public void deleteFiles(String[] asFileNames, String sRoot){
		debugPrintln("deleteFiles() from: '" + sRoot);
		if (asFileNames != null) {
			String sPathFile="";
		
			try {
				for(int i=0;i<asFileNames.length;i++){
					sPathFile = sRoot + sep + asFileNames[i];
					
					File fPathFile = new File(sPathFile);	
					fPathFile.delete();
				}
			} catch (Exception e) {
				e.printStackTrace();
				errorMessage += "deleteFiles() failed.";
			}
	    }
	}

	/** Deletes the files listed in the array from the root directory.
	 * 
	 * @param asFileNames - array of full file path/name strings.
	 * @param sRoot - root directory to move the files from.
	 * @return - array of the new full file path/name strings.
	 */
	public void deleteDirectory(String sFileSubDir, String sRoot){
		debugPrintln("deleteDirectory() " + sFileSubDir + " from: '" + sRoot);
		if (sFileSubDir != null) {
			String sPathFile="";
		
			try {
				sPathFile = sRoot + sep + sFileSubDir;
				
				File fPathFile = new File(sPathFile);	
				fPathFile.delete();
			} catch (Exception e) {
				e.printStackTrace();
				errorMessage += "deleteFiles() failed.";
			}
	    }
	}


	protected boolean storeLocalFiles(org.apache.axiom.om.OMElement e) {
		
		boolean ret = false;
		
		Map<String, OMElement> params = ServiceUtils.extractParams(e);
		Map<String, OMElement> jobs = ServiceUtils.extractParams(params.get("jobs"));
		if(jobs != null){
			
			for (String jobId : jobs.keySet()) {
			
				String inputPath = ServiceUtils.SERVER_TEMP_ANALYSIS_FOLDER + File.separator + jobId;
				
				StringTokenizer strToken = new StringTokenizer(jobs.get(jobId).getText(), "^");
				String[] fileNames = new String[strToken.countTokens()];
				for (int i = 0; i < fileNames.length; i++) {
					String name = strToken.nextToken();
					fileNames[i] = inputPath + File.separator + name;
					
					ServiceUtils.createTempLocalFile(params, name, inputPath, name);
					
					ret = new File(fileNames[i]).exists();
					
					if(!ret){
						return ret;
					}
				}
				
				tempFilesMap.put(jobId, fileNames);
			}
		}
		
		return ret;
	}
	
	protected boolean sendResultFiles(String[] resultFileNames, long groupId, long folderId, String jobId){
		boolean success = false;
		String errorMessage = "";
		debugPrintln("moveFiles() from: local to: liferay");
		if (resultFileNames != null) {
			int iMovedCount=0;
			try {
				for(int i=0;i<resultFileNames.length;i++){
					
					File orign = new File(resultFileNames[i]);
					FileInputStream fis = new FileInputStream(orign);
					
					String path = ServiceUtils.extractPath(resultFileNames[i]);
					
					ServiceUtils.sendToLiferay(groupId, folderId, path, orign.getName(), orign.length(), fis);
					
					iMovedCount++;
				}
				
				success = iMovedCount == resultFileNames.length;
				if(!success){
					errorMessage += "sendResultFiles() failed. " + iMovedCount + " of " + resultFileNames.length + " moved successfully.";
					log.error(errorMessage);
				}
				
			} catch (Exception e) {
				errorMessage += "sendResultFiles() failed.";
				log.error(errorMessage + " " + e.getMessage());
			}finally{
				
				String[] tempFileNames = tempFilesMap.get(jobId);
				//Delete temporary files
				if(tempFileNames != null){
					for (String fileName : tempFileNames) {
						ServiceUtils.deleteFile(fileName);
					}
					
					String jobFolder = ServiceUtils.extractPath(tempFileNames[0]);
					ServiceUtils.deleteFile(jobFolder);
					tempFilesMap.remove(jobId);
				}
			}
	    }
		return success;		
	}
	
	public void debugPrintLocalln(String text){
		debugPrintln("+ bSvcUtils: " + text);
	}
	public void debugPrintln(String text){
		log.debug("+ " + text);
	}
	public void debugPrint(String text){
		log.debug("+ " + text);
	}
}
