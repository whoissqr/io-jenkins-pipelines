# Synopsys Intelligent Orchestration

## Overview

### Jenkins Shared Library for Java

Shared library code to run Java projects. Security stages & configured tools:

- SAST
  - Sigma
  - Polaris
  - SpotBugs
- SCA:
  - BlackDuck
  - Dependency-Check

### Code

Entry point/script for pipeline code execution: `src\com\synopsys\pipeline\SecurityPipeline.groovy` - method: `execute()`.

This can be invoked by: `new pipeline.SecurityPipeline().execute()`.

Tools configured under Jenkins's Global Tool Configuration (such as Maven or Node JS installations) cannot directly be set in the shared library the same way as they are set on standard pipeline code. Refer to `src\com\synopsys\util\BuildUtil.groovy` that defines `"${steps.tool '<tool>'}/bin/<tool> ${args}"` under a helper method to run the build using globally configured tools ([doc](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#accessing-steps)).

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
- Set Repository URL (to the project selected in the step above, eg: 'https://github.com/devsecops-test/vulnado')
- Set Branch Specifier (to the appropriate branch on the project selected in the step above, eg:'*/devsecops-shared-library-java')
- Ensure 'Script Path' is set to 'Jenkinsfile'.
- Save pipeline job configuration.

### Parameters

Set the parameters required for the build & execute.

- SCMURL - The URL of the project to build.
- SCMBranch - The branch of the project to build.
- IOConfigName - The configuration name of IO (configured under Jenkin's global configuration for IO).
- IOProjectName - The project name configured for this build through IO's UI.
- IOWorkflowVersion - The IO workflow version.
- GitHubConfigName - The configuration name for GitHub (configured under Jenkin's global configuration for IO).
- GitHubOwner - The owner of the repository for the GitHub project.
- GitHubRepositoryName - The name of the GitHub repository.
- BuildBreakerConfigName - Build Breaker configuration name.
- PolarisClassName - Class name for Polaris
- PolarisConfigName - The configuration name for Polaris (configured under Jenkin's global configuration for IO).
- PolarisProjectName - The name of the project as configured on the Polaris instance.
- BlackDuckConfigName - The configuration name for BlackDuck (configured under Jenkin's global configuration for IO).
- BlackDuckProjectName - The name of the project as configured on the BlackDuck instance.
- BlackDuckProjectVersion - The version of the project configured for the BlackDuck scan.
- SpotBugs - (Boolean) Enable to also execute SpotBugs during the SAST stage (only runs if SAST is enabled by IO's prescription).
- DependencyCheck - (Boolean) Enable to also execute Dependency-Check during the SCA stage (only runs if SCA is enabled by IO's prescription).