package edu.jhu.cvrg.services.dataTransferService;

import java.util.Map;
import java.util.StringTokenizer;

import org.apache.axiom.om.OMElement;

import edu.jhu.cvrg.waveform.model.ApacheCommonsFtpWrapper;
import edu.jhu.cvrg.waveform.service.ServiceUtils;


public class DataTransfer {

	private TransferSvcUtils utils = new TransferSvcUtils();
	/** local filesystem's root directory for Globus Online (or ftp), <BR>e.g. /export/icmv058/cvrgftp **/
	private String localInputRoot = "/export/icmv058/cvrgftp";  
	private String localOutputRoot = "/export/icmv058/cvrgftp"; // may be the same as localInputRoot 
	private String localAnalysisInputRoot = "/export/icmv058/execute";  

	/** For testing of service.
	 * @return version number.
	 * @throws Exception
	 */
	public String getVersion() throws Exception{
		utils.debugPrintln("Running DataTransfer/getVersion() 1.0");
		return "<Version> 1.0</Version>";
	}

	/** Copies the (data)files from the specified repository(via ftp) to the analysis server's local filesystem.
	 *  
	 * @param param0 - the data transfer parameters contained in the OMElement node: fileCount, fileNameList, relativePath, ftpHost, ftpUser, ftpPassword, and verbose.
	 * @return - The list of transfered files using the local path relative to the localTranferRoot (ftp root).
	 * @throws Exception
	 */
	public org.apache.axiom.om.OMElement transferDataFilesToAnalysis(org.apache.axiom.om.OMElement param0) throws Exception {
		utils.debugPrintln("Running transferDataFilesToAnalysis()");
		
		String[] saFileNames = utils.parseTransferParameters(param0, "");
		
		ApacheCommonsFtpWrapper ftpClient = new ApacheCommonsFtpWrapper(utils.sFtpHost, utils.sFtpUser, utils.sFtpPassword);

		String sDir = "/" + utils.generateTimeStamp();

		utils.getFilesFromFTP(utils.sFTPRelativePath, saFileNames, localInputRoot + sDir, ftpClient);
		for(int f=0;f<saFileNames.length;f++){
			saFileNames[f] = sDir + "/" + saFileNames[f];
		}
		
		OMElement stageTransferredDataStatus = utils.buildOmeTransferToAnalysisReturn(saFileNames, "transferFilesToAnalysis");

		return stageTransferredDataStatus;
	}

	/** Copies the (data)files from the specified repository(via ftp) to the analysis server's local filesystem.
	 *  
	 * @param param0 - the data transfer parameters contained in the OMElement node: fileCount, fileNameList, relativePath, ftpHost, ftpUser, ftpPassword, and verbose.
	 * @return - The list of transfered files using the local path relative to the localTranferRoot (ftp root).
	 * @throws Exception
	 */
	public org.apache.axiom.om.OMElement copyDataFilesToAnalysis(org.apache.axiom.om.OMElement param0) throws Exception {
		utils.debugPrintln("Running copyDataFilesToAnalysis()");
		
		String[] asInputFileNames = utils.parseTransferParameters(param0, "");
		ApacheCommonsFtpWrapper ftpClient = new ApacheCommonsFtpWrapper(utils.sFtpHost, utils.sFtpUser, utils.sFtpPassword);
		
		String sDir = "/" + utils.generateTimeStamp();

		utils.getFilesFromFTP(utils.sFTPRelativePath, asInputFileNames, localInputRoot + sDir, ftpClient);
		for(int f=0;f<asInputFileNames.length;f++){
			asInputFileNames[f] = sDir + "/" + asInputFileNames[f];
		}
		
		// [optional] Moves files from FTP directory to analysis directory (e.g. for permissions isolation.)
		String[] asInputFileHandles = utils.moveFiles(asInputFileNames, localInputRoot, localAnalysisInputRoot);

		OMElement stageTransferredDataStatus = utils.buildOmeTransferToAnalysisReturn(asInputFileHandles, "transferFilesToAnalysis");

		return stageTransferredDataStatus;
	}


	/** Copies the (result)files from the analysis server's local filesystem(via ftp) to the specified repository.
	 * 
	 * @param param0 - the data transfer parameters contained in the OMElement node: fileCount, fileNameList, relativePath, ftpHost, ftpUser, ftpPassword, and verbose.
	 * @return - The list of transfered files using the local path relative to the localTranferRoot (ftp root).
	 * @throws Exception
	 */
	public org.apache.axiom.om.OMElement transferResultFilesFromAnalysis(org.apache.axiom.om.OMElement param0) throws Exception {
		utils.debugPrintln("Running transferResultFilesFromAnalysis()");

		String[] fileNames = utils.parseTransferParameters(param0, "");
		ApacheCommonsFtpWrapper ftpClient = new ApacheCommonsFtpWrapper(utils.sFtpHost, utils.sFtpUser, utils.sFtpPassword);


		utils.sendFilesToFTP(utils.sFTPRelativePath, fileNames, localOutputRoot, ftpClient);
		OMElement stageTransferredDataStatus = utils.buildOmeTransferToAnalysisReturn(fileNames, "transferResultFilesFromAnalysis");

		return stageTransferredDataStatus;
	}


	/** Copies the (result)files from the analysis server's local filesystem(via ftp) to the specified repository.
	 * 
	 * @param param0 - the data transfer parameters contained in the OMElement node: fileCount, fileNameList, relativePath, ftpHost, ftpUser, ftpPassword, and verbose.
	 * @return - The list of transfered files using the local path relative to the localTranferRoot (ftp root).
	 * @throws Exception
	 */
	public org.apache.axiom.om.OMElement copyResultFilesFromAnalysis(org.apache.axiom.om.OMElement param0) throws Exception {
		utils.debugPrintln("Running copyResultFilesFromAnalysis()");

		String[] asOutputFileNames = utils.parseTransferParameters(param0, "");
		ApacheCommonsFtpWrapper ftpClient = new ApacheCommonsFtpWrapper(utils.sFtpHost, utils.sFtpUser, utils.sFtpPassword);

		utils.sendFilesToFTP(utils.sFTPRelativePath, asOutputFileNames, localOutputRoot, ftpClient);
		OMElement stageTransferredDataStatus = utils.buildOmecopyResultFilesFromAnalysisReturn(asOutputFileNames, "copyResultFilesFromAnalysis");

		return stageTransferredDataStatus;
	}


	/** Deletes the temporary(data and result)files from the analysis server's local file system.
	 * 
	 * @param param0 - the data transfer parameters contained in the OMElement node: fileCount, fileNameList, relativePath, ftpHost, ftpUser, ftpPassword, and verbose.
	 * @return - The list of transfered files using the local path relative to the localTranferRoot (ftp root).
	 * @throws Exception
	 */
	public org.apache.axiom.om.OMElement deleteFilesFromAnalysis(org.apache.axiom.om.OMElement param0) throws Exception {
		utils.debugPrintln("Running deleteFilesFromAnalysis()");

		String[] asOutputFileHandles = utils.parseTransferParameters(param0, "");
		utils.deleteFiles(asOutputFileHandles, localAnalysisInputRoot);
		utils.deleteDirectory(utils.sFTPRelativePath, localInputRoot);
		utils.deleteDirectory(utils.sFTPRelativePath, localAnalysisInputRoot);
		
		return null;
	}
	
	
	
	public org.apache.axiom.om.OMElement receiveAnalysisTempFiles(org.apache.axiom.om.OMElement e) throws Exception {
		utils.debugPrintln("Running receiveAnalysisTempFiles()");

		boolean status = utils.storeLocalFiles(e);
		
		return utils.buildAnalysisReturn("receiveAnalysisTempFiles", status);
	}
	
	public org.apache.axiom.om.OMElement sendAnalysisResultFiles(org.apache.axiom.om.OMElement e) throws Exception {
		utils.debugPrintln("Running receiveAnalysisTempFiles()");

		Map<String, OMElement> params = ServiceUtils.extractParams(e);
		String jobId = params.get("jobID").getText();
		long groupId = Long.valueOf(params.get("groupID").getText());
		long folderId = Long.valueOf(params.get("folderID").getText());
		
		StringTokenizer strToken = new StringTokenizer(params.get("resultFileNames").getText(), "^");
		String[] fileNames = new String[strToken.countTokens()];
		for (int i = 0; i < fileNames.length; i++) {
			fileNames[i] = strToken.nextToken();
		}
		
		boolean status = utils.sendResultFiles(fileNames, groupId, folderId, jobId);
		
		return utils.buildAnalysisReturn("sendAnalysisResultFiles", status);
	}
	
	
	
}
