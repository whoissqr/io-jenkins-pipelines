def isSASTEnabled
def isSASTPlusMEnabled
def isSCAEnabled
def isDASTEnabled
def isDASTPlusMEnabled

pipeline {
    agent any
    tools {
        maven 'Maven3'
    }
    stages {
        stage('Checkout Source Code') {
            steps {
                git branch: 'devsecops', url: 'https://github.com/devsecops-test/vulnado'
            }
        }

        stage('Build Source Code') {
            steps {
                sh '''mvn clean compile'''
            }
        }

        stage('IO - Prescription') {
            steps {
                synopsysIO(connectors: [
                    io(
                        configName: 'io-azure',
                        projectName: 'devsecops-vulnado',
                        workflowVersion: '2021.12.2'),
                    github(
                        branch: 'devsecops',
                        configName: 'github-devsecops',
                        owner: 'devsecops-test',
                        repositoryName: 'vulnado'),
                    jira(
                        assignee: 'rahulgu@synopsys.com', 
                        configName: 'SIG-JIRA-Demo', 
                        issueQuery: 'resolution=Unresolved', 
                        projectKey: 'VUL', 
                        projectName: 'VUL'),
                    buildBreaker(configName: 'BB-Custom')]) {
                        sh 'io --stage io'
                    }

                script {
                    def prescriptionJSON = readJSON file: 'io_state.json'

                    print("Business Criticality Score: $prescriptionJSON.Data.Prescription.RiskScore.BusinessCriticalityScore")
                    print("Data Class Score: $prescriptionJSON.Data.Prescription.RiskScore.DataClassScore")
                    print("Access Score: $prescriptionJSON.Data.Prescription.RiskScore.AccessScore")
                    print("Open Vulnerability Score: $prescriptionJSON.Data.Prescription.RiskScore.OpenVulnerabilityScore")
                    print("Change Significance Score: $prescriptionJSON.Data.Prescription.RiskScore.ChangeSignificanceScore")
                    print("Tooling Score: $prescriptionJSON.Data.Prescription.RiskScore.ToolingScore")
                    print("Training Score: $prescriptionJSON.Data.Prescription.RiskScore.TrainingScore")

                    isSASTEnabled = prescriptionJSON.Data.Prescription.Security.Activities.Sast.Enabled
                    isSASTPlusMEnabled = prescriptionJSON.Data.Prescription.Security.Activities.SastPlusM.Enabled
                    isSCAEnabled = prescriptionJSON.Data.Prescription.Security.Activities.Sca.Enabled
                    isDASTEnabled = prescriptionJSON.Data.Prescription.Security.Activities.Dast.Enabled
                    isDASTPlusMEnabled = prescriptionJSON.Data.Prescription.Security.Activities.DastPlusM.Enabled
                    isImageScanEnabled = prescriptionJSON.Data.Prescription.Security.Activities.ImageScan.Enabled

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
                echo env.OSTYPE
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
                    projectName: 'sig-devsecops/vulnado']]) {
                    sh 'io --stage execution --state io_state.json'
                }
            }
        }

        stage('SAST Plus Manual') {
            when {
                expression { isSASTPlusMEnabled }
            }
            steps {
                script {
                    env.COMMENT = input message: 'Manual source code review (SAST - Manual) triggered by IO. Proceed?'
                }
                echo "Out-of-Band Activity - SAST Plus Manual triggered & approved"
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
                  projectName: 'vulnado',
                  projectVersion: '1.0')]) {
                  sh 'io --stage execution --state io_state.json'
              }
            }
        }

        stage('DAST Plus Manual') {
            when {
                expression { isDASTPlusMEnabled }
            }
            steps {
                script {
                    env.COMMENT = input message: 'Manual threat-modeling (DAST - Manual) triggered by IO. Proceed?'
                }
                echo "Out-of-Band Activity - DAST Plus Manual triggered & approved"
            }
        }

        stage('IO - Workflow') {
            steps {
                echo 'Execute Workflow Stage'
                synopsysIO(connectors: [
                    codeDx(configName: 'SIG-CodeDx', projectId: '3'), 
                    jira(assignee: 'rahulgu@synopsys.com', configName: 'SIG-JIRA-Demo', issueQuery: 'resolution=Unresolved AND labels in (Security, Defect)', projectKey: 'VUL', projectName: 'VUL'), 
                    msteams(configName: 'io-bot'), 
                    buildBreaker(configName: 'BB-Custom')
                ]) {
                    sh 'io --stage workflow --state io_state.json'
                }
                
                script {
                    def workflowJSON = readJSON file: 'wf-output.json'
                    print("========================== IO WorkflowEngine Summary ============================")
                    print("Breaker Status: $workflowJSON.breaker.status")
                    print("CodeDX Score: $workflowJSON.breaker.criteria.risk_score")
                    codedx_value = workflowJSON.breaker.criteria.risk_score
                    if(codedx_value =! "null")
                    {   
                        print("CodeDX Score: $codedx_value")
                    }
                    if(workflowJSON.summary.activity == "codedx")
                    {
                        print("CodeDX Score: $workflowJSON.summary.risk_score")
                    }
                }
            }
        }
    }

    post {
        always {
            // Archive Results & Logs
            archiveArtifacts artifacts: '**/*-results*.json', allowEmptyArchive: 'true'

            // Remove the state json file as it has sensitive information
            sh 'rm io_state.json'
        }
    }
}
