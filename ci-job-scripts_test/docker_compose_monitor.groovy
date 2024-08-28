/**
 * 
 * This is script is used to catch docker-compose events
 * 
 * NOTE: When container dies a file "<container name>.die" is created into workspace folder
 * 
 * @author ebialan
 *         
 */

import static Env.*
import static Constant.*
import static Utils.*

import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.Future
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.util.*;
import jenkins.model.*;

import groovy.io.FileType
import groovy.json.JsonSlurper
import groovy.xml.MarkupBuilder

class Env {
    public final static String GIT_URL = System.getenv()['GIT_URL']
    public final static String WORKSPACE = System.getenv()['WORKSPACE']
    public final static String GERRIT_SSH = 'ssh -p 29418 gerrit.ericsson.se gerrit'
    public final static String BUILD_ID = System.getenv()['BUILD_ID']
    public final static String JOB_URL = System.getenv()['JOB_URL']
}


class Constant {
    public final static String LOG_SEPARATOR = "*************************************************************************************"
    public final static String LINE_SEPARATOR = System.getProperty("line.separator")
    public final static String ENV_FILE_PATH = "${WORKSPACE}/env.txt"
    public final static String JENKINS_HTML_INFO_ICONE = '<td><img width=\'48\' height=\'48\' src=\'https://fem108-eiffel004.lmera.ericsson.se:8443/jenkins//plugin/build-failure-analyzer/images/48x48/information.png\' style=\'margin-right:1em;\'></td>'
    public final static String INFO_HTML_MESSAGE_FORMAT = "<tr>${JENKINS_HTML_INFO_ICONE}<td style=\"vertical-align:middle\"><p><b>%s: </b>%s</p></td></tr>"
}

class Utils {
    public final static Logger LOGGER = new Logger("${WORKSPACE}/docker-compose-events.log", true)
    public final static FilenameFilter DIED_FILE_FILTER = [accept: { f, filename ->
            filename.endsWith(".die")
        }] as FilenameFilter
}

class Logger {
    def logFile = System.out;
    private final static String dateFormat = "[dd.MM.yyyy;HH:mm:ss.SSS]"
    private boolean printToConsole = false;


    public Logger () {
    }

    public Logger (String logFilePath) {
        logFile = new File(logFilePath)
    }


    public Logger (String logFilePath, boolean printToConsole) {
        logFile = new File(logFilePath)
        this.printToConsole = printToConsole
    }

    def methodMissing(String name, args) {
        def message = args[0]
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat)
        String date = formatter.format(new Date())
        if (printToConsole) {
            println "${date} ${message}"
        }
        switch (name.toLowerCase()) {
            case "console":
                println  "${date} ${message}"
                break
            case "trace":
                logFile << "${date} TRACE ${message}\n"
                break
            case "debug":
                logFile << "${date} DEBUG ${message}\n"
                break
            case "info":
                logFile << "${date} INFO  ${message}\n"
                break
            case "warn":
                logFile << "${date} WARN  ${message}\n"
                break
            case "error":
                logFile << "${date} ERROR ${message}\n"
                break
            default:
                throw new MissingMethodException(name, delegate, args)
        }
    }
}

if(!args) {
    throw new InterruptedException('Missed argument: docker-compose.yml folder must be provided!')
}
monitor(args[0])

def monitor(WORKDIRPATH) {
    def workspaceDir = new File(WORKSPACE)
    def workDir = new File(WORKDIRPATH)
    
    workspaceDir.listFiles(DIED_FILE_FILTER).each{ it.delete() }

    runBashCmd("docker-compose events --json", workDir,  {
        it.in.eachLine { event ->
            eventDetails = parseDockerComposeEvent(event)
            LOGGER.info eventDetails
            if(eventDetails.action == 'die'){
               new File("${WORKSPACE}/${eventDetails.name}.die").write("${eventDetails}${LINE_SEPARATOR}")
               String errorMessage = "Docker ${eventDetails.service}:${eventDetails.name} is died at ${eventDetails.time}."   
               writeEnvVariables(errorMessage)
               throw new InterruptedException(errorMessage)
            }
        }
        it.err.eachLine { LOGGER.error it }
    },{
        LOGGER.info 'Docker Compose Monitor has been ended.'
        if (it.exitValue()!=0) {
            //throw new InterruptedException(it.err.text)
        }
    }
    )
}

def parseDockerComposeEvent(event){
    def slurper = new JsonSlurper()
    def eventDetails = [:]
    def jsonResult = slurper.parseText(event)

    eventDetails << [service:jsonResult.service]
    eventDetails << [time:jsonResult.time]
    eventDetails << [action:jsonResult.action]
    eventDetails << [type:jsonResult.type]
    eventDetails << [id:jsonResult.id]
    eventDetails << [image:jsonResult.attributes.image]
    eventDetails << [name:jsonResult.attributes.name]

    return eventDetails
}

def writeEnvVariables(errorMessage='') {
    def envFile = new File(ENV_FILE_PATH)
    def env_variables = ''

    env_variables += "GERRIT_TEST_ERROR_MESSAGE=\"${errorMessage}\"" + LINE_SEPARATOR
    env_variables += "ERROR_HTML_MESSAGE=\"${(errorMessage ? sprintf(INFO_HTML_MESSAGE_FORMAT, 'ERROR MESSAGE', errorMessage) : '')}\"" + LINE_SEPARATOR
    envFile << env_variables
}

def runBashCmd(cmd, dir, duringExecution, afterExecution){
    return runBashCmd(cmd, dir, true, duringExecution, afterExecution)
}

def runBashCmd(String cmd, File dir, boolean wait, Closure duringExecution, Closure afterExecution){
    LOGGER.info ">>>> ${cmd}"
    def proc =  (["/bin/bash", "-c", cmd] as String[]).execute(null, dir)
    if(duringExecution != null){
        duringExecution(proc)
    }
    if(wait){
        proc.waitFor()
    }
    if(afterExecution != null){
        afterExecution(proc)
    }
    return proc
}
