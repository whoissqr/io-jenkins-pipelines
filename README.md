# Synopsys Intelligent Orchestration

## Overview

### Jenkins Shared Library for Java

Shared library code to run Java projects. Security stages & configured tools:

- SAST
  - Sigma
  - Polaris
- SCA:
  - BlackDuck

### Code

Entry point/script for pipeline code execution: `src\com\synopsys\pipeline\SecurityPipeline.groovy` - method: `execute()`.

This can be invoked by: `new pipeline.SecurityPipeline().execute()`.

Tools configured under Jenkins's Global Tool Configuration (such as Maven or Node JS installations) cannot directly be set in the shared library the same way as they are set on standard pipeline code. Refer to `src\com\synopsys\util\BuildUtil.groovy` that defines `"${steps.tool '<tool>'}/bin/<tool> ${args}"` under a helper method to run the build using globally configured tools.

## Setup/Usage

First, setup Jenkins to read the shared library (Ref: [Jenkins Library Setup](#jenkins-library-setup)).

Next, setup the project to execute the shared library (Ref: [Project Setup](#project-setup)).

Finally, create a job that runs the project as a pipeline utilizing the above configuration (Ref: [Pipeline Job Setup](#pipeline-job-setup)).

### Jenkins Library Setup

- Navigate to Jenkins's global configuration page (Jenkins Dashboard > Manage Jenkins > Configure System)
- Locate 'Global Pipeline Libraries'
  - 'Add' a new entry.
  - Set name: 'io-library-java'
  - Set version: 'shared-library-java'
  - Set retrieval method: Modern SCM
  - Set Source Code Management: GitHub
  - Set Project Repository: 'https://github.com/devsecops-test/io-jenkins-pipelines/'

### Project Setup

- Complete Jenkins setup (above).
- Locate project that should be run as a pipeline using this shared library code.
  - eg: https://github.com/devsecops-test/vulnado
- On this project, create a new file, `Jenkinsfile` (no file extension).
- Add the code from the block below to the Jenkinsfile.
  - Line #1 locates the library saved under Jenkin's global configuration using the `@Library` annotation.
  - Line #2 imports all code from the shared-library package: `com.synopsys`.
  - Line #3 triggers the execution of the shared-library code from the `execute()` method.
````
@Library('io-library-java')
import com.synopsys.*
new pipeline.SecurityPipeline().execute()
````

### Pipeline Job Setup

- Create a new job on Jenkins (Type: Pipeline)
- Pipeline Defintion: 'Pipeline script from SCM'
- Set SCM: 'Git'
- Set Repository URL (to the project selected in the step above), eg: 'https://github.com/devsecops-test/vulnado'
- Set Branch Specifier: '*/devsecops-shared-library'.
- Ensure 'Script Path' is set to 'Jenkinsfile'.
- Save & execute.