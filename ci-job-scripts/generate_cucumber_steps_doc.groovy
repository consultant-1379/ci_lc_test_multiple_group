/**
 * 
 * This is script is used to generate cucumber steps documentation
 * 
 * @author ebialan
 *         
 */

import static Env.*
import static Constant.*
//import static Utils.*

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
    public final static String GIT_REPO = System.getenv()['GIT_REPO']
    public final static String WORKSPACE = System.getenv()['WORKSPACE']
    public final static String ENV_CUCUMBER_STEPS_DIR = System.getenv()['CUCUMBER_STEPS_DIR']
    public final static String GERRIT_SSH = 'ssh -p 29418 gerrit.ericsson.se gerrit'
    public final static String GERRIT_MIRROR = System.getenv()['GERRIT_MIRROR']
    public final static String GERRIT_CENTRAL = System.getenv()['GERRIT_CENTRAL']
    public final static String BUILD_ID = System.getenv()['BUILD_ID']
    public final static String JOB_URL = System.getenv()['JOB_URL']
}


class Constant {
    public final static String LOG_SEPARATOR = "*************************************************************************************"
    public final static String LINE_SEPARATOR = System.getProperty("line.separator")
    public final static String ENV_FILE_PATH = "${WORKSPACE}/env.txt"
    public final static String DEFAULT_CUCUMBER_STEPS_DIR = 'cucumber-steps'
    public final static String CUCUMBER_STEPS_DIR = ENV_CUCUMBER_STEPS_DIR ? ENV_CUCUMBER_STEPS_DIR : DEFAULT_CUCUMBER_STEPS_DIR
    public final static String HTML_FOLDER = "${WORKSPACE}/cucumber-step-doc"
    public final static Map<String,String> CUCUMBER_STEP_COLOR = ['@Given':'orange','@When':'RoyalBlue','@Then':'greenyellow','@And':'white','@But':'white']
    public final static String REPO = GIT_URL.contains(GERRIT_MIRROR) ? GIT_URL.split("${GERRIT_MIRROR}/")[1] : GIT_URL.split("${GERRIT_CENTRAL}/")[1]
    public final static String GERRIT_FILE_LINK_FORMAT = 'https://gerrit.ericsson.se/gitweb?p=%s.git;a=blob;f=%s'
}

new File(HTML_FOLDER).mkdirs()
info = discoverCucumberSteps()
writeIndexHtml(info)
writeProjectHtml(info)

def discoverCucumberSteps() {
    def projectSteps = [:]
    def currentDir = new File(WORKSPACE)
    def cucumberStepsDir = null
    def dirs = []
    proc = runBashCmd("find . -name '${CUCUMBER_STEPS_DIR}' -type d", currentDir,  null, {
        if (it.exitValue()==0) {
            cucumberStepsDir =   it.in.text.trim()
        }else{
            abort("${CUCUMBER_STEPS_DIR} not found !!!")
        }})
    def stepsProject = new File(cucumberStepsDir)
    stepsProject.eachFile(FileType.DIRECTORIES) {
        def classSteps = [:]
        dir = it.name
        def projDir = it
        runBashCmd("find . -name '*.java'", projDir,  null, {
            if (it.exitValue()==0) {
                it.in.eachLine { line ->
                    def clazz = line.split('/').last()
                    def javaFilePath = "${cucumberStepsDir}/${dir}/${line}"
                    def javaFileGerritPath = javaFilePath.substring(javaFilePath.indexOf(CUCUMBER_STEPS_DIR)).replaceAll('/./','/');
                    def javaFileGerritLink = sprintf(GERRIT_FILE_LINK_FORMAT, REPO, javaFileGerritPath);
                    def javaFile = new File(javaFilePath)
                    def isDeprecated = false
                    def steps = new ArrayList<>()
                    javaFile.eachLine {
                        def step
                        def pattern = Pattern.compile('^[\\t\\s]*(@Deprecated)?[\\t\\s]*(@Given|@When|@Then|@And|@But).*')
                        Matcher m = pattern.matcher(it)
                        if(m.matches()){
                            step = it.minus('@Deprecated').replaceAll('^\\s*','')
                            if(isDeprecated || it.contains('@Deprecated')) {
                                steps << [project:dir,class:clazz,step:step,deprecated:true]
                                isDeprecated = false
                            }else {
                                steps << [project:dir,class:clazz,step:step,deprecated:false]
                            }
                        }else {
                            isDeprecated = it.contains('@Deprecated')
                        }
                    }
                    if(steps){
                        classSteps << [(clazz):[steps:steps, gerritLink:javaFileGerritLink]]
                    }
                }
            } else {
                abort("Failed to search rpms for ${project}")
            }
        })
        if(classSteps){
            projectSteps << [(dir):[project:dir,steps:classSteps]]
        }
    }
    return projectSteps
}

def writeIndexHtml(info){
    def writer = new StringWriter()
    def html = new MarkupBuilder(writer)
    html.html {
        head {
            title: "Creating html document with groovy"
            script: "alert('hello');"
        }
        body(bgcolor:"black", link:"lime", vlink:"lime") {
            mkp.yieldUnescaped("<b><font color=\"whitesmoke\">")
            h1 "ENM Cucumber Steps"
            h2 "Projects"
            p {
                if(info){
                    info.each { project, steps ->
                        if(steps.steps) {
                            mkp.yieldUnescaped('- ')
                            a href: "${project}.html", "${project}"
                            mkp.yieldUnescaped('<br><br>')

                        }
                    }
                }
            }
            mkp.yieldUnescaped("</font></b>")
        }
    }
    index = new File("${HTML_FOLDER}/index.html")
    index.write(writer.toString())
    index << '\n'

}

def writeProjectHtml(info){
    if(info){
        info.each { project, steps ->
            if(steps.steps) {
                def writer = new StringWriter()
                def html = new MarkupBuilder(writer)
                html.html {
                    head {
                        title: "Creating ${project}"
                        script: "alert('hello');"
                    }
                    body(bgcolor:"black", link:"whitesmoke", vlink:"whitesmoke") {
                        mkp.yieldUnescaped("<b><font color=\"whitesmoke\">")
                        h1 id: "book-mark",  "Cucumber steps provided by \"${project}\" project"
                        mkp.yieldUnescaped("</font></b>")
                        p {
                            steps.steps.each { clazz, clazzInfo ->
                                def isteps = clazzInfo.steps
                                def clazzGerritLink = clazzInfo.gerritLink
                                if(isteps) {
                                    mkp.yieldUnescaped("<b><font color=\"whitesmoke\">")
                                    //mkp.yieldUnescaped("<pre><b>  - ${clazz}</b></pre>")
                                    mkp.yieldUnescaped("<b>  - ")
                                    a href: "${clazzGerritLink}", "${clazz}"
                                    mkp.yieldUnescaped("</b>")
                                    mkp.yieldUnescaped("</font></b>")
                                    isteps.each { step ->
                                        if(step.deprecated){
                                            mkp.yieldUnescaped("<del>")
                                        }
                                        def pattern = Pattern.compile('(@Given|@When|@Then|@And|@But)(.*)')
                                        Matcher m = pattern.matcher(step.step)
                                        if(m.matches()){
                                            message1 = "${m.group(1)}"
                                            message2 = "${m.group(2)}"
                                            mkp.yieldUnescaped("<pre>")
                                            mkp.yieldUnescaped("<font color=\"${CUCUMBER_STEP_COLOR[message1]}\">")
                                            mkp.yieldUnescaped("    ${message1}")
                                            mkp.yieldUnescaped("</font>")
                                            mkp.yieldUnescaped("<font color=\"grey\">")
                                            mkp.yieldUnescaped(message2)
                                            mkp.yieldUnescaped("</pre>")
                                        }
                                        if(step.deprecated) {
                                            mkp.yieldUnescaped("</del>")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                htmlFile = new File("${HTML_FOLDER}/${project}.html")
                htmlFile.write(writer.toString())
                htmlFile << '\n'
            }
        }
    }
}

def abort(errorMessage) {
    println (errorMessage)
    it.in.eachLine { println it }
    it.err.eachLine { println it }
    throw new InterruptedException(errorMessage)
}

def runBashCmd(cmd, dir, duringExecution, afterExecution){
    return runBashCmd(cmd, dir, true, duringExecution, afterExecution)
}

def runBashCmd(String cmd, File dir, boolean wait, Closure duringExecution, Closure afterExecution){
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
