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
            git branch: 'devsecops', url: 'https://github.com/devsecops-test/vulnado'
        }

        stage('Build Source Code') {
            def buildUtil = new BuildUtil(this)
            buildUtil.mvn 'clean compile > mvn-install.log'
        }

        stage('IO - Prescription') {
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
            } else {
                echo 'SAST not enabled, skipping Sigma - RapidScan'
            }
        }

        stage('SAST - Polaris') {
            if (isSASTEnabled) {
                echo 'Running SAST using Polaris'
                synopsysIO(connectors: [
                    [$class: 'PolarisPipelineConfig',
                    configName: 'csprod-polaris',
                    projectName: 'sig-devsecops/vulnado']]) {
                        sh 'io --stage execution --state io_state.json'
                    }
            } else {
                echo 'SAST not enabled, skipping Polaris'
            }
        }

        stage('SCA - BlackDuck') {
            if (isSCAEnabled) {
                echo 'Running SCA using BlackDuck'
                synopsysIO(connectors: [
                    blackduck(configName: 'BIZDevBD',
                    projectName: 'vulnado',
                    projectVersion: '1.0')]) {
                        sh 'io --stage execution --state io_state.json'
                    }
            } else {
                echo 'SCA not enabled, skipping BlackDuck'
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

            // Remove the state json file as it has sensitive information
            sh 'rm io_state.json'
        }
    }
}
