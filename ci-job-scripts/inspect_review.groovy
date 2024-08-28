/**
 * Inspect Reviews script
 * 
 * This is script is responsible to manage a multi repo build including vertical slice project
 * Available features:
 *   - manage the concurrency 
 *   - manage the dependencies among the commits
 *   - manage the projects RPMs 
 * 
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

import groovy.json.JsonSlurper

class Env {
    public final static String INSPECT_REVIEW_THREAD_POOL_SIZE = System.getenv()['INSPECT_REVIEW_THREAD_POOL_SIZE']
    public final static String INSPECT_REVIEW_BUILD_PROPERTIES = System.getenv()['INSPECT_REVIEW_BUILD_PROPERTIES']
    public final static String INSPECT_REVIEW_ADDITIONAL_BUILD_PROPERTIES = System.getenv()['INSPECT_REVIEW_ADDITIONAL_BUILD_PROPERTIES']
    public final static String INSPECT_REVIEW_IGNORE_TEST_REPO_RPMS = System.getenv()['INSPECT_REVIEW_IGNORE_TEST_REPO_RPMS']
    public final static String INSPECT_REVIEW_IGNORE_RPMS = System.getenv()['INSPECT_REVIEW_IGNORE_RPMS']
    public final static String INSPECT_REVIEW_SNAPSHOT_MODEL_RPMS_DIR = System.getenv("SNAPSHOT_MODEL_RPMS_DIR")
    public final static String INSPECT_REVIEW_SNAPSHOT_CODE_RPMS_DIR = System.getenv("SNAPSHOT_CODE_RPMS_DIR")
    public final static String TOPIC_USERS = System.getenv()['INSPECT_REVIEW_TOPIC_USERS']
    public final static String USER_CHANGE_NUMBERS = System.getenv()['INSPECT_REVIEW_USER_CHANGE_NUMBERS']
    public final static String GIT_URL = System.getenv()['GIT_URL']
    public final static String WORKSPACE = System.getenv()['WORKSPACE']
    public final static String GERRIT_SSH = 'ssh -p 29418 gerrit.ericsson.se gerrit'
    public final static String GERRIT_TOPIC = System.getenv()['GERRIT_TOPIC']
    public final static String GERRIT_MIRROR = System.getenv()['GERRIT_MIRROR']
    public final static String GERRIT_CENTRAL = System.getenv()['GERRIT_CENTRAL']
    public final static String GERRIT_REFSPEC = System.getenv()['GERRIT_REFSPEC']
    public final static String GERRIT_PROJECT = System.getenv()['GERRIT_PROJECT']
    public final static String GERRIT_PATCHSET_REVISION = System.getenv()['GERRIT_PATCHSET_REVISION']
    public final static String GERRIT_CHANGE_OWNER_NAME = System.getenv()['GERRIT_CHANGE_OWNER_NAME']
    public final static String GERRIT_CHANGE_NUMBER = System.getenv()['GERRIT_CHANGE_NUMBER']
    public final static String BUILD_ID = System.getenv()['BUILD_ID']
    public final static String JOB_URL = System.getenv()['JOB_URL']
    public final static String USER_TOPIC = System.getenv()['TOPIC']
}


class Constant {
    public final static String LOG_SEPARATOR = "*************************************************************************************"
    public final static String LINE_SEPARATOR = System.getProperty("line.separator")
    public final static String DEFAULT_SNAPSHOT_MODEL_RPMS_DIR = "${WORKSPACE}/rpms/models"
    public final static String DEFAULT_SNAPSHOT_CODE_RPMS_DIR = "${WORKSPACE}/rpms/code"
    public final static String ENV_FILE_PATH = "${WORKSPACE}/env.txt"
    public final static String UPDATED_RPMS_REPORT = "${WORKSPACE}/updated_rpms_report.log"
    public final static int DEFAULT_THREAD_POOL_SIZE = 1
    public final static int THREAD_POOL_SIZE = INSPECT_REVIEW_THREAD_POOL_SIZE ? INSPECT_REVIEW_THREAD_POOL_SIZE.toInteger() : DEFAULT_THREAD_POOL_SIZE
    public final static String TOPIC = USER_TOPIC ? USER_TOPIC : GERRIT_TOPIC
    public final static List<String> RPMS_TO_BE_IGNORED = INSPECT_REVIEW_IGNORE_RPMS ? INSPECT_REVIEW_IGNORE_RPMS.tokenize(',') : []
    public final static Boolean IGNORE_TEST_REPO_RPMS = INSPECT_REVIEW_IGNORE_TEST_REPO_RPMS ? INSPECT_REVIEW_IGNORE_TEST_REPO_RPMS.toBoolean() : false
    public final static String ADDITIONAL_BUILD_PROPERTIES = INSPECT_REVIEW_ADDITIONAL_BUILD_PROPERTIES ? INSPECT_REVIEW_ADDITIONAL_BUILD_PROPERTIES : ''
    public final static String BUILD_PROPERTIES = INSPECT_REVIEW_BUILD_PROPERTIES ? INSPECT_REVIEW_BUILD_PROPERTIES : ''
    public final static String GERRIT_SERVER = (GERRIT_MIRROR && GIT_URL.contains(GERRIT_MIRROR)) ? GERRIT_MIRROR : GERRIT_CENTRAL
    public final static String SNAPSHOT_MODEL_RPMS_DIR = INSPECT_REVIEW_SNAPSHOT_MODEL_RPMS_DIR ? INSPECT_REVIEW_SNAPSHOT_MODEL_RPMS_DIR :
    DEFAULT_ENV_VAR_WARNING(LOGGER, 'INSPECT_REVIEW_SNAPSHOT_MODEL_RPMS_DIR', DEFAULT_SNAPSHOT_MODEL_RPMS_DIR)
    public final static String SNAPSHOT_CODE_RPMS_DIR = INSPECT_REVIEW_SNAPSHOT_CODE_RPMS_DIR ? INSPECT_REVIEW_SNAPSHOT_CODE_RPMS_DIR :
    DEFAULT_ENV_VAR_WARNING(LOGGER, 'INSPECT_REVIEW_SNAPSHOT_CODE_RPMS_DIR', DEFAULT_SNAPSHOT_CODE_RPMS_DIR)
    public final static String VERTICAL_SLICE_REPO = (GERRIT_MIRROR && GIT_URL.contains(GERRIT_MIRROR)) ? GIT_URL.split("${GERRIT_MIRROR}/")[1] : GIT_URL.split("${GERRIT_CENTRAL}/")[1]
    public final static String VERTICAL_SLICE_PROJECT = GIT_URL.split('/').last()
    public final static Pattern RPM_PATTERN = Pattern.compile(".*(ERIC.*_CXP[\\d]++)-([\\d]+\\.[\\d]+\\.[\\d]+).*")
    public final static Pattern SNAPSHOT_RPM_PATTERN = Pattern.compile(".*(ERIC.*_CXP[\\d]++)-([\\d]+\\.[\\d]+\\.[\\d]+-SNAPSHOT).*")
    public final static String MVN_CMD = "mvn"
    public final static String MVN_VERSION_CMD = 'mvn -q -Dexec.executable=\"echo\" -Dexec.args=\'\${project.version}\' --non-recursive exec:exec'

    public final static String MVN_BUILD_DEFAULT_OPTIONS = '-U -B install -DskipTests -Dmaven.test.skip=true -Dcheckstyle.skip=true -Dcobertura.skip=true -Dmaven.artifact.threads=10'
    public final static String MVN_BUILD_BASE_OPTIONS = BUILD_PROPERTIES ? BUILD_PROPERTIES : MVN_BUILD_DEFAULT_OPTIONS;

    public final static String JENKINS_HTML_OPT_ICONE = '<td><img style=\'width: 48px; height: 48px; \' class=\'icon-orange-square icon-xlg\' src=\'/jenkins/static/3443ee7f/images/48x48/orange-square.png\'></td>'

    public final static String JENKINS_HTML_GIT_ICONE = '<td><img style=\'width: 48px; height: 48px; margin-right:1em;\' alt=\'\' src=\'/jenkins/static/3443ee7f/plugin/git/icons/git-48x48.png\'></td>'

    public final static String JENKINS_HTML_CLIPBOARD_ICONE = '<td><img src=\'/jenkins/static/7b4800b0/images/48x48/clipboard.png\' style=\'width: 48px; height: 48px; \' class=\'icon-clipboard icon-xlg\'></td>'

    public final static String JENKINS_HTML_INFO_ICONE = '<td><img width=\'48\' height=\'48\' src=\'https://fem108-eiffel004.lmera.ericsson.se:8443/jenkins//plugin/build-failure-analyzer/images/48x48/information.png\' style=\'margin-right:1em;\'></td>'

    public final static String INFO_HTML_MESSAGE_FORMAT = "<tr>${JENKINS_HTML_INFO_ICONE}<td style=\"vertical-align:middle\"><p><b>%s: </b>%s</p></td></tr>"

    public final static String GERRIT_TOPIC_QUERY_HTML_MESSAGE_FORMAT = "<tr>${JENKINS_HTML_GIT_ICONE}<td style=\"vertical-align:middle\"><p><b>GERRIT TOPIC QUERY: </b> <a href=\'https://gerrit.ericsson.se/#/q/topic:%s\'>https://gerrit.ericsson.se/#/q/topic:%s</a></p></td></tr>"

    public final static String GERRIT_USER_REVIEWS_HTML_MESSAGE_FORMAT = "<tr>${JENKINS_HTML_GIT_ICONE}<td style=\"vertical-align:middle\"><p><b>GERRIT REVIEWS: </b><br><ul>%s</ul></p></td></tr>"

    public final static String USER_PACKAGES_HTML_MESSAGE_FORMAT = "<tr>${JENKINS_HTML_CLIPBOARD_ICONE}<td style=\"vertical-align:middle\"><p><b>DOWNLOADED PACKAGES: </b><br><ul>%s</ul></p></td></tr>"

    public final static String UPDATED_PACKAGES_HTML_MESSAGE_FORMAT = "<tr>${JENKINS_HTML_CLIPBOARD_ICONE}<td style=\"vertical-align:middle\"><p><b>UPDATED %s PACKAGES: </b><br><ul>%s</ul></p></td></tr>"

    public final static String HTML_MESSAGE_LINK_LIST_FORMAT = '<li><a href=\'%s\'>%s</a></li>'
    public final static String HTML_MESSAGE_LIST_FORMAT = '<li>%s</li>'
}

class Utils {
    public final static Closure DUMP_COLLECTION = { logger, message, collection ->
        LOGGER.console LOG_SEPARATOR
        logger.console message
        collection.each{ logger.console " - ${it}" }
    }
    public final static Closure DUMP_MAP_OF_MAP = { logger, message,  map ->
        LOGGER.console LOG_SEPARATOR
        logger.console message
        map.each {
            logger.console " - ${it.key}"
            it.value.each{ logger.console "   - ${it.key}:${it.value}" }
        }
    }
    public final static Closure DEFAULT_ENV_VAR_WARNING = { LOGGER, envVar, defaultValue ->
        LOGGER.console "WARNING: ${envVar} env variable is not defined. Default: ${defaultValue}"
        return defaultValue
    }
    public final static FilenameFilter RPM_FILENAME_FILTER = [accept: { f, filename ->
            filename.endsWith("rpm")
        }] as FilenameFilter
    public final static Logger LOGGER = new Logger()
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


def startTime = System.currentTimeMillis()
init()
if (!args || args[0] != 'show') {
    manageCommits()
    writeEnvVariables()
    dumpResult()
}
def endTime = System.currentTimeMillis()
def timeTaken = endTime - startTime
LOGGER.console LOG_SEPARATOR
LOGGER.console "Script completed in ${timeTaken} ms"

def init() {
    user = GERRIT_CHANGE_OWNER_NAME ? GERRIT_CHANGE_OWNER_NAME : getJenkinsUser()
    generatedSystemProperties = new HashSet<String>()
    snapModelRpmsDir = new File(SNAPSHOT_MODEL_RPMS_DIR)
    snapCodeRpmsDir = new File(SNAPSHOT_CODE_RPMS_DIR)

    commitsType = getCommitType()
    commitsDetails = getCommitDetails()

    DUMP_MAP_OF_MAP(LOGGER, "Commits Details: ", commitsDetails)
}

def manageCommits() {
    if (commitsDetails) {
        LOGGER.console LOG_SEPARATOR
        validateDependencies(commitsDetails)
        def futures = [:]
        def threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
        def fetchProjectsResult = [:]
        def buildProjectsResult = [:]
        def orderedProjectsToBeManaged = sortProjects(commitsDetails)

        try {
            //Fetch all projects
            commitsDetails.each { repo, commitDetails ->
                def project = commitDetails.project
                LOGGER.console "Fetching ${project} ..."
                def logger = new Logger("${WORKSPACE}/${project}.log")
                futures << [(project):threadPool.submit({-> fetchProject(logger, repo, commitDetails)} as Callable)]
            }
            futures.each{
                fetchProjectsResult << it.value.get()
                LOGGER.console "${it.key} ready"
            }
            futures.clear()

            DUMP_MAP_OF_MAP(LOGGER, "Fetch Result: ", fetchProjectsResult)

            //Ensure RPMs folder presence
            if(!snapModelRpmsDir.exists()) { snapModelRpmsDir.mkdirs() }
            if(!snapCodeRpmsDir.exists()) { snapCodeRpmsDir.mkdirs() }

            //Build all projects
            LOGGER.console LOG_SEPARATOR
            while(true) {
                def futuresDone = 0
                futures.each{
                    def future = it.value
                    //Remove the project that has been taken in charge
                    orderedProjectsToBeManaged.remove(it.key)
                    if(future.isDone()) {
                        //Store the result for each managed project
                        buildProjectsResult << future.get()
                        //Mark that a future has finished its execution
                        ++futuresDone
                    }
                }

                //Remove the future for project that has been ended up
                buildProjectsResult.each {
                    futures.remove(it.key)
                }

                //There are still some future in progress and no one is completed yet, just wait for a while
                if(futures && futuresDone == 0) { sleep(1000); continue }

                DUMP_COLLECTION(LOGGER, "Found ${orderedProjectsToBeManaged.size()} projects still to be managed:", orderedProjectsToBeManaged)

                //Evaluate the projects that could be managed
                for (proj in orderedProjectsToBeManaged){
                    for (commitDetailsEntry in commitsDetails){
                        def project = commitDetailsEntry.value.project
                        if (project == proj) {
                            def wait = false
                            commitDetailsEntry.value << [systemProperties:'']
                            for (dep in commitDetailsEntry.value.dependencies) {
                                if(dep) {
                                    if(!buildProjectsResult[dep]) {
                                        LOGGER.console "${project} can not be built. Waiting for at least ${dep}"
                                        wait = true
                                        break
                                    }else {
                                        def properties = ''
                                        buildProjectsResult[dep].properties.each { properties += "${it} "}
                                        commitDetailsEntry.value.systemProperties += " ${properties}"
                                    }
                                }
                            }

                            if(!wait){
                                LOGGER.console "Managing ${project} ..."
                                def logger = new Logger("${WORKSPACE}/${project}.log")
                                futures << [(project):threadPool.submit({
                                        buildProject(logger, commitDetailsEntry.key, commitDetailsEntry.value, null)
                                    } as Callable)]
                            }
                            break
                        }
                    }
                }
                DUMP_COLLECTION(LOGGER, "${futures.size()} Future in progress: ", futures)

                //No more project to be managed just wait for the progress ones
                if(!orderedProjectsToBeManaged) {
                    futures.each{
                        buildProjectsResult << it.value.get()
                    }
                    futures.clear()
                    break
                }
            }

            DUMP_MAP_OF_MAP(LOGGER, "Build Result: ", buildProjectsResult)

            buildProjectsResult.each{
                //Store the generated properties for each built rpm
                generatedSystemProperties.addAll(it.value.properties)
            }
        }finally {
            threadPool.shutdown()
        }

    }else{
        if(TOPIC){
            throwException("No commit found for topic: ${TOPIC} !!!")
        }
        LOGGER.console "No commit information found"
    }
}

def getCommitDetails() {
    LOGGER.console "******************* Fetching Commit(s) for project ${VERTICAL_SLICE_PROJECT} *******************"
    def PARSE_GERRIT_JSON_QUERY = true
    def commitsDetails = [:]
    def gerritResponse
    if (commitsType=="single_review"){
        LOGGER.console "Gerrit Change Revision: ${GERRIT_PATCHSET_REVISION}"
        if(PARSE_GERRIT_JSON_QUERY){
            gerritResponse = ("${GERRIT_SSH} query change:${GERRIT_CHANGE_NUMBER} --current-patch-set --format JSON").execute().text
            commitsDetails = parseGerritQueryJsonResponse(gerritResponse)
        }else {
            gerritResponse = ("${GERRIT_SSH} query change:${GERRIT_CHANGE_NUMBER} --current-patch-set").execute().text
            commitsDetails = parseGerritQueryTextResponse(gerritResponse)
        }
    }else if (commitsType=="user_reviews"){
        LOGGER.console "Gerrit Change numbers: ${USER_CHANGE_NUMBERS}"
        for(changeNumber in USER_CHANGE_NUMBERS.tokenize(',')){
            if(PARSE_GERRIT_JSON_QUERY){
                gerritResponse = ("${GERRIT_SSH} query change:${changeNumber} --current-patch-set --format JSON").execute().text
                commitsDetails << parseGerritQueryJsonResponse(gerritResponse)
            }else {
                gerritResponse = ("${GERRIT_SSH} query change:${changeNumber} --current-patch-set").execute().text
                commitsDetails << parseGerritQueryTextResponse(gerritResponse)
            }
        }
    } else if (commitsType=="topic_review"){
        LOGGER.console "Topic branch: ${TOPIC}"
        if(PARSE_GERRIT_JSON_QUERY){
            gerritResponse = ("${GERRIT_SSH} query topic:{${TOPIC}} --current-patch-set --format JSON").execute().text
            commitsDetails = parseGerritQueryJsonResponse(gerritResponse)
        }else {
            gerritResponse = ("${GERRIT_SSH} query topic:{${TOPIC}} --current-patch-set").execute().text
            commitsDetails = parseGerritQueryTextResponse(gerritResponse)
        }
    }

    //ensure that vertical slice project is included in commitsDetails
    if(!commitsDetails[VERTICAL_SLICE_REPO]) {
        def commitDetails = [:]
        commitDetails << [dependencies:[]]
        commitDetails << [repo:VERTICAL_SLICE_REPO]
        commitDetails << [project:VERTICAL_SLICE_PROJECT]
        commitDetails << [url:'']
        commitDetails << [ref:'']
        commitDetails << [user:user]
        commitDetails << [revision:'']
        commitDetails << [status:'']
        commitsDetails << [(VERTICAL_SLICE_REPO):commitDetails]
    }

    LOGGER.console "User: ${user}"
    return commitsDetails
}

def validateDependencies(commitsDetails) {
    def allDependencies = new HashSet<String>()
    def projectToDependenciesMap = [:]

    //collect the dependencies
    commitsDetails.each {
        allDependencies.addAll(it.value.dependencies)
        projectToDependenciesMap << [(it.value.project):it.value.dependencies]
    }

    //ensure that there is a valid commit for each dependency
    allDependencies.each {
        if(!projectToDependenciesMap.keySet().contains(it)) {
            throwException("No commit found for dependency: ${it} !!!")
        }
    }

    //ensure that vertical slice is dependent on each other project
    if(commitsDetails[VERTICAL_SLICE_REPO]) {
        commitsDetails[VERTICAL_SLICE_REPO].dependencies.addAll(projectToDependenciesMap.keySet())
        commitsDetails[VERTICAL_SLICE_REPO].dependencies.remove(VERTICAL_SLICE_PROJECT)
    }

    //ensure that there are no bi-directional dependencies
    projectToDependenciesMap.each { project, dependencies ->
        dependencies.each { dependency ->
            if (projectToDependenciesMap[dependency].contains(project)) {
                throwException("Unmanageble dependencies: ${project} is dependent by ${dependency} but ${dependency} is also dependent by ${project} !!!")
            }
        }
    }
}

def sortProjects(commitsDetails) {
    def orderedProjectsList = new LinkedList();
    for (commitDetailsEntry in commitsDetails){
        def project = commitDetailsEntry.value.project
        def deps = commitDetailsEntry.value.dependencies
        LOGGER.console "Dependencies for project ${project}: ${deps}"
        if (!deps && !orderedProjectsList.contains(project)) {
            orderedProjectsList.add(0, project);
            continue
        }
        for ( dep in deps) {
            depIndex = orderedProjectsList.indexOf(dep);
            currentIndex = orderedProjectsList.indexOf(project)
            if( depIndex == -1 && currentIndex == -1) {
                orderedProjectsList.add(project)
                currentIndex = orderedProjectsList.indexOf(project)
                orderedProjectsList.add(currentIndex, dep);
            } else if (depIndex == -1) {
                orderedProjectsList.add(currentIndex, dep);
            } else if (currentIndex == -1) {
                orderedProjectsList.add(depIndex + 1, project);
            }
        }
    }
    return orderedProjectsList
}

//This method must be thread safe
def fetchProject(logger, repo, commitDetails) {
    def fetchProjectsResult = [:]
    def retrieveProjectVersionByMaven = false
    def user = commitDetails.user
    def ref = commitDetails.ref
    def revision = commitDetails.revision
    def project = commitDetails.project
    def version = null
    def projectSystemProperties = []
    def projDir = new File(getProjDirPath(project))

    fetchProjectsResult << [startTime:System.currentTimeMillis()]
    if (!projDir.exists()){
        runBashCmd(logger, "git clone ${GERRIT_SERVER}/${repo}", new File(WORKSPACE), null, {
            if (it.exitValue()==0) {
                logger.info "Clone Succeeded for ${project}"
            }else {
                def errorMessage = "Failed to clone project: ${project}"
                logger.error (errorMessage)
                throwException(errorMessage)
            }})
    }
    if (projDir.exists()){
        if(ref && revision) {
            runBashCmd(logger, "git fetch ${GERRIT_SERVER}/${repo} ${ref}", projDir, null, {
                if (it.exitValue()==0) {

                    logger.info "Fetch Succeeded for ${project}"
                    logger.info "Revision: ${revision}"
                    logger.info "Ref Found: ${ref}\n"

                    runBashCmd(logger, "git checkout FETCH_HEAD", projDir, null, {
                        (it.err.text).eachLine {
                            if (it.contains("HEAD is now")) {
                                logger.info it
                            }
                        } })
                }else {
                    def errorMessage = "Failed to fetch commit for project: ${project}"
                    logger.error (errorMessage)
                    throwException(errorMessage)
                }})
        }

        runBashCmd(logger, "git status", projDir, null, {it.in.eachLine { logger.info it }})

        if(retrieveProjectVersionByMaven){
            runBashCmd(logger, MVN_VERSION_CMD, projDir, {
                it.in.eachLine { line ->
                    version = line
                    logger.info "Project version: ${version}"
                }
            }, {
                if (it.exitValue()!=0) {
                    def errorMessage = "Failed to retrieve version for project: ${project}"
                    logger.error (errorMessage)
                    throwException(errorMessage)
                }})
        }
    }else{
        logger.info "Module ${project} doesnt exist in the workspace"
    }
    fetchProjectsResult << [endTime:System.currentTimeMillis()]
    fetchProjectsResult << [timeTaken:(fetchProjectsResult.endTime-fetchProjectsResult.startTime)]
    fetchProjectsResult << [version:version]
    return [(project):fetchProjectsResult]
}

//This method must be thread safe
def buildProject(logger, repo, commitDetails, version) {
    def buildProjectsResult = [:]
    def manageProjectRpmsResult = [:]
    def project = commitDetails.project
    def mvnBuildSystemProperties = commitDetails.systemProperties
    def projectSystemProperties = []
    def projDir = new File(getProjDirPath(project))

    buildProjectsResult << [startTime:System.currentTimeMillis()]
    if (projDir.exists()){
        logger.info "Starting build for project: ${project}"
        def MVN_BUILD_OPTIONS = "${ADDITIONAL_BUILD_PROPERTIES} ${MVN_BUILD_BASE_OPTIONS} ${mvnBuildSystemProperties}"
        runBashCmd(logger, "${MVN_CMD} ${MVN_BUILD_OPTIONS}", projDir, { it.in.eachLine { line -> logger.info line }}, {
            if (it.exitValue()!=0) {
                def errorMessage = "Failed to build ${project}"
                logger.error (errorMessage)
                throwException(errorMessage)
            }}
        )
        manageProjectRpmsResult= manageProjectRpms(logger, project, version)
        projectSystemProperties.addAll(manageProjectRpmsResult.properties)
    }else{
        logger.info "Module ${project} doesnt exist in the workspace"
    }
    buildProjectsResult << [endTime:System.currentTimeMillis()]
    buildProjectsResult << [timeTaken:(buildProjectsResult.endTime-buildProjectsResult.startTime)]
    buildProjectsResult << [version:version]
    buildProjectsResult << [properties:projectSystemProperties]
    buildProjectsResult << [ignoredRpms:manageProjectRpmsResult.ignoredRpms]
    return [(project):buildProjectsResult]
}

//This method must be thread safe
def manageProjectRpms(logger, project, version) {
    def builtRpmsVersionSystemProperties = []
    def ignoredRpms = []
    if((project != VERTICAL_SLICE_PROJECT) || (!IGNORE_TEST_REPO_RPMS)) {
        def snapModelRpmsDirLocal = new File(SNAPSHOT_MODEL_RPMS_DIR)
        def snapCodeRpmsDirLocal = new File(SNAPSHOT_CODE_RPMS_DIR)
        def projDir = new File(getProjDirPath(project))
        logger.info "Searching RPMs for  ${project}:${version} ..."
        runBashCmd(logger, "find . -name '*.rpm'", projDir,  null, {
            if (it.exitValue()==0) {
                it.in.eachLine { line ->
                    def rpmFile = new File(projDir, line)
                    def snapModelRpmFile = new File(snapModelRpmsDirLocal, rpmFile.getName())
                    def snapCodeRpmFile = new File(snapCodeRpmsDirLocal, rpmFile.getName())
                    if(!snapModelRpmFile.exists() && !snapCodeRpmFile.exists()) {
                        logger.info "Found rpm: ${line}"

                        def rpmInfo = getRpmInfo(line)
                        if(!RPMS_TO_BE_IGNORED.contains(rpmInfo.name)) {
                            if (rpmInfo) {
                                builtRpmsVersionSystemProperties << "-Dversion.${rpmInfo.name}=${version ? version : rpmInfo.version}"
                            } else {
                                logger.info "WARNING: Unable to retrieve the RPM name for ${line}"
                            }
                            runBashCmd(logger, "rpm -qpl ${line} | grep -o -m 1 ERICmodeldeployment", projDir, null, {
                                if (it.exitValue()==0) {
                                    logger.info "${line} is model rpm"
                                    rpmFile.renameTo(snapModelRpmFile);
                                } else {
                                    logger.info "${line} is code rpm"
                                    rpmFile.renameTo(snapCodeRpmFile);
                                }
                            })
                        }else {
                            ignoredRpms << rpmInfo.name
                        }
                    }
                }
            } else {
                def errorMessage = "Failed to search rpms for ${project}"
                logger.error (errorMessage)
                it.in.eachLine { logger.error it }
                it.err.eachLine { logger.error it }
                throwException(errorMessage)
            }
        })
    }
    return [properties:builtRpmsVersionSystemProperties,ignoredRpms:ignoredRpms]
}

def getJenkinsUser() {
    def user = "No user found"
    def slurper = new JsonSlurper()

    if (JOB_URL && BUILD_ID) {
        def jenkinsRestCall = ("curl ${JOB_URL}${BUILD_ID}/api/json?pretty=true").execute().text

        def jsonResult = slurper.parseText(jenkinsRestCall)

        for (cause in jsonResult.actions.causes){
            if (cause){
                if (cause.shortDescription[0].matches('((Started by user)|(Retriggered by)).*')){
                    user = ((cause.userName).toString()).replaceAll("\\[|\\]","")
                } else if (cause.shortDescription[0].contains("Started by timer")){
                    user = "Timer"
                } else if (cause.shortDescription[0].contains("Triggered by Gerrit")){
                    user = "Gerrit"
                } else if (cause.shortDescription[0].contains("Rebuilds build")){
                    user = ((cause.shortDescription[0]).toString()).replaceAll("\\[|\\]","")
                }
            }
        }
    }
    return user
}

def parseGerritQueryJsonResponse(gerritResponse){
    def slurper = new JsonSlurper()
    //def commitsJsonText = gerritResponse.split('\n').toList().findAll { it.contains('project')}
    def commitsJsonText = gerritResponse.tokenize('\n').findAll { it.contains('project')}
    def commitsDetails = [:]

    for( commitJsonTest in commitsJsonText) {
        def jsonResult = slurper.parseText(commitJsonTest)
        def commitDetails = [:]

        if(jsonResult.status != "DRAFT" && jsonResult.status != "NEW" && jsonResult.status != "MERGED") {
            LOGGER.console "${jsonResult.project}: The review ${jsonResult.url} has been skipped -> status:${jsonResult.status}"
            continue
        }

        if(TOPIC_USERS && !TOPIC_USERS.contains(jsonResult.owner.name)){
            LOGGER.console "${jsonResult.project}: The review ${jsonResult.url} has been skipped -> commitUser:${jsonResult.owner.name} expectedUser:${TOPIC_USERS}"
            continue
        }
        if(commitsDetails.containsKey(jsonResult.project) && jsonResult.status != "NEW") {
            LOGGER.console sprintf("The review ref:%s status:%s has been skipped. The following has been choosen: ref:%s status:%s",
            jsonResult.currentPatchSet.ref, jsonResult.status, commitsDetails[jsonResult.project].ref, commitsDetails[jsonResult.project].status)
            continue
        }

        commitDetails << [dependencies:[]]
        commitDetails << [repo:jsonResult.project]
        commitDetails << [project:commitDetails.repo.split('/').last()]
        commitDetails << [url:jsonResult.url]
        commitDetails << [ref:jsonResult.currentPatchSet.ref]
        commitDetails << [user:jsonResult.owner.name]
        commitDetails << [revision:jsonResult.currentPatchSet.revision]
        commitDetails << [status:jsonResult.status]

        //jsonResult.commitMessage.split('\n').toList().grep(~/.*dependsOn:.*/).each {
        jsonResult.commitMessage.tokenize('\n').grep(~/.*dependsOn:.*/).each {
            //commitDetails << [dependencies:new HashSet<String>(it.replaceAll('\\s','').minus('dependsOn:').split(',').toList().findAll())]
            commitDetails << [dependencies:new HashSet<String>(it.replaceAll('\\s','').minus('dependsOn:').tokenize(','))]
        }
        commitsDetails << [(jsonResult.project):commitDetails]
    }
    return commitsDetails
}
def parseGerritQueryTextResponse(gerritResponse){
    def commitsDetails = [:]
    def commitDetails = [:]
    def commitFieldFilter = { line, field -> line.replaceAll('\\s','').minus("${field}:")}
    gerritResponse.eachLine { line ->
        if (line.contains('project:')) {
            commitDetails.clear()
            commitDetails << [repo:commitFieldFilter(line, 'project')]
            commitDetails << [project:commitDetails.repo.split('/').last()]
            commitDetails << [dependencies:[]]
        }
        if (line.contains('url:')) {
            commitDetails << [url:commitFieldFilter(line, 'url')]
        }
        if (line.contains('dependsOn:')) {
            //commitDetails << [dependencies:new HashSet<String>(commitFieldFilter(line, 'dependsOn').split(',').toList().findAll())]
            commitDetails << [dependencies:new HashSet<String>(commitFieldFilter(line, 'dependsOn').tokenize(','))]
        }
        if (line.contains('ref:')) {
            commitDetails << [ref:commitFieldFilter(line, 'ref')]
        }
        if (line.contains('name:')) {
            if(!commitDetails.user) {
                commitDetails << [user:commitFieldFilter(line, 'name')]
            }
        }
        if (line.contains('revision:')) {
            commitDetails << [revision:commitFieldFilter(line, 'revision')]
        }
        if (line.contains('status:')) {
            commitDetails << [status:commitFieldFilter(line, 'status')]
            if(!(commitDetails.status == "DRAFT" || commitDetails.status == "NEW" || commitDetails.status == "MERGED")) {
                LOGGER.console "${commitDetails.repo}: The review ${commitDetails.url} has been skipped -> ${line}"
                commitDetails.clear()
            }
        }
        if (commitDetails.repo && commitDetails.ref && commitDetails.revision && (!TOPIC_USERS || TOPIC_USERS.contains(commitDetails.user))){
            def repo = commitDetails.repo
            if(!commitsDetails.containsKey(repo) || commitDetails.status == "NEW") {
                commitsDetails.put(repo, commitDetails)
            } else {
                LOGGER.console sprintf("The review ref:%s status:%s has been skipped. The following has been choosen: ref:%s status:%s",
                commitDetails.ref, commitDetails.status, commitsDetails[repo].ref, commitsDetails[repo].status)
            }
            commitDetails = [:]
        }
    }
    return commitsDetails
}

def writeEnvVariables(errorMessage='') {
    def envFile = new File(ENV_FILE_PATH)
    def revisions = ''
    def reviewsHtmlMessage = ''
    def projects = ''
    def properties = ''
    def updatedModelRpmsHtmlMessage = ''
    def updatedCodeRpmsHtmlMessage = ''
    def env_variables = "GERRIT_CHANGE_OWNER_NAME=${user}" + LINE_SEPARATOR

    for (property in generatedSystemProperties){ properties += "${property} "}
    if (commitsDetails) {
        for (commitDetailsEntry in commitsDetails){
            def url = commitDetailsEntry.value.url
            def project = commitDetailsEntry.value.project
            revisions += "${commitDetailsEntry.value.revision} "
            projects += "${getProjDirPath(project)} "
            reviewsHtmlMessage += url ? sprintf(HTML_MESSAGE_LINK_LIST_FORMAT, url, project) : ''
        }
    }
    reviewsHtmlMessage = reviewsHtmlMessage ? sprintf(GERRIT_USER_REVIEWS_HTML_MESSAGE_FORMAT, reviewsHtmlMessage) : ''

    snapModelRpmsDir.listFiles(RPM_FILENAME_FILTER).each{ updatedModelRpmsHtmlMessage += sprintf(HTML_MESSAGE_LIST_FORMAT, it.getName())}
    updatedModelRpmsHtmlMessage = updatedModelRpmsHtmlMessage ? sprintf(UPDATED_PACKAGES_HTML_MESSAGE_FORMAT, 'MODEL', updatedModelRpmsHtmlMessage) : ''

    snapCodeRpmsDir.listFiles(RPM_FILENAME_FILTER).each{ updatedCodeRpmsHtmlMessage += sprintf(HTML_MESSAGE_LIST_FORMAT, it.getName())}
    updatedCodeRpmsHtmlMessage = updatedCodeRpmsHtmlMessage ? sprintf(UPDATED_PACKAGES_HTML_MESSAGE_FORMAT, 'CODE', updatedCodeRpmsHtmlMessage) : ''

    env_variables += "PROJECTS=${projects}" + LINE_SEPARATOR
    env_variables += "MAVEN_BUILD_PROPERTIES=${properties}" + LINE_SEPARATOR
    env_variables += "REVISIONS=${revisions}" + LINE_SEPARATOR
    env_variables += "GERRIT_TEST_REPORTS=true" + LINE_SEPARATOR
    env_variables += "GERRIT_TEST_ERROR_MESSAGE=\"${errorMessage}\"" + LINE_SEPARATOR
    env_variables += "TOPIC=\"${(TOPIC ? TOPIC : '')}\"" + LINE_SEPARATOR
    env_variables += "ERROR_HTML_MESSAGE=\"${(errorMessage ? sprintf(INFO_HTML_MESSAGE_FORMAT, 'ERROR MESSAGE', errorMessage) : '')}\"" + LINE_SEPARATOR
    env_variables += "GERRIT_REVIEWS_HTML_MESSAGE=\"${(commitsType=='no_review' ? '' : reviewsHtmlMessage)}\"" + LINE_SEPARATOR
    env_variables += "GERRIT_TOPIC_QUERY_HTML_MESSAGE=\"${(TOPIC ? sprintf(GERRIT_TOPIC_QUERY_HTML_MESSAGE_FORMAT, TOPIC, TOPIC) : '')}\"" + LINE_SEPARATOR
    env_variables += "UPDATED_MODEL_PACKAGES_HTML_MESSAGE=\"${updatedModelRpmsHtmlMessage}\"" + LINE_SEPARATOR
    env_variables += "UPDATED_CODE_PACKAGES_HTML_MESSAGE=\"${updatedCodeRpmsHtmlMessage}\"" + LINE_SEPARATOR
    envFile.write env_variables
}

def dumpResult() {
    def rpmsReporter = new Logger(UPDATED_RPMS_REPORT, true)
    LOGGER.console LOG_SEPARATOR
    LOGGER.console "Env file content: "
    new File(ENV_FILE_PATH).eachLine{ line -> LOGGER.console line}
    LOGGER.console LOG_SEPARATOR

    rpmsReporter.info ("Model Rpms built:")
    snapModelRpmsDir.listFiles(RPM_FILENAME_FILTER).each{ rpmsReporter.info "${it.getName()}"}
    rpmsReporter.info ("Code Rpms built:")
    snapCodeRpmsDir.listFiles(RPM_FILENAME_FILTER).each{ rpmsReporter.info "${it.getName()}"}
}

def getCommitType(){
    def commitsType = "topic_review"
    if (!TOPIC){
        LOGGER.console "***** No topic found, searching for gerrit change... *****"
        if (USER_CHANGE_NUMBERS) {
            commitsType = "user_reviews"
            LOGGER.console "Found user reviews: ${USER_CHANGE_NUMBERS}"
        } else if (GERRIT_PATCHSET_REVISION) {
            commitsType = "single_review"
            LOGGER.console "Found change, tests ran for ${GERRIT_REFSPEC} on repo ${GERRIT_PROJECT}"
        }else{
            LOGGER.console "***** No review found *****"
            commitsType = "no_review"
        }
    }
    LOGGER.console "Discovered commitsType: ${commitsType}"
    return commitsType
}

def getProjDirPath(project) {
    return "${WORKSPACE}/${project}/"
}

def getRpmInfo(rpmPath){
    Pattern p = rpmPath.contains('SNAPSHOT') ? SNAPSHOT_RPM_PATTERN : RPM_PATTERN
    Matcher m = p.matcher(rpmPath)
    if(m.matches()){
        def version = m.group(2)
        version += version.contains('SNAPSHOT') ? '' : '-SNAPSHOT'
        return [name:m.group(1), version:version]
    }
    return  null
}


def runBashCmd(cmd, dir, duringExecution, afterExecution){
    return runBashCmd(null, cmd, dir, true, duringExecution, afterExecution)
}

def runBashCmd(logger, cmd, dir, duringExecution, afterExecution){
    return runBashCmd(logger, cmd, dir, true, duringExecution, afterExecution)
}

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
    if(duringExecution){
        duringExecution(proc)
    }
    if(wait){
        proc.waitFor()
    }
    if(afterExecution){
        afterExecution(proc)
    }
    return proc
}

def throwException(message) {
    writeEnvVariables(message)
    dumpResult()
    throw new InterruptedException(message)
}
