pipeline {
    agent any
    tools {
        maven 'NodeJS'
    }
    stages {
        stage('Checkout Source Code') {
            steps {
                git branch: 'devsecops', url: 'https://github.com/devsecops-test/vulnerable-node'
            }
        }

        stage('Build Source Code') {
            steps {
                sh 'npm install > npm-install.log'
            }
        }

        stage('IO - Prescription') {
            steps {
                synopsysIO(connectors: [
                    io(
                        configName: 'io-azure',
                        projectName: 'devsecops-vulnerable-node',
                        workflowVersion: '2021.12.2'),
                    github(
                        branch: 'devsecops',
                        configName: 'github-devsecops',
                        owner: 'devsecops-test',
                        repositoryName: 'vulnerable-node'),
                    buildBreaker(configName: 'BB-ALL')]) {
                        sh 'io --stage io'
                    }
            }
        }

        stage('SAST - ESLint') {
            steps {
                echo 'Running SAST using ESLint'
                synopsysIO() {
                    sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/eslint/eslint-adapter.json --output eslint-adapter.json'
                    sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/eslint/eslint.sh --output eslint.sh'
                    sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/eslint/eslintrc.json --output .eslintrc.json'
                    sh 'io --stage execution --adapters eslint-adapter.json --state io_state.json || true'
                }
            }
        }

        stage('SCA - NPM Audit') {
            steps {
                echo 'Running SCA using NPM Audit'
                sh 'npm audit > npm-audit.log || true'
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
            archiveArtifacts artifacts: '**/*-results*.json', allowEmptyArchive: 'true'
            archiveArtifacts artifacts: '.io/**', allowEmptyArchive: 'true'
            archiveArtifacts artifacts: 'npm-install.log', allowEmptyArchive: 'true'
            archiveArtifacts artifacts: 'npm-audit.log', allowEmptyArchive: 'true'
            archiveArtifacts artifacts: 'npm-eslint.log', allowEmptyArchive: 'true'

            // Remove the state json file as it has sensitive information
            sh 'rm io_state.json'
        }
    }
}
