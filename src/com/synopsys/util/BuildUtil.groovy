package com.synopsys.util

/**
 * A utility class to store build execution methods.
 * This replaces the "tools { nodejs: 'NodeJS' }"" section of a standard pipeline script.
 * https://www.jenkins.io/doc/book/pipeline/shared-libraries/#accessing-steps
 */
class BuildUtil implements Serializable {

  def steps

  BuildUtil(steps) {
    this.steps = steps
  }

  def go(args) {
    steps.sh "${steps.tool 'Go'}/bin/go ${args}"
  }
}
