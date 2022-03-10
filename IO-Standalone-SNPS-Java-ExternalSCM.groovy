pipeline {
    agent any

    tools {
        maven 'Maven3'
    }

    environment {
        SYNOPSYS_IO_Scm_CodePatch = sh(script: 'git diff HEAD^ HEAD', , returnStdout: true)
    }

    stages {
        stage('Checkout Source Code') {
            steps {
                git branch: 'master', url: 'https://bitbucket.org/1nv1n/spring-boot-demo'
            }
        }

        stage('Build Source Code') {
            steps {
                sh '''mvn clean compile'''
            }
        }

        stage('IO - Prescription') {
            steps {
                synopsysIO() {
                        sh 'io --version'
                        sh "io --stage io --verbose \
                            scm.Type=external \
                            Project.Name='bb-spring-sample' \
                            Project.Application.Name='spring-boot-demo' \
                            Persona.Type='devsecops' \
                            Io.Server.Token=$IOToken \
                            Io.Server.Url='http://23.99.131.170'"
                    }

                script {
                    def prescriptionJSON = readJSON file: 'io_state.json'

                    print("Business Criticality Score: $prescriptionJSON.data.prescription.riskScore.businessCriticalityScore")
                    print("Data Class Score: $prescriptionJSON.data.prescription.riskScore.dataClassScore")
                    print("Access Score: $prescriptionJSON.data.prescription.riskScore.accessScore")
                    print("Open Vulnerability Score: $prescriptionJSON.data.prescription.riskScore.openVulnerabilityScore")
                    print("Change Significance Score: $prescriptionJSON.data.prescription.riskScore.changeSignificanceScore")
                    print("Tooling Score: $prescriptionJSON.data.prescription.riskScore.toolingScore")
                    print("Training Score: $prescriptionJSON.data.prescription.riskScore.trainingScore")

                    isSASTEnabled = prescriptionJSON.data.prescription.security.activities.sast.enabled
                    isSASTPlusMEnabled = prescriptionJSON.data.prescription.security.activities.sastPlusM.enabled
                    isSCAEnabled = prescriptionJSON.data.prescription.security.activities.sca.enabled
                    isDASTEnabled = prescriptionJSON.data.prescription.security.activities.dast.enabled
                    isDASTPlusMEnabled = prescriptionJSON.data.prescription.security.activities.dastPlusM.enabled
                    isImageScanEnabled = prescriptionJSON.data.prescription.security.activities.imageScan.enabled

                    print("SAST Enabled: $isSASTEnabled")
                    print("SAST+Manual Enabled: $isSASTPlusMEnabled")
                    print("SCA Enabled: $isSCAEnabled")
                    print("DAST Enabled: $isDASTEnabled")
                    print("DAST+Manual Enabled: $isDASTPlusMEnabled")
                    print("ImageScan Enabled: $isImageScanEnabled")
                }
            }
        }
    }
}
