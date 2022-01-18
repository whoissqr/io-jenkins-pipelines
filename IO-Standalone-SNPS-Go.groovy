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
            }
        }

        stage('SAST - RapidScan (Sigma)') {
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

        stage('SCA - BlackDuck') {
            steps {
              echo 'Running SCA using BlackDuck'
              synopsysIO(connectors: [
                  blackduck(configName: 'BIZDevBD',
                  projectName: 'govwa',
                  projectVersion: '1.0')]) {
                  sh 'io --stage execution --state io_state.json'
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
            archiveArtifacts artifacts: '.io/**', allowEmptyArchive: 'true'
            archiveArtifacts artifacts: '**/*-results*.json', allowEmptyArchive: 'true'

            // Remove the state json file as it has sensitive information
            sh 'rm io_state.json'
        }
    }
}
