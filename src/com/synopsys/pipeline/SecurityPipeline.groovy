#! /bin/groovy
package com.synopsys.pipeline

import com.synopsys.util.BuildUtil

/**
 * This script is the entry point for the shared library pipeline.
 * It defines pipeline stages and manages overall control flow in the pipeline.
 */
def execute() {
    def isSASTEnabled = false
    def isSASTPlusMEnabled = false
    def isSCAEnabled = false
    def isDASTEnabled = false
    def isDASTPlusMEnabled = false

    node('master') {
        stage('Checkout Code') {
            git branch: "${SCMBranch}", url: "${SCMURL}"
        }

        stage('Build Source Code') {
            def buildUtil = new BuildUtil(this)
            buildUtil.mvn 'clean compile > mvn-install.log'
        }

        stage('IO - Prescription') {
            synopsysIO(connectors: [
                io(
                    configName: "${IOConfigName}",
                    projectName: "${IOProjectName}",
                    workflowVersion: "${IOWorkflowVersion}"),
                github(
                    branch: "${SCMBranch}",
                    configName: "${GitHubConfigName}",
                    owner: "${GitHubOwner}",
                    repositoryName: "${GitHubRepositoryName}"),
                buildBreaker(configName: "${BuildBreakerConfigName}")]) {
                    sh 'io --stage io'
                }

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

        stage('SAST - Sigma - RapidScan') {
            if (isSASTEnabled) {
                echo 'Running SAST using Sigma - Rapid Scan'
                synopsysIO(connectors: [rapidScan(configName: 'Sigma')]) {
                    sh 'io --stage execution --state io_state.json'
                }
            }
        }

        stage('SAST - Polaris') {
            if (isSASTEnabled) {
                echo 'Running SAST using Polaris'
                synopsysIO(connectors: [
                    [$class: "${PolarisClassName}",
                    configName: "${PolarisConfigName}",
                    projectName: "${PolarisProjectName}"]]) {
                        sh 'io --stage execution --state io_state.json'
                    }
            }
        }

        stage('SAST - SpotBugs') {
            if (isSASTEnabled && params.SpotBugs) {
                echo 'Running SAST using SpotBugs'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/spotbugs/spotbugs-adapter.json --output spotbugs-adapter.json'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/spotbugs/spotbugs.sh --output spotbugs.sh'
                synopsysIO() {
                    sh 'io --stage execution --adapters spotbugs-adapter.json --state io_state.json'
                }
            }
        }

        stage('SAST Plus Manual') {
            if (isSASTPlusMEnabled) {
                def userInput = input message: 'Major code change detected, manual code review (SAST - Manual) triggerd. Proceed?',
                    ok: 'Go ahead',
                    parameters: [string(name: 'Comment', defaultValue: 'Approved', description: 'Approval Comment.')]

                echo "Out-of-Band Activity - SAST Plus Manual triggered & approved with comment: ${userInput}"
            }
        }

        stage('SCA - BlackDuck') {
            if (isSCAEnabled) {
                echo 'Running SCA using BlackDuck'
                synopsysIO(connectors: [
                    blackduck(configName: "${BlackDuckConfigName}",
                    projectName: "${BlackDuckProjectName}",
                    projectVersion: "${BlackDuckProjectVersion}")]) {
                        sh 'io --stage execution --state io_state.json'
                    }
            }
        }

        stage('SCA - Dependency-Check') {
            if (isSCAEnabled && params.DependencyCheck) {
                echo 'Running SCA using Dependency-Check'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/dependency-check/dependency-check-adapter.json --output dependency-check-adapter.json'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/dependency-check/dependency-check.sh --output dependency-check.sh'
                synopsysIO() {
                    sh 'io --stage execution --adapters dependency-check-adapter.json --state io_state.json'
                }
            }
        }

        stage('DAST Plus Manual') {
            if (isDASTPlusMEnabled) {
                def userInput = input message: 'Major code change detected, manual threat-modeling (DAST - Manual) triggerd. Proceed?',
                    ok: 'Go ahead',
                    parameters: [string(name: 'Comment', defaultValue: 'Approved', description: 'Approval Comment.')]

                echo "Out-of-Band Activity - DAST Plus Manual triggered & approved with comment: ${userInput}"
            }
        }

        stage('IO - Workflow') {
            echo 'Execute Workflow Stage'
            synopsysIO() {
                sh 'io --stage workflow --state io_state.json'
            }
        }

        stage('IO - Archive') {
            // Archive Results & Logs
            archiveArtifacts artifacts: '**/*-results*.json', allowEmptyArchive: 'true'
            archiveArtifacts artifacts: 'mvn-install.log', allowEmptyArchive: 'true'
            archiveArtifacts artifacts: 'spotbugs-report.html', allowEmptyArchive: 'true'
            archiveArtifacts artifacts: 'dependency-check-report.html', allowEmptyArchive: 'true'
            archiveArtifacts artifacts: 'io_state.json', allowEmptyArchive: 'true'

            // Remove the state json file as it has sensitive information
            sh 'rm io_state.json'
        }
    }
}
