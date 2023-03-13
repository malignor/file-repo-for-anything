package qasts.autotest;

public class RemoteLoc {
    /*
     * This is merely a complex data type which describes locations in a given test environment.
     * It has no methods, only data members.
     * Any given environment will have a discrete list of these, likely less than 30 locations.
     * As such, the risk of memory issues is approximately null.
     */
    public String localIP;
    public String localName;
    public String login;
    public String password;
    public String writablePath;
    public String testRunnerPath;
    public String testProjectPath;

    public RemoteLoc(String localIP, String localName, String login, String password, String writablePath, String testRunnerPath, String testProjectPath) {
        this.localIP = localIP;
        this.localName = localName;
        this.login = login;
        this.password = password;
        this.writablePath = writablePath;
        this.testRunnerPath = testRunnerPath;
        this.testProjectPath = testProjectPath;
    }

    public RemoteLoc() {
        this.localIP = "";
        this.localName = "";
        this.login = "";
        this.password = "";
        this.writablePath = "";
        this.testRunnerPath = "";
        this.testProjectPath = "";
    }

    public RemoteLoc(String localName) {
        this.localName = localName;
    }

    public RemoteLoc(RemoteLoc copyLoc) {
        this(copyLoc.localIP, copyLoc.localName, copyLoc.login, copyLoc.password, copyLoc.writablePath, copyLoc.testRunnerPath, copyLoc.testProjectPath);
    }
}
