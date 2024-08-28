import jenkins.util.*;
import jenkins.model.*;
import hudson.model.*

def thr = Thread.currentThread();
def currentBuild = thr?.executable;
def workspace = currentBuild.getModuleRoot().absolutize().toString();
def nvFile = "$workspace/env.txt"

manager.listener.logger.println "INFO: Updating job environment with name/value pairs from '$nvFile'"

def build = Thread.currentThread().executable

def envFile= build.getParent().getWorkspace().child('env.txt').readToString()
manager.listener.logger.println "envFile= "+envFile


  envFile.eachLine { line ->
    if (! line.startsWith("#") && ! line.isAllWhitespace() && line.contains("=")) {
      def name = line.substring(0, line.indexOf('='))
      def value = line.substring(line.indexOf('=')+1)

      def var = new hudson.model.StringParameterValue(name, value);
      def varAction = new hudson.model.ParametersAction(var);
      currentBuild.addAction(varAction);
    }
  }
