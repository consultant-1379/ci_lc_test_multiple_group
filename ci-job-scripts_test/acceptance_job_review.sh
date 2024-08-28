#!/bin/bash
#
# author ebialan

getFailSafeReports(){

  for file in "$@"; do
    if [ -f $file ]; then 
      printf "Parsing $file ...\n"
      while IFS=',' read -ra line; do
          array=$(grep "Tests run" | awk -F"," '{print $1, $2, $3, $4}' | awk -F" " '{print $3, $5, $7, $9}')
      done < "$file"
      TESTS_RAN=$((TESTS_RAN + $( echo ${array} | awk -F" " '{print $1}')))
      FAILURES=$((FAILURES + $( echo ${array} | awk -F" " '{print $2}')))
      ERRORS=$((ERRORS + $( echo ${array} | awk -F" " '{print $3}')))
      SKIPPED=$((SKIPPED + $(echo ${array} | awk -F" " '{print $4}')))
      SEND_REPORT=true
    else
      printf "File "$file" does not exist \n"
    fi
  done
}

getSurefireReports(){

  for file in "$@"; do
    if [ -f $file ]; then
     printf "Parsing $file ...\n"
     line=$(sed -n '2p' $file)
     TESTS_RAN=$((TESTS_RAN + $( echo $line | grep -o 'tests=[^ ,]\+' | awk -F'"' '{ print $2 }')))
     ERRORS=$((ERRORS + $(echo $line | grep -o 'errors=[^ ,]\+' | awk -F'"' '{ print $2 }')))
     FAILURES=$((FAILURES + $(echo $line | grep -o 'failures=[^ ,]\+' | awk -F'"' '{ print $2 }')))
     SKIPPED=$((SKIPPED + $(echo $line | grep -o 'skipped=[^ ,]\+' | awk -F'"' '{ print $2 }')))
     SEND_REPORT=true
    else
      printf "File "$file" does not exist \n"
    fi
  done
}

getBuildErrorReports(){
    local BUILD_ERROR_MESSAGE=$@
    printf "Build Error Message: ${BUILD_ERROR_MESSAGE}\n\n"
    ERROR_MESSAGE="${BUILD_ERROR_MESSAGE}"
    SEND_REPORT=true
}
    
sendReportToGerritReview() {
  COMMENT="Vertical Slice Test Report\n"
  COMMENT="${COMMENT}Vertical Slice Job: $BUILD_URL\n\n"
  COMMENT="${COMMENT}Cucumber Report: ${BUILD_URL}cucumber-html-reports/feature-overview.html\n\n"
  COMMENT="${COMMENT}Updated RPMs Report: ${BUILD_URL}artifact/updated_rpms_report.log\n\n"
  COMMENT="${COMMENT}Inspect Reviews Report: ${BUILD_URL}artifact/inspect-review.log\n\n"
  COMMENT="${COMMENT}TESTS RAN: $TESTS_RAN\n"
  COMMENT="${COMMENT}FAILURES: $FAILURES\n"
  COMMENT="${COMMENT}ERRORS: $ERRORS\n"
  COMMENT="${COMMENT}SKIPPED: $SKIPPED\n"
  COMMENT="${COMMENT}ERROR_MESSAGE: $ERROR_MESSAGE\n"
  COMMENT="$(echo -e ${COMMENT})"

  for revision in $REVISIONS
  do
      if (( $FAILURES > 0 )) || (( $ERRORS > 0 )) || (($TESTS_RAN == 0))
      then
        FEEDBACK="-1"
      else
        FEEDBACK="+1"
      fi
       
      printf "Sending report for revision=${revision}: $FEEDBACK\n"
      ssh -p 29418 gerrit.ericsson.se gerrit review "$revision" --label Vertical-Slice=$FEEDBACK -m "'${COMMENT}'"
      #ssh -p 29418 gerrit.ericsson.se gerrit review "$revision" --verified $FEEDBACK
      if [ "$GERRIT_VERTICAL_SLICE_SUBMIT" == "true" -a "$FEEDBACK" == "+1" ]; then
        printf "Submiting changes for revision=${revision}\n"
        ssh -p 29418 gerrit.ericsson.se gerrit review "$revision" --submit
      fi
      printf "\n"
  done
}

checkReportType() {
  if [ ! -z "$(eval echo ${GERRIT_TEST_ERROR_MESSAGE})" ]; then
    getBuildErrorReports $GERRIT_TEST_ERROR_MESSAGE
  elif [[ $1 == *"xml" ]]; then
    getSurefireReports $@
  elif [[ $1 == *"txt" ]]; then
    getFailSafeReports $@
  else
    printf "No suitable test reports found"
  fi

  printf "\n*************************************************************************************\n"
}


#################################
# MAIN
#################################

#TODO: in case of failure jenkins is not loading env variables produced by inspect_review.groovy 
#source /dev/stdin <<-EOF 
#  $(cat ${WORKSPACE}/env.txt | grep -e GERRIT_TEST -e REVISIONS -e HTML | sed 's/^/export /g')
#EOF

if [ "$GERRIT_TEST_REPORTS" == "true" ]; then
  printf "\n********************* Reporting Test Results to Code Reviews ************************\n\n"

  SEND_REPORT=false
  TESTS_RAN=0
  FAILURES=0
  ERRORS=0
  SKIPPED=0
  ERROR_MESSAGE=""

  checkReportType $@
  [ $SEND_REPORT ] && sendReportToGerritReview
else
  printf "Test reports not requested to be sent to Gerrit Reviews\n"
fi
