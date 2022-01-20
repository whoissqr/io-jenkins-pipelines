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
                def prescriptionJSON = readJSON file: 'io_state.json'
                print("Updated Prescription JSON :\n$prescriptionJSON\n")
                print("SAST Enabled: $prescriptionJSON.Data.Prescription.Security.Activities.Sast.Enabled")
                print("SCA Enabled: $prescriptionJSON.Data.Prescription.Security.Activities.Sca.Enabled")
                print("BusinessCriticalityScore: $prescriptionJSON.Data.Prescription.RiskScore.BusinessCriticalityScore")
                print("DataClassScore: $prescriptionJSON.Data.Prescription.RiskScore.DataClassScore")
                print("AccessScore: $prescriptionJSON.Data.Prescription.RiskScore.AccessScore")
                print("ToolingScore: $prescriptionJSON.Data.Prescription.RiskScore.ToolingScore")
                print("TrainingScore: $prescriptionJSON.Data.Prescription.RiskScore.TrainingScore")
            }
        }

        stage('SAST - SpotBugs') {
            when {
                isSASTEnabled = prescriptionJSON.Data.Prescription.Security.Activities.Sast.Enabled
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
