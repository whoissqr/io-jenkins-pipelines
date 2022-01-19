#! /bin/groovy
package com.synopsys.pipeline

import com.synopsys.util.BuildUtil

/**
 * This script is the entry point for the shared library pipeline.
 * It defines pipeline stages and manages overall control flow in the pipeline.
 */
def execute() {
    def isSASTEnabled = false
    def isSCAEnabled = false

    node('master') {
        stage('Checkout Code') {
            git branch: 'devsecops', url: 'https://github.com/devsecops-test/vulnado'
        }

        stage('Build Source Code') {
            def buildUtil = new BuildUtil(this)
            buildUtil.mvn 'clean compile > mvn-install.log'
        }

        stage('IO - Setup Prescription') {
            echo 'Setup Prescription'
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

        stage('IO - Read Prescription') {
            def prescriptionJSON = readJSON file: 'io_state.json'
            print("Updated Prescription JSON :\n$prescriptionJSON\n")
            print("SAST Enabled: $prescriptionJSON.Data.Prescription.Security.Activities.Sast.Enabled")
            print("SCA Enabled: $prescriptionJSON.Data.Prescription.Security.Activities.Sca.Enabled")
            print("BusinessCriticalityScore: $prescriptionJSON.Data.Prescription.RiskScore.BusinessCriticalityScore")
            print("DataClassScore: $prescriptionJSON.Data.Prescription.RiskScore.DataClassScore")
            print("AccessScore: $prescriptionJSON.Data.Prescription.RiskScore.AccessScore")
            print("ToolingScore: $prescriptionJSON.Data.Prescription.RiskScore.ToolingScore")
            print("TrainingScore: $prescriptionJSON.Data.Prescription.RiskScore.TrainingScore")

            isSASTEnabled = prescriptionJSON.Data.Prescription.Security.Activities.Sast.Enabled
            isSCAEnabled = prescriptionJSON.Data.Prescription.Security.Activities.Sca.Enabled

            isSASTEnabled = false
        }

        if (isSASTEnabled) {
            stage('SAST - RapidScan') {
                echo 'Running SAST using Sigma - Rapid Scan'
                synopsysIO(connectors: [rapidScan(configName: 'Sigma')]) {
                    sh 'io --stage execution --state io_state.json'
                }
            }
        } else {
            echo 'SAST not enabled, skipping RapidScan'
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
            }
        }

        stage('SCA - Black Duck') {
            if (isSCAEnabled) {
                echo 'Running SCA using Black Duck'
                synopsysIO(connectors: [
                    blackduck(configName: 'BIZDevBD',
                    projectName: 'vulnado',
                    projectVersion: '1.0')]) {
                    sh 'io --stage execution --state io_state.json'
                    }
            }
        }

        stage('IO - Workflow') {
            echo 'Execute Workflow Stage'
            synopsysIO() {
                synopsysIO() {
                    sh 'io --stage workflow --state io_state.json'
                }
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
