package qasts.autotest;

import java.io.*;
import java.util.Calendar;
import javax.json.*;

public class MasterRobotScripted {
    static MasterRobot monkeyDrone;

    public static void main(String[] args) throws IOException {
        //local
        //execCMD("cmd /c C:\\DRIVERS\\TomTemp\\Work\\hullovurld.bat");
        //network drive
        //execCMD("cmd /c \"\\\\ontcrpp5bfs0021.ssha.ca\\My_Docs02$\\tom.dalsin\\My Documents\\Requests\\2018-10-03-VTE-AutomationPhase1B\\DrawingBoard\\hullovurld.bat\"");
        defineEnvironment(args[0]);
        runTests(args[0]);
    }

    static int execCMD(String sCommand){
        try {
            // create a process and execute notepad.exe
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
            System.out.println("Batch file completed, exit code is " + exitCode);
        } catch (Exception ex){
            ex.printStackTrace();
        }
        return 0;
    }

    static int defineEnvironment(String sConfigFile) throws IOException {
        // inspiration from https://www.journaldev.com/2315/java-json-example

        // first make sure there is a file name; default is same as this jar
        String sMyConfigFile = sConfigFile;
        if (sConfigFile.isEmpty() || sConfigFile.equals(null)) {
            sMyConfigFile = MasterRobotScripted.class.getName();
        } else if (sConfigFile.substring(sConfigFile.length()-5).toUpperCase() != ".JSON") {
            sMyConfigFile = sMyConfigFile + ".JSON";
        }

        // now open the file and read it as JSON
        System.out.println("looking for " + sMyConfigFile);
        InputStream isCfgStream = new FileInputStream(sMyConfigFile);
        JsonReader jrConfigReader = Json.createReader(isCfgStream);

        JsonObject joRobotScript = jrConfigReader.readObject();

        // now close the reading streams
        jrConfigReader.close();
        isCfgStream.close();

        // populate the RemoteLocs in MasterRobot, henceforth known as the "monkeyDrone"
        JsonArray jaLocationList = joRobotScript.getJsonArray("locations");
        int locationCount = jaLocationList.size();
        int agentCount = locationCount-2;
        if (agentCount < 1){System.out.println("No agents! Location count = " + locationCount); return -1;}

        RemoteLoc[] monkeyTroop = new RemoteLoc[agentCount];
        int iTrooperNumber = 0;
        for (JsonValue jvLocation : jaLocationList){
            JsonObject joLocObj = jvLocation.asJsonObject();
            String sLocype = joLocObj.getString("type");
            if (sLocype.equals("agent")){
                monkeyTroop[iTrooperNumber].localIP = joLocObj.getString("ip");
                monkeyTroop[iTrooperNumber].localName = joLocObj.getString("name");
                monkeyTroop[iTrooperNumber].login = joLocObj.getString("login");
                monkeyTroop[iTrooperNumber].password = joLocObj.getString("password");
                monkeyTroop[iTrooperNumber].testRunnerPath = joLocObj.getString("testRunnerAt");
                iTrooperNumber++;
            } else if (sLocype.equals("results")){
                monkeyDrone.resultsRepo.localIP = joLocObj.getString("ip");
                monkeyDrone.resultsRepo.localName = joLocObj.getString("name");
                monkeyDrone.resultsRepo.login = joLocObj.getString("login");
                monkeyDrone.resultsRepo.password = joLocObj.getString("password");
                monkeyDrone.resultsRepo.writablePath = joLocObj.getString("writeTo");
            } else if (sLocype.equals("projects")) {
                monkeyDrone.projectRepo.localIP = joLocObj.getString("ip");
                monkeyDrone.projectRepo.localName = joLocObj.getString("name");
                monkeyDrone.projectRepo.login = joLocObj.getString("login");
                monkeyDrone.projectRepo.password = joLocObj.getString("password");
                monkeyDrone.projectRepo.testProjectPath = joLocObj.getString("projectsAt");
            } else {
                System.out.println("Invalid location type: " + sLocype); return -2;
            }
        }
        monkeyDrone.agents = monkeyTroop;
        return 0;
    }

    static int runTests(String sSchedulerFile) throws IOException {
        // inspiration from https://www.journaldev.com/2315/java-json-example

        // first make sure there is a file name; default is same as this jar
        String sMyConfigFile = sSchedulerFile;
        if (sSchedulerFile.isEmpty() || sSchedulerFile.equals(null)) {
            sMyConfigFile = MasterRobotScripted.class.getName();
        } else if (sSchedulerFile.substring(sSchedulerFile.length()-5).toUpperCase() != ".JSON") {
            sMyConfigFile = sMyConfigFile + ".JSON";
        }

        // now open the file and read it as JSON
        InputStream isSchStream = new FileInputStream(sMyConfigFile);
        JsonReader jrScheduleReader = Json.createReader(isSchStream);

        JsonObject joRobotScript = jrScheduleReader.readObject();

        // now close the reading streams
        jrScheduleReader.close();
        isSchStream.close();

        // run the tests with MasterRobot, henceforth known as the "monkeyDrone"
        JsonArray jaTestRunList = joRobotScript.getJsonArray("tests");
        int iAgentSelected;
        for (JsonValue jvTestRun : jaTestRunList){
            // in case of agent uncertainty, pick a random one ^_^
            iAgentSelected = new java.util.Random().nextInt(monkeyDrone.agents.length);

            // now to read the json.
            String sAgentID = jvTestRun.asJsonObject().getString("agent",""+iAgentSelected);
            String sProjectFile = jvTestRun.asJsonObject().getString("projectFile");
            String sTestSuite = jvTestRun.asJsonObject().getString("suite");
            String sTestCase = jvTestRun.asJsonObject().getString("case","*");
            String sTestTime = jvTestRun.asJsonObject().getString("when","now");

            // agent can be by IP or name or number. Figure it out to sort out where testRunner is.
            if (sAgentID.equals("0")) {iAgentSelected = 0;}
            else if (Integer.parseInt(sAgentID) > 0) {iAgentSelected = Integer.parseInt(sAgentID);}
            else if (sAgentID.split("[.]").length == 4){
                // find by IP
                System.out.println("find by IP = " + sAgentID);
                iAgentSelected = 0;
                for (RemoteLoc trooper : monkeyDrone.agents) {
                    if (trooper.localIP.equals(sAgentID)) {break;}
                    iAgentSelected++;
                }
            } else {
                // find by system name
                System.out.println("find by Name = " + sAgentID);
                iAgentSelected = 0;
                for (RemoteLoc trooper : monkeyDrone.agents) {
                    if (trooper.localName.equals(sAgentID)) {break;}
                    iAgentSelected++;
                }
            }

            // now to find the time in calendar format.
            Calendar execTime = Calendar.getInstance();
            if (sTestTime.equals("now")){
                System.out.println("Executing immediately");
            } else {
                int iExYear = Integer.parseInt(sTestTime.substring(0,3));
                int iExMonth = Integer.parseInt(sTestTime.substring(4,5));
                int iExDate = Integer.parseInt(sTestTime.substring(6,7));
                int iExHour = Integer.parseInt(sTestTime.substring(8,9));
                int iExMinute = Integer.parseInt(sTestTime.substring(10,11));
                execTime.set(iExYear,iExMonth,iExDate,iExHour,iExMinute);
            }

            //now that we've found the agent and time for the test, and we have the rest of the information, we can create the commands.
            if (sTestCase.equals("*")){
                if (sTestTime.equals("now")){
                    monkeyDrone.batchJob(iAgentSelected, sProjectFile, sTestSuite);
                } else {
                    monkeyDrone.batchJob(iAgentSelected, sProjectFile, sTestSuite, execTime);
                }
            } else if (sTestTime.equals("now")){
                monkeyDrone.batchJob(iAgentSelected, sProjectFile, sTestSuite, sTestCase);
            } else {
                monkeyDrone.batchJob(iAgentSelected, sProjectFile, sTestSuite, sTestCase, execTime);
            }
        }
        return 0;
    }
}
