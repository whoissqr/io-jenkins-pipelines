package com.synopsys.util

/**
 * A utility class to store build execution methods.
 * This replaces the "tools { maven: 'Maven3' }"" section of a standard pipeline script.
 * https://www.jenkins.io/doc/book/pipeline/shared-libraries/#accessing-steps
 */
class BuildUtil implements Serializable {

  def steps

  BuildUtil(steps) {
    this.steps = steps
  }

  def mvn(args) {
    steps.sh "${steps.tool 'Maven3'}/bin/mvn ${args}"
  }
}
