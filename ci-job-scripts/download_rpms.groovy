/**
 *  rpms dowloader
 * 
 * This is script is responsible to download the list of rpms provided.
 * 
 * @author ebialan
 *         
 */

import static Env.*
import static Constant.*

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

import groovy.json.JsonSlurper

class Env {
    public final static String DOWNLOAD_RPMS_THREAD_POOL_SIZE = System.getenv()['DOWNLOAD_RPMS_THREAD_POOL_SIZE']
    public final static String DOWNLOAD_RPMS_SNAPSHOT_MODEL_RPMS_DIR = System.getenv("SNAPSHOT_MODEL_RPMS_DIR")
    public final static String DOWNLOAD_RPMS_SNAPSHOT_CODE_RPMS_DIR = System.getenv("SNAPSHOT_CODE_RPMS_DIR")
    public final static String RPMS_LIST = System.getenv("DEPLOY_PACKAGE")
    public final static String WORKSPACE = System.getenv()['WORKSPACE']
    public final static String BUILD_ID = System.getenv()['BUILD_ID']
    public final static String JOB_URL = System.getenv()['JOB_URL']
}

class Constant {
    public final static String LOG_SEPARATOR = "*************************************************************************************"
    public final static int DEFAULT_THREAD_POOL_SIZE = 10
    public final static int THREAD_POOL_SIZE = DOWNLOAD_RPMS_THREAD_POOL_SIZE ? DOWNLOAD_RPMS_THREAD_POOL_SIZE.toInteger() : DEFAULT_THREAD_POOL_SIZE
    public final static String SNAPSHOT_MODEL_RPMS_DIR = DOWNLOAD_RPMS_SNAPSHOT_MODEL_RPMS_DIR
    public final static String SNAPSHOT_CODE_RPMS_DIR = DOWNLOAD_RPMS_SNAPSHOT_CODE_RPMS_DIR
    public final static String NEXUS_LINK = "https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus"
    public final static String NEXUS_PROJECT_PATH = "${NEXUS_LINK}/content/repositories/releases"
    public final static String NEXUS_GAV_QUERY= "${NEXUS_LINK}/service/local/lucene/search?_dc=1476866661785&collapseresults=true"

    public final static String ENV_FILE_PATH = "${WORKSPACE}/env.txt"
    public final static String DEFAULT_WORK_DIR_PATH = "${WORKSPACE}/download-rpms"
    public final static Logger LOGGER = new Logger()
    public final static Collection<String> RPMS = RPMS_LIST == null ? [] :  RPMS_LIST.tokenize('@@')
    public final static Map<String, String> RPMS_INFO = RPMS.collectEntries { rpm -> def rpmTokens = rpm.split('::'); [(rpmTokens[0]):(rpmTokens[1])]}
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
        if (printToConsole) {
            println message
        }
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat)
        String date = formatter.format(new Date())
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

//MAIN
init()
show()
if (args.length < 1 || args[0] != 'show') {
    execute()
}


def show(){
    LOGGER.info LOG_SEPARATOR
    LOGGER.info "Rpms to be downloaded:"
    RPMS_INFO.each { name, version ->
        LOGGER.info sprintf("%-50s%-10s\n",name,version)
    }
}

def init() {
    def WORK_DIR = new File(DEFAULT_WORK_DIR_PATH)
    if(!WORK_DIR.exists()) { WORK_DIR.mkdirs() }
}

def execute() {
    def futures = [:]
    def taskResults = []
    def threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
    try {
        RPMS_INFO.each { artifact, version ->
            futures << [(artifact):threadPool.submit({->downloadRpm(artifact, version)} as Callable)]
        }
        LOGGER.info LOG_SEPARATOR
        futures.each { artifact, future ->
            def taskResult = future.get()
        }

        LOGGER.info LOG_SEPARATOR
        futures.each { artifact, future ->
            def taskResult = future.get()
            def timeTaken = taskResult.endTime - taskResult.startTime
            if(future.isDone()) {
                LOGGER.info "${artifact} downloaded in ${timeTaken} ms"
            }else if(future.isCancelled()){
                def errorMessage = "${artifact} download has been cancelled after ${timeTaken} ms."
                LOGGER.error errorMessage
                throw new InterruptedException(errorMessage)
            }
        }

        LOGGER.info LOG_SEPARATOR
        manageRpms()
    }finally {
        threadPool.shutdown()
    }
}

def manageRpms(workDirPath = DEFAULT_WORK_DIR_PATH, logger = LOGGER) {
    logger.info "Searching RPMs ..."
    def workDir = new File(workDirPath)
    def snapModelRpmsDirLocal = new File(SNAPSHOT_MODEL_RPMS_DIR)
    def snapCodeRpmsDirLocal = new File(SNAPSHOT_CODE_RPMS_DIR)
    //Ensure RPMs folder presence
    if(!snapModelRpmsDirLocal.exists()) { snapModelRpmsDirLocal.mkdirs() }
    if(!snapCodeRpmsDirLocal.exists()) { snapCodeRpmsDirLocal.mkdirs() }
    runBashCmd(logger, "find . -name '*.rpm'", workDir,  null, {
        if (it.exitValue()==0) {
            it.in.eachLine { line ->
                runBashCmd(logger, "rpm -qpl ${line} | grep -o -m 1 ERICmodeldeployment", workDir, null, {
                    if (it.exitValue()==0) {
                        logger.info "${line} is model rpm"
                        def rpmFile = new File(workDir, line)
                        rpmFile.renameTo(new File(snapModelRpmsDirLocal, rpmFile.getName()));
                    } else {
                        logger.info "${line} is code rpm"
                        def rpmFile = new File(workDir, line)
                        rpmFile.renameTo(new File(snapCodeRpmsDirLocal, rpmFile.getName()));
                    }
                })
            }
        } else {
            def errorMessage = "Failed to search rpms in ${workDirPath}"
            logger.error (errorMessage)
            it.in.eachLine { logger.error it }
            it.err.eachLine { logger.error it }
            throw new InterruptedException(errorMessage)
        }
    })

}

//This method must be thread safe
def downloadRpm(artifact, version){
    def taskResult = [startTime:System.currentTimeMillis()]
    if (!version.contains('http')) {
        downloadRpmFromNexus(artifact, version)
    } else {
        downloadFile(version)
    }
    taskResult << [endTime:System.currentTimeMillis()]
    return taskResult
}

//This method must be thread safe
def downloadRpmFromNexus(artifact, version, workDirPath = DEFAULT_WORK_DIR_PATH, logger = LOGGER) {
    def gav =  getGAV('', artifact, version)
    def RPM_FILE_NAME = "${gav.artifactId}-${gav.version}.rpm"
    def URL = "${NEXUS_PROJECT_PATH}/${gav.groupId.replaceAll('\\.','/')}/${gav.artifactId}/${gav.version}/${RPM_FILE_NAME}"
    downloadFile(URL, RPM_FILE_NAME)
}

//This method must be thread safe
def downloadFile(url, filename = '', workDirPath = DEFAULT_WORK_DIR_PATH, logger = LOGGER) {
    filename = filename ? filename : url.split('/').last()
    runBashCmd(logger, "curl -o ${filename} \"${url}\"", new File(workDirPath), {}, {
        if (it.exitValue()!=0) {
            def errorMessage = "${filename} download failed from ${URL}"
            logger.error errorMessage
            throw new InterruptedException(errorMessage)
        }
    })
}

//This method must be thread safe
def getGAV(groupId, artifactId, version, workDirPath = DEFAULT_WORK_DIR_PATH, logger = LOGGER){
    def GAV
    def slurper = new XmlSlurper()
    def GAVs = new HashSet<>()
    def groupIdQuery = groupId ? "&g=${groupId}" : ''
    def artifactIdQuery = artifactId ? "&a=${artifactId}" : ''
    def versionQuery = version && version != 'Latest' ? "&v=${version}" : ''
    def query = "${NEXUS_GAV_QUERY}${groupIdQuery}${artifactIdQuery}${versionQuery}"
    def text = ''
    runBashCmd(logger, "curl \"${query}\"", new File(workDirPath), {it.in.eachLine { text += "${it}\n"; }}, {
        if (it.exitValue()==0) {
            def xmlResult = slurper.parseText(text)
            def data = xmlResult.data.artifact
            data.each {
                GAVs <<   [groupId:it.groupId.toString(), artifactId:it.artifactId.toString(), version:it.latestRelease.toString()]
            }
            def sortedGavs = GAVs.sort { a, b ->
                def aTokens = a.version.split('\\.')
                def bTokens = b.version.split('\\.')
                aTokens[0] <=> bTokens[0] ?:  aTokens[1] <=> bTokens[1] ?: aTokens[2] <=> bTokens[2]
            }
            GAV = sortedGavs.last()
        } else {
            def errorMessage = "Nexus GAV query failed: ${query}"
            logger.error errorMessage
            throw new InterruptedException(errorMessage)
        }
    })
    return GAV
}

//This method must be thread safe
def runBashCmd(logger, cmd, dir, duringExecution, afterExecution){
    return runBashCmd(logger, cmd, dir, true, duringExecution, afterExecution)
}


//This method must be thread safe
def runBashCmd(Logger logger, String cmd, File dir, boolean wait, Closure duringExecution, Closure afterExecution){
    def logMessage = ">>>> ${cmd}"
    if (logger) {
        logger.info ''
        logger.info logMessage
    } else {
        LOGGER.console ''
        LOGGER.console logMessage
    }

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


