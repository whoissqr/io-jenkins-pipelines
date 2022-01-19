package com.synopsys.util

class BuildUtil implements Serializable {

  def steps

  BuildUtil(steps) {
    this.steps = steps
  }

  def mvn(args) {
    steps.sh "${steps.tool 'Maven3'}/bin/mvn ${args}"
  }
}
