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
                    buildBreaker(configName: 'BB-ALL')]) {
                        sh 'io --stage io'
                    }
            }
        }

        stage('IO - Read Prescription') {
            steps {
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

                    isSASTEnabled = false;

                    print("SAST Enabled: $isSASTEnabled")
                    print("SAST+Manual Enabled: $isSASTPlusMEnabled")
                    print("SCA Enabled: $isSCAEnabled")
                    print("DAST Enabled: $isDASTEnabled")
                    print("DAST+Manual Enabled: $isDASTPlusMEnabled")
                }
            }
        }

        stage('SAST - SpotBugs') {
            when {
                expression { isSASTEnabled }
            }
            steps {
                echo 'Running SAST using SpotBugs'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/spotbugs/spotbugs-adapter.json --output spotbugs-adapter.json'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/spotbugs/spotbugs.sh --output spotbugs.sh'
                synopsysIO() {
                    sh 'io --stage execution --adapters spotbugs-adapter.json --state io_state.json'
                }
            }
        }

        stage('Out-of-Band Activity - SAST Plus Manual') {
            when {
                conditional { isSASTPlusMEnabled }
            }
            input {
                message "Major code change detected, manual code review (SAST - Manual) triggerd. Proceed?"
                ok "Approve"
                parameters {
                    string(name: 'ApprovalComment', defaultValue: 'Approved', description: 'Approval Comment.')
                }
            }
            steps {
                echo "$Out-of-Band Activity - SAST Plus Manual triggered & approved with comment: {$ApprovalComment}."
            }
        }

        stage('SCA - Dependency-Check') {
            when {
                expression { isSCAEnabled }
            }
            steps {
                echo 'Running SCA using Dependency-Check'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/dependency-check/dependency-check-adapter.json --output dependency-check-adapter.json'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/dependency-check/dependency-check.sh --output dependency-check.sh'
                synopsysIO() {
                    sh 'io --stage execution --adapters dependency-check-adapter.json --state io_state.json'
                }
            }
        }

        stage('IO - Workflow') {
            steps {
                echo 'Execute Workflow Stage'
                synopsysIO() {
                    sh 'io --stage workflow --state io_state.json'
                }
            }
        }
    }

    post {
        always {
            // Archive Results & Logs
            archiveArtifacts artifacts: 'spotbugs-report.html', allowEmptyArchive: 'true'
            archiveArtifacts artifacts: 'dependency-check-report.html', allowEmptyArchive: 'true'

            // Remove the state json file as it has sensitive information
            sh 'rm io_state.json'
        }
    }
}
