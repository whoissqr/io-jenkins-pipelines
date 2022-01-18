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

        stage('SAST - RapidScan (Sigma)') {
            when {
                expression { params.SAST == 'Sigma' }
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
                expression { params.SAST == 'Polaris' }
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

        stage('SAST - Coverity') {
            when {
                expression { params.SAST == 'Coverity' }
            }
            steps {
                echo 'TODO - Coverity implementation'
            }
        }

        stage('SAST - SpotBugs') {
            when {
                expression { params.SAST == 'SpotBugs' }
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

        stage('SCA - BlackDuck') {
            when {
                expression { params.SCA == 'BlackDuck' }
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

        stage('SCA - Dependency-Check') {
            when {
                expression { params.SCA == 'DependencyCheck' }
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

        stage('DAST - OWASP ZAP') {
            when {
                expression { params.DAST == 'OWASP-ZAP' }
            }
            steps {
                echo 'TODO - OWASP-ZAP implementation'
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
            if (fileExists('**/*-results*.json')) {
                archiveArtifacts artifacts: '**/*-results*.json', allowEmptyArchive: 'true'
            }

            archiveArtifacts artifacts: '.io/**', allowEmptyArchive: 'true'

            // Remove the state json file as it has sensitive information
            sh 'rm io_state.json'
        }
    }
}
