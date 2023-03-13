package qasts.autotest;

import java.io.*;
import java.util.Calendar;
import java.nio.file.*;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class MasterRobot {
    public RemoteLoc[] agents;
    public RemoteLoc resultsRepo;
    public RemoteLoc projectRepo;

    public int batchJob(int remoteAgent, String projectFile, String testSuite, String testCase, Calendar dateTime){
        /*
         *   Creates an EXECUTE batch file to execute the test suite
         *   Creates an ORCHESTRATE batch file to…
         *       (if later) Schedule the task: execute the EXECUTE batch file.
         *       (if now or earlier) execute the EXECUTE batch file now.
         *       Schedule the task: delete the EXECUTE batch file 168 hours after execution.
         *       Schedule the task: delete the ORCHESTRATE batch file in 15 minutes.
         *   Moves both batch files to the remote agent.
         *   Executes the ORCHESTRATE batch.
         */
        if (agents.length < 1 || remoteAgent >= agents.length || remoteAgent < 0) {
            // non-starter
            return -1;
        }
        String sExecFilename = buildBatchExecuter(agents[remoteAgent].testRunnerPath, projectFile, testSuite, testCase);
        String sOrchFilename = buildBatchOrchestrater(sExecFilename, remoteAgent, dateTime);
        try {
            Path pExecBat = new File(sExecFilename).toPath();
            Path pOrchBat = new File(sOrchFilename).toPath();
            Path pDestination = new File(localToNetwork(agents[remoteAgent].writablePath, agents[remoteAgent].localName)).toPath();

            Files.copy(pExecBat, pDestination, REPLACE_EXISTING);
            Files.copy(pOrchBat, pDestination, REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Caught I/O exception: " + e.getMessage());
            return -2;
        }
        /* Sample psexec command:
         *        psexec -u subscribers\qaservice.soapui01 -p Welcome01 \\ONTINJV2SAV004 -d cmd.exe /c "C:\TestingTools\TestRobotAutomation\strSampleCase2.bat"
         */
        execCMD("psexec -u " + agents[remoteAgent].login + " -p " + agents[remoteAgent].password + " \\\\" + agents[remoteAgent].localName + " -d cmd.exe /c \"" + agents[remoteAgent].writablePath + "\"");
        return 0;
    }

    public int batchJob(int remoteAgent, String projectFile, String testSuite, String testCase){
        // simple variant of the original batchJob method
        Calendar curTime = Calendar.getInstance();
        return batchJob(remoteAgent, projectFile, testSuite, testCase, curTime);
    }

    public int batchJob(int remoteAgent, String projectFile, String testSuite){
        // simple variant of the original batchJob method
        Calendar curTime = Calendar.getInstance();
        return batchJob(remoteAgent, projectFile, testSuite, "", curTime);
    }

    public int batchJob(int remoteAgent, String projectFile, String testSuite, Calendar dateTime){
        // simple variant of the original batchJob method
        return batchJob(remoteAgent, projectFile, testSuite, "", dateTime);
    }

    private String buildBatchExecuter(String sTRPath, String projectFile, String testSuite, String testCase){
        // returns the file path and name. Name is autogenerated.
        // Creates a batch file in the default directory on the agent, in which a testRunner command is run.

        String sBatchFilename = "QASTS_BBEx" + generateUniqueTag() + ".bat";
        String sPathTR = sTRPath;
        String sPathRe = resultsRepo.writablePath;
        String sPathPr = projectRepo.testProjectPath;
        String sCaseSelect = " -c " + testCase;

        if (testCase.equals(null) || testCase.isEmpty()){sCaseSelect = "";}
        /* Sample testRunner command:
         *        "c:\TestingTools\ReadyAPI-1.9.0\bin\testrunner.bat" -f \\QA-DTE-LAUNCHPA\QA_Share\ExecLogs\ -s TestRobot -c robotSampleCase-1 “\\QA-DTE-LAUNCHPA\QA_Share\Projects\TomDalsin\IF-soapui-project_VTE_TomsPoC.xml”
         */
        String batCommand = "\"" + sPathTR + "\" -f " + sPathRe + " -s " + testSuite + sCaseSelect + "\"" + sPathPr + "\\" + projectFile + "\"";

        BufferedWriter bwWriter = null;
        try {
            File locBat = new File(sBatchFilename);
            bwWriter = new BufferedWriter(new FileWriter(locBat));
            bwWriter.write(batCommand);
        } catch (IOException e1) {
            System.err.println("Caught I/O exception: " + e1.getMessage());
            return e1.toString();
        } finally {
            try {
                bwWriter.close();
            } catch (IOException e2) {
                System.err.println("Caught I/O exception: " + e2.getMessage());
                return e2.toString();
            }
        }
        return sBatchFilename;
    }

    private String buildBatchOrchestrater(String batchExecName, int localAgent, Calendar deadline){
        /* returns the file path and name. Name is autogenerated.
         * creates a batch file in the default directory, in which various tasks are scheduled and/or executed.
         *     if the deadline is not a valid future timestamp, it executes the batchExecName.
         *     otherwise it schedules the batchExecName for some time in the future.
         * Also schedules for the batch batchExecName file, and this file, to be deleted in the future.
         */

        int iDelaySelfCleanDays = 14;
        int iDelayBatchCleanDays = 90;
        int iMinFutureRunMins = 1;

        String sMyTag = generateUniqueTag();
        String sBatchFilename = "QASTS_BBOr" + sMyTag + ".bat";
        /* Sample Schtasks command:
         *        SCHTASKS /Create /SC ONCE /TN PoC-HV /TR "C:\Projects\HulloVurld.bat" /ST 00:01 /DU 01:00 /K /SD 10/31/2018 /V1 /RU "System"
         */

        // FIRST make the line where the scheduling batch is deleted. Currently set to self-destruct in 14 days.
        Calendar cCleanSelf = deadline;
        cCleanSelf.add(cCleanSelf.DATE,iDelaySelfCleanDays);
        String sCleanup = "" + cCleanSelf.MONTH + "//" + cCleanSelf.DAY_OF_MONTH + "//" + cCleanSelf.YEAR;
        String batCommandCleanSelf = "SCHTASKS /Create /SC ONCE /TN CLEAN" + sMyTag + " /TR DEL \"" + agents[localAgent].writablePath + "\\" + sBatchFilename + "\" /ST 00:01 /DU 01:00 /K /SD " + sCleanup + " /V1 /RU \"System\"";

        // NEXT make the line which schedules the execution
        Calendar cCurrent = Calendar.getInstance();
        if ((cCurrent.getTimeInMillis() + (iMinFutureRunMins * 60000)) < deadline.getTimeInMillis()){
            // soonest a test can be scheduled is in 1 minute. If under 1 minute in future, set to 1 minute in future.
            cCurrent = deadline;
        }
        String sRunDate = "" + cCurrent.MONTH + "//" + cCurrent.DAY_OF_MONTH + "//" + cCurrent.YEAR;;
        String sRunTime = "" + cCurrent.HOUR + ":" + (cCurrent.MINUTE + iMinFutureRunMins);
        String batCommandBat = "SCHTASKS /Create /SC ONCE /TN RUNBAT" + sMyTag + " /TR \"" + agents[localAgent].writablePath + "\\" + batchExecName + "\" /ST " + sRunTime + " /DU 01:00 /K /SD " + sRunDate + " /V1 /RU \"System\"";

        // NEXT make the line which deletes the execution batch in 90 days
        Calendar cCleanBat = deadline;
        cCleanBat.add(cCleanBat.DATE,iDelayBatchCleanDays);
        String sCleanDate = "" + deadline.MONTH + "//" + deadline.DAY_OF_MONTH + "//" + deadline.YEAR;;
        String batCommandCleanBat = "SCHTASKS /Create /SC ONCE /TN CLEANBAT" + sMyTag + " /TR DEL \"" + agents[localAgent].writablePath + "\\" + batchExecName + "\" /ST 00:01 /DU 01:00 /K /SD " + sCleanDate + " /V1 /RU \"System\"";

        BufferedWriter bwWriter = null;
        try {
            File locBat = new File(sBatchFilename);
            bwWriter = new BufferedWriter(new FileWriter(locBat));
            bwWriter.write(batCommandBat + "\r\n" + batCommandCleanBat + "\r\n" + batCommandCleanSelf + "\r\n");
        } catch (IOException e1) {
            System.err.println("Caught I/O exception: " + e1.getMessage());
            return e1.toString();
        } finally {
            try {
                bwWriter.close();
            } catch (IOException e2) {
                System.err.println("Caught I/O exception: " + e2.getMessage());
                return e2.toString();
            }
        }
        return sBatchFilename;
    }

    private String generateUniqueTag(){
        Calendar ts = Calendar.getInstance();
        return Long.toHexString(ts.getTimeInMillis());
    }

    private int execCMD(String sCommand){
        try {
            // execute a command
            System.out.println("about to execute: " + sCommand);
            Runtime rtTest = Runtime.getRuntime();
            Process procTest = rtTest.exec(sCommand);
            BufferedReader br = new BufferedReader(new InputStreamReader(procTest.getInputStream()));
            String stdout = "";
            String str = "";
            while ((str = br.readLine()) != null) {
                stdout += str + "\r\n";
            }
            procTest.waitFor();
            int exitCode = procTest.exitValue();
            System.out.println(stdout);
            System.out.println("Command has been run. Exit code is " + exitCode);
        } catch (Exception ex){
            ex.printStackTrace();
        }
        return 0;
    }

    private String localToNetwork(String sPathLocal, String sMachineName){
        return "\\\\" + sMachineName + "\\" + sPathLocal.substring(0,1) + "$" + sPathLocal.substring(sPathLocal.length() - 2);
    }
}