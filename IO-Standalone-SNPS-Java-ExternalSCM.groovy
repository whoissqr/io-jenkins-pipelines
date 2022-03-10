def isSASTEnabled
def isSASTPlusMEnabled
def isSCAEnabled
def isDASTEnabled
def isDASTPlusMEnabled
def buildBreakerStatus

pipeline {
    agent any
    tools {
        maven 'Maven3'
    }
    environment {
        SYNOPSYS_IO_Scm_CodePatch = sh(script: 'git diff HEAD^ HEAD', , returnStdout: true)
    }

    stages {
        stage('Checkout Source Code') {
            steps {
                git branch: 'master', url: 'https://bitbucket.org/1nv1n/spring-boot-demo'
            }
        }

        stage('Build Source Code') {
            steps {
                sh '''mvn clean compile'''
            }
        }

        stage('IO - Prescription') {
            steps {
                synopsysIO() {
                        sh 'io --version'
                        sh "io --stage io --verbose \
                            scm.Type=external \
                            Project.Name='bb-spring-sample' \
                            Project.Application.Name='spring-boot-demo' \
                            Persona.Type='devsecops' \
                            Io.Server.Token=$IOToken \
                            Io.Server.Url='http://23.99.131.170'"
                    }

                script {
                    def prescriptionJSON = readJSON file: 'io_state.json'

                    print("Business Criticality Score: $prescriptionJSON.data.prescription.riskScore.businessCriticalityScore")
                    print("Data Class Score: $prescriptionJSON.data.prescription.riskScore.dataClassScore")
                    print("Access Score: $prescriptionJSON.data.prescription.riskScore.accessScore")
                    print("Open Vulnerability Score: $prescriptionJSON.data.prescription.riskScore.openVulnerabilityScore")
                    print("Change Significance Score: $prescriptionJSON.data.prescription.riskScore.changeSignificanceScore")
                    print("Tooling Score: $prescriptionJSON.data.prescription.riskScore.toolingScore")
                    print("Training Score: $prescriptionJSON.data.prescription.riskScore.trainingScore")

                    isSASTEnabled = prescriptionJSON.data.prescription.security.activities.sast.enabled
                    isSASTPlusMEnabled = prescriptionJSON.data.prescription.security.activities.sastPlusM.enabled
                    isSCAEnabled = prescriptionJSON.data.prescription.security.activities.sca.enabled
                    isDASTEnabled = prescriptionJSON.data.prescription.security.activities.dast.enabled
                    isDASTPlusMEnabled = prescriptionJSON.data.prescription.security.activities.dastPlusM.enabled
                    isImageScanEnabled = prescriptionJSON.data.prescription.security.activities.imageScan.enabled

                    print("SAST Enabled: $isSASTEnabled")
                    print("SAST+Manual Enabled: $isSASTPlusMEnabled")
                    print("SCA Enabled: $isSCAEnabled")
                    print("DAST Enabled: $isDASTEnabled")
                    print("DAST+Manual Enabled: $isDASTPlusMEnabled")
                    print("ImageScan Enabled: $isImageScanEnabled")
                }
            }
        }

        stage('SAST - RapidScan (Sigma)') {
            when {
                expression { isSASTEnabled }
            }
            environment {
                OSTYPE = 'linux-gnu'
            }
            steps {
                echo 'Running SAST using Sigma - Rapid Scan'
                synopsysIO(connectors: [
                    rapidScan(configName: 'Sigma')]) {
                    sh 'io --stage execution --state io_state.json'
                }
            }
        }

        stage('SAST - Polaris') {
            when {
                expression { isSASTEnabled }
            }
            steps {
                echo 'Running SAST using Polaris'
                synopsysIO(connectors: [
                    [$class: 'PolarisPipelineConfig',
                    configName: 'csprod-polaris',
                    projectName: 'sig-devsecops/spring-boot-demo']]) {
                    sh 'io --stage execution --state io_state.json'
                }
            }
        }

        stage('SCA - BlackDuck') {
            when {
                expression { isSCAEnabled }
            }
            steps {
              echo 'Running SCA using BlackDuck'
              synopsysIO(connectors: [
                  blackduck(configName: 'BIZDevBD',
                  projectName: 'spring-boot-demo',
                  projectVersion: '0.0.1-SNAPSHOT')]) {
                  sh 'io --stage execution --state io_state.json'
              }
            }
        }

        stage('IO - Workflow') {
            steps {
                echo 'Execute Workflow Stage'
                synopsysIO(connectors: [
                    codeDx(configName: 'SIG-CodeDx', projectId: '6'),
                    buildBreaker(configName: 'BB-Custom')
                ]) {
                    sh 'io --stage workflow --state io_state.json'
                }

                script {
                    def workflowJSON = readJSON file: 'wf-output.json'
                    print("========================== IO WorkflowEngine Summary ============================")
                    print("Breaker Status: $workflowJSON.breaker.status")

                    buildBreakerStatus = workflowJSON.breaker.status
                    workflowJSON.breaker.criteria.each{activity->
                        print("Activity: ${activity.activityname}")
                        if(activity.has("overall")) {
                            overallCount = activity.overall.size()
                            print("Build Breaker Issue Count: $overallCount")
                        } else if(activity.has("risk_score")) {
                            print("CodeDx Risk Score: ${activity.risk_score}")
                        }
                    }
                }
            }
        }

        stage('Security Sign-Off') {
            steps {
                script {
                    if (buildBreakerStatus) {
                        input message: 'One or more conditions triggered Build Breaker. Do you wish to proceed?'
                    }
                }
                echo "Security Sign-Off triggered & approved"
            }
        }
    }

    post {
        always {
            // Always remove the state JSON file as it has sensitive information
            sh 'rm io_state.json'
        }
    }
}