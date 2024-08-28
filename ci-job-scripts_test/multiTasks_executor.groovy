/**
 *  multi task executor
 * 
 * This is script is responsible to run the list of provided tasks in parallel and wait the result for each of them
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

class Env {
    public final static String MULTITASKS_EXECUTOR_THREAD_POOL_SIZE = System.getenv()['MULTITASKS_EXECUTOR_THREAD_POOL_SIZE']
    public final static String WORKSPACE = System.getenv()['WORKSPACE']
    public final static String BUILD_ID = System.getenv()['BUILD_ID']
    public final static String JOB_URL = System.getenv()['JOB_URL']
}

class Constant {
    public final static String LOG_SEPARATOR = "*************************************************************************************"
    public final static int DEFAULT_THREAD_POOL_SIZE = 10
    public final static int THREAD_POOL_SIZE = MULTITASKS_EXECUTOR_THREAD_POOL_SIZE ? MULTITASKS_EXECUTOR_THREAD_POOL_SIZE.toInteger() : DEFAULT_THREAD_POOL_SIZE
    public final static String ENV_FILE_PATH = "${WORKSPACE}/env.txt"
    public final static Logger LOGGER = new Logger("${WORKSPACE}/multiTasks_executor.log", true)
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


configFilePath = args[0]
tasksInfo = parseConfigFile(configFilePath)
show()
if (args.length < 2 || args[1] != 'show') {
    execute()
}

def show(){
    LOGGER.info LOG_SEPARATOR
    LOGGER.info "Tasks to be executed:"
    tasksInfo.each { description, taskData ->
        LOGGER.info "${description}: "
        LOGGER.info " - workdir:${taskData.workdir}"
        LOGGER.info " - cmd:${taskData.cmd}"
    }
}

def execute() {
    def futures = [:]
    def threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
    try {
        LOGGER.info LOG_SEPARATOR
        tasksInfo.each { description, taskData ->
            def logger = new Logger("${WORKSPACE}/${description}.log")
            def workDir = new File(taskData.workdir)
            LOGGER.info "Starting Task: ${description}"
            futures << [(description):threadPool.submit({->runBashCmd(logger, workDir, taskData.cmd)} as Callable)]
        }
        LOGGER.info LOG_SEPARATOR
        futures.each { description, future ->
            def taskResult = future.get()
            def timeTaken = taskResult.endTime - taskResult.startTime
            if(future.isDone()) {
                if (taskResult.task.exitValue()==0) {
                    LOGGER.info "Task ${description} completed in ${timeTaken} ms"
                } else {
                    def errorMessage = "Task ${description} failed after ${timeTaken} ms. See ${description}.log" 
                    LOGGER.error errorMessage
                    throw new InterruptedException(errorMessage)
                }
            }else if(future.isCancelled()){
                def errorMessage = "Task ${description} has been cancelled after ${timeTaken} ms. See ${description}.log"
                LOGGER.error errorMessage
                throw new InterruptedException(errorMessage)
            }
        }
    }finally {
        threadPool.shutdown()
    }
}

def parseConfigFile(String configFilePath) {
    def configFile = new File(configFilePath)
    def tasksInfo = [:]
    configFile.eachLine { line ->
        if(!line.startsWith('#')) {
            lineTockens = line.split(';')
            tasksInfo << [(lineTockens[0]):[workdir:lineTockens[1],cmd:lineTockens[2]]]
        }
    }
    return tasksInfo
}

def runBashCmd(logger, dir, cmd){
    def logMessage = ">>>> ${cmd}"
    if (logger) {
        logger.info logMessage
    } else {
        LOGGER.info logMessage
    }

    def taskResult = [startTime:System.currentTimeMillis()]
    def task =  (["/bin/bash", "-c", cmd] as String[]).execute(null, dir)
    task.in.eachLine { logger.info it }
    task.err.eachLine { logger.info it }
    task.waitFor()
    taskResult << [task:task, endTime:System.currentTimeMillis()]
    return taskResult
}
