/**
 * Bash Executor
 * 
 * This is script is used to run bash script
 * 
 * 
 * @author ebialan
 *         
 */


import java.text.SimpleDateFormat;

class Logger {
    def logFile = System.out;
    private final static String dateFormat = "[dd.MM.yyyy;HH:mm:ss.SSS]"

    public Logger () {
    }

    public Logger (String logFilePath) {
        logFile = new File(logFilePath)
    }

    def methodMissing(String name, args) {
        def threadID = Thread.currentThread().getId()
        def message = args[0]
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat)
        String date = formatter.format(new Date())
        
        switch (name.toLowerCase()) {
            case 'console':
               logFile << "${date} [Thread:${threadID}] ${message}\n"
            break
        }
    }
}


/**
 * Main
 */
workDirPath = args[0]
cmd = args[1] + (args.length > 3 ? " "+args[3].replaceAll(","," ") : "")
loggerFilePath = args.length > 2 ? args[2] : null

logger = loggerFilePath ? new Logger(loggerFilePath) : new Logger()
workDir = new File(workDirPath)

runBashCmd("pwd", workDir)
runBashCmd("${cmd}", workDir)


def runBashCmd(cmd, dir){
    return runBashCmd(cmd, dir, true)
}

def runBashCmd(String cmd, File dir, boolean wait){
    def logMessage = ">>>> ${cmd}"
    def stdoutStream = loggerFilePath ? new FileOutputStream(loggerFilePath) : System.out
    def stderrStream = loggerFilePath ? new FileOutputStream(loggerFilePath) : System.err

    logger.console ''
    logger.console logMessage

    def proc =  (["/bin/bash", "-c", cmd] as String[]).execute(null, dir)
    
    if(wait){
        proc.waitForProcessOutput(stdoutStream, stderrStream)
    } else {
        proc.consumeProcessOutput(stdoutStream, stderrStream)
    }
    return proc
}

def throwException(message) {
    writeEnvVariables(message)
    dumpResult()
    throw new InterruptedException(message)
}

