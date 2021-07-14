#!/bin/bash
# v0.0.3 - 20210411 - update run-project-test.sh, and make execution recurly

function logp() {
  echo "[PROJECT TEST] ${1}"
}

function execute_maven_recurly() {
  # if it is singel project
  if [[ -d "src" ]]; then
    local project_name="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
    project_name=${project_name##*/} # current folder without full path, for print out only
    execute_maven "${project_name}"
  elif [[ ! -f "run-project-test.skip" ]]; then
  # for multiple projects
    for d in *; do
      if [[ -d "${d}" ]]; then
        cd "${d}" || exit
        execute_maven_recurly #"${d}"
        cd .. || exit
      fi
    done
  fi
}

# execute maven clean test if there is pom.xml. in future it can be execute gradle also.
function execute_maven() {
  local d="${1}"
  # use "pom.xml" to tell if maven project
  if [[ -f pom.xml ]]; then
    printf "[PROJECT TEST] %s: " "${d}" # printf not to printing a line seperator
    # choose the maven exector, use maven wrapper or maven installed in local
    if [[ -f mvnw ]]; then local response=$(./mvnw clean test); else local response=$(mvn clean test); fi
    # use "BUILD SUCCESS" as successful indicator
    if [[ "$(echo "${response}" | grep "BUILD SUCCESS")" != "" ]]; then # success
      echo "Test successfully... ..."
      succ=$((succ + 1))
    else
      echo "Test failed!!!"
      failed=$((failed + 1))
    fi
  fi
}

function project_test() {
  local repo_test_report="${1}" # taking from command parameter
  
  local CURRENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
  echo "--------------------------------------------------------------------------------"
  logp "${CURRENT_DIR}"
  logp ""

  # execute all maven project(s) in this folder
  local succ=0
  local failed=0
  execute_maven_recurly

  echo ""
  logp "Total success: ${succ}; Total failed: ${failed}"
  echo ""
  # append the project test result into repo rest report with formatted by tab
  if [[ -f "${repo_test_report}" ]]; then
    # with format and placeholder
    ## %-80s: append 80 spaces on the right, if not reach to 80
    ## [20210411] update the name
    ## ${CURRENT_DIR##*/}: only take sub string after the last /
    # printf "%-80s   SUCCESS: %2d   FAILED: %2d\n" "${CURRENT_DIR##*/}" ${succ} ${failed} >> "${repo_test_report}"
    ## [20210411]
    local ROOT_PROJECT="github"
    echo "${ROOT_PROJECT}------------------------------------xxxx"
    local NAME_INDEX=$(awk -v a="${CURRENT_DIR}" -v b="${ROOT_PROJECT}" 'BEGIN{print index(a,b)}')
    NAME_INDEX=$(( NAME_INDEX + ${#ROOT_PROJECT} + 1 )) # remove till 1st sub folder in ${ROOT_PROJECT}
    local NAME=$(echo "${CURRENT_DIR}" | cut -c ${NAME_INDEX}-)
    printf "%-80s   SUCCESS: %2d   FAILED: %2d\n" "${NAME}" ${succ} ${failed} >> "${repo_test_report}"
  fi
}

project_test "${1}"
