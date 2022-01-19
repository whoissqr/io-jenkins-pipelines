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

        stage('SAST - GoSec') {
            steps {
                echo 'Running SAST using GoSec'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/gosec/gosec-adapter.json --output gosec-adapter.json'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/gosec/gosec.sh --output gosec.sh'
                synopsysIO() {
                    sh 'io --stage execution --adapters gosec-adapter.json --state io_state.json || true'
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
            archiveArtifacts artifacts: 'gosec-results.json', allowEmptyArchive: 'true'

            // Remove the state json file as it has sensitive information
            sh 'rm io_state.json'
        }
    }
}
