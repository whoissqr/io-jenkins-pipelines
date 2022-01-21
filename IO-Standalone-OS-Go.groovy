def isSASTEnabled
def isSASTPlusMEnabled
def isSCAEnabled
def isDASTEnabled
def isDASTPlusMEnabled

pipeline {
    agent any
    tools {
        go 'Go'
    }
    stages {
        stage('Checkout Source Code') {
            steps {
                git branch: 'devsecops', url: 'https://github.com/devsecops-test/govwa'
            }
        }

        stage('Build Source Code') {
            steps {
                sh 'go build'
            }
        }

        stage('IO - Prescription') {
            steps {
                synopsysIO(connectors: [
                    io(
                        configName: 'io-azure',
                        projectName: 'devsecops-vulnerable-govwa',
                        workflowVersion: '2021.12.2'),
                    github(
                        branch: 'devsecops',
                        configName: 'github-devsecops',
                        owner: 'devsecops-test',
                        repositoryName: 'govwa'),
                    buildBreaker(configName: 'BB-ALL')]) {
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

                    print("SAST Enabled: $isSASTEnabled")
                    print("SAST+Manual Enabled: $isSASTPlusMEnabled")
                    print("SCA Enabled: $isSCAEnabled")
                    print("DAST Enabled: $isDASTEnabled")
                    print("DAST+Manual Enabled: $isDASTPlusMEnabled")
                }
            }
        }

        stage('SAST - GoSec') {
            when {
                expression { isSASTEnabled }
            }
            steps {
                echo 'Running SAST using GoSec'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/gosec/gosec-adapter.json --output gosec-adapter.json'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/gosec/gosec.sh --output gosec.sh'
                synopsysIO() {
                    sh 'io --stage execution --adapters gosec-adapter.json --state io_state.json || true'
                }
            }
        }

        stage('SAST Plus Manual') {
            when {
                expression { isSASTPlusMEnabled }
            }
            input {
                message "Major code change detected, manual code review (SAST - Manual) triggerd. Proceed?"
                ok "Approve"
                parameters {
                    string(name: 'Comment', defaultValue: 'Approved', description: 'Approval Comment.')
                }
            }
            steps {
                echo "Out-of-Band Activity - SAST Plus Manual triggered & approved with comment: {$Comment}."
            }
        }

        stage('DAST Plus Manual') {
            when {
                expression { isDASTPlusMEnabled }
            }
            input {
                message "Major code change detected, manual threat-modeling (DAST - Manual) triggerd. Proceed?"
                ok "Approve"
                parameters {
                    string(name: 'Comment', defaultValue: 'Approved', description: 'Approval Comment.')
                }
            }
            steps {
                echo "Out-of-Band Activity - DAST Plus Manual triggered & approved with comment: {$Comment}."
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
            archiveArtifacts artifacts: 'gosec-results.json', allowEmptyArchive: 'true'

            // Remove the state json file as it has sensitive information
            sh 'rm io_state.json'
        }
    }
}
