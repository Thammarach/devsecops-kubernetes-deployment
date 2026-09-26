// vars/mainPipeline.groovy
def call() {

    stage('check') {
        sh 'rm -rf helm-application-livingos template-helm scan-report.html sonar-report.html'
        sh 'echo $JOB_NAME'
        sh 'echo $WORKSPACE'
        // sh 'echo ${IMAGE_NAME}:${TAG}'
        sh 'echo ${PROJECT}'
        sh 'echo ${DOCKERFILE}'
    }

    stage('Check Branch start') {
        echo "Start pipeline from: ${BRANCH_NAME}"
        if (!(BRANCH_NAME == 'develop' || env.GIT_TAG || BRANCH_NAME ==~ /^v-\d+\.\d+\.\d+$/)) { 
            currentBuild.result = 'SUCCESS'
            error("Stop pipeline because branch is ${BRANCH_NAME}")
        }
    }

    stage('Code Analysis with SonarQube') {
        if (env.BRANCH_NAME in ['develop', 'main'] || env.GIT_TAG || BRANCH_NAME ==~ /^v-\d+\.\d+\.\d+$/ ) {
            withCredentials([string(credentialsId: 'SONAR_TOKEN', variable: 'SONAR_TOKEN')]) {
                sh """
                    mvn clean verify sonar:sonar \
                        -Dsonar.host.url=${SONAR_HOST_URL} \
                        -Dsonar.projectKey=${NAME_REPO} \
                        -Dsonar.branch.target=${BRANCH_NAME} \
                        -Dsonar.login=$SONAR_TOKEN
                """
            }

            def sonarLink = "${SONAR_HOST_URL}/dashboard?id=${NAME_REPO}"
            echo "SonarQube Report URL: ${sonarLink}"

            // หรือ publish เป็น HTML เล็ก ๆ
            writeFile file: "${WORKSPACE}/sonar-report.html", text: """
                <html>
                    <body>
                        <h2>SonarQube Report</h2>
                        <p><a href="${sonarLink}" target="_blank">${sonarLink}</a></p>
                    </body>
                </html>
            """
            publishHTML([
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: "${WORKSPACE}",
                reportFiles: "sonar-report.html",
                reportName: "SonarQube Report"
            ])
        }
    }

    stage('Scan Source') {
        script {
            if (env.BRANCH_NAME == 'develop' || env.GIT_TAG || BRANCH_NAME ==~ /^v-\d+\.\d+\.\d+$/) {
                sh 'rm -rf trivy-source-report'
                sh 'mkdir -p trivy-source-report'
                sh """
                    curl -sSL -o html.tpl https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl
                    trivy fs --format template --template "@html.tpl" \
                        -o trivy-source-report/trivy-source-report.html .
                """

                publishHTML(target: [
                    reportName: 'Source Code Report',
                    reportDir: 'trivy-source-report',
                    reportFiles: 'trivy-source-report.html',
                    keepAll: true,
                    alwaysLinkToLastBuild: true,
                    allowMissing: false
                ])
            } else {
                echo "Skipping Scan Source ${env.BRANCH_NAME}"
            }
        }
    }

    stage('Scan Secret On Repo') {
        script {
            if (env.BRANCH_NAME == 'develop' || env.BRANCH_NAME == 'main' || env.GIT_TAG || BRANCH_NAME ==~ /^v-\d+\.\d+\.\d+$/) {
                try {
                    echo "Running Gitleaks via Docker..."
                    sh 'rm -rf $WORKSPACE/gitleaks-report.json || true'
                    sh 'rm -rf $WORKSPACE/gitleaks-report.html || true'
                    sh 'rm -rf $WORKSPACE/trivy-report || true'
                    sh '''
                        docker run --rm \
                        -v $WORKSPACE:/path \
                        gitleaks/gitleaks:latest detect \
                        --source /path \
                        --no-git \
                        --report-format json \
                        --report-path /path/gitleaks-report.json
                    '''

                    sh '''
                        if [ -f $WORKSPACE/gitleaks-report.json ]; then
                            echo "<html><body><h2>Gitleaks Report</h2><pre>" > $WORKSPACE/gitleaks-report.html
                            cat $WORKSPACE/gitleaks-report.json >> $WORKSPACE/gitleaks-report.html
                            echo "</pre></body></html>" >> $WORKSPACE/gitleaks-report.html
                        fi
                    '''

                    echo "Publishing Gitleaks HTML Report..."
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: "$WORKSPACE",
                        reportFiles: "gitleaks-report.html",
                        reportName: "Secret Scan Report"
                    ])
                } catch (Exception e) {
                    sh '''
                        if [ -f $WORKSPACE/gitleaks-report.json ]; then
                            echo "<html><body><h2>Gitleaks Report</h2><pre>" > $WORKSPACE/gitleaks-report.html
                            cat $WORKSPACE/gitleaks-report.json >> $WORKSPACE/gitleaks-report.html
                            echo "</pre></body></html>" >> $WORKSPACE/gitleaks-report.html
                        fi
                    '''

                    echo "Publishing Gitleaks HTML Report..."
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: "$WORKSPACE",
                        reportFiles: "gitleaks-report.html",
                        reportName: "Gitleaks Secret Scan Report"
                    ])
                    error("Secrets found, stopping pipeline.")
                }
            } else {
                echo "Skipping secret scan for branch ${env.BRANCH_NAME}"
            }
        }
    }

    stage('Set Tags Name') {
        if (env.BRANCH_NAME == 'develop' || env.GIT_TAG || BRANCH_NAME ==~ /^v-\d+\.\d+\.\d+$/ ) {
            sh 'echo ${IMAGE_NAME}:${TAG}'
        }
    }

    stage('Build') {
        if (env.BRANCH_NAME == 'develop' || env.GIT_TAG || BRANCH_NAME ==~ /^v-\d+\.\d+\.\d+$/  ) {
            withCredentials([
                file(credentialsId: GITLAB_USERNAME, variable: 'GITLAB_USERNAME'),
                file(credentialsId: GITLAB_PASSWORD, variable: 'GITLAB_PASSWORD')
            ]) {
                sh "docker login -u ${GITLAB_USERNAME} -p ${GITLAB_PASSWORD} $REGISTORY_URL"
                sh "docker build -t ${IMAGE_NAME}:${TAG} -f ${DOCKERFILE} ."
            }
        }
    }

    stage('Scan Docker Image') {
        script {
            if (env.BRANCH_NAME == 'develop' || env.GIT_TAG || BRANCH_NAME ==~ /^v-\d+\.\d+\.\d+$/  ) {
                sh 'mkdir -p trivy-report'
                sh """
                    curl -sSL -o html.tpl https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl
                    trivy image --format template --template "@html.tpl" \
                        -o trivy-report/trivy-report.html ${IMAGE_NAME}:${TAG}
                """

                publishHTML(target: [
                    reportName: 'Scan Images Report',
                    reportDir: 'trivy-report',
                    reportFiles: 'trivy-report.html',
                    keepAll: true,
                    alwaysLinkToLastBuild: true,
                    allowMissing: false
                ])
            } else {
                echo "Skipping Trivy scan because branch is ${env.BRANCH_NAME}"
            }
        }
    }

    stage('Push') {
        if (env.BRANCH_NAME == 'develop' || env.GIT_TAG || BRANCH_NAME ==~ /^v-\d+\.\d+\.\d+$/  ) {
            withCredentials([
                file(credentialsId: GITLAB_USERNAME, variable: 'GITLAB_USERNAME'),
                file(credentialsId: GITLAB_PASSWORD, variable: 'GITLAB_PASSWORD')
            ]) {
                sh "docker push ${IMAGE_NAME}:${TAG}"
            }
        }
    }

    stage('Remove Images') {
        if (env.BRANCH_NAME == 'develop' || env.GIT_TAG || BRANCH_NAME ==~ /^v-\d+\.\d+\.\d+$/  ) {
            sh "docker rmi -f ${IMAGE_NAME}:${TAG}"
        }
    }

    stage('change version') {
        if (env.BRANCH_NAME == 'develop' || env.GIT_TAG || BRANCH_NAME ==~ /^v-\d+\.\d+\.\d+$/ ) {
            withCredentials([string(credentialsId: 'GITLAB_TOKEN', variable: 'GITLAB_TOKEN')]) {
                sh """
                    rm -rf helm-application template-helm
                    git clone https://:${GITLAB_TOKEN}@gitlab.com/platform-team/helm-application.git
                    cd $WORKSPACE/helm-application && chmod +x ./build-jenkins.sh && ./build-jenkins.sh '$WORKSPACE' '$WORKSPACE/helm-application' ${IMAGE_NAME} $PROJECT_NAME
                """
            }
        }
    }

    stage('Waiting') {
        if (env.BRANCH_NAME == 'develop' ||  env.SCAN_URL == 'true') {
                echo "⏳ Waiting for 30 seconds before continuing..."
                sleep(time: 30, unit: 'SECONDS')
        }
    }

    stage('OWASP Dependency Check') {
        script {
            if (env.BRANCH_NAME == 'develop' ||  env.SCAN_URL == 'true' ) {
                def localhost_url = "${DOMAIN_NONPRD}"
                def reportFile = "scan-report.html"

                try {
                    echo "Starting container for OWASP ZAP Scan..."
                    echo "Running OWASP ZAP Baseline Scan..."
                    echo "localhost_url : ${localhost_url}"
                    sh """
                        rm -rf wrk
                        mkdir wrk
                        chmod -R 777 ${WORKSPACE}/wrk
                        docker run --rm \
                            -v ${WORKSPACE}/wrk:/zap/wrk:rw \
                            --network="host" \
                            zaproxy/zap-stable zap-api-scan.py \
                            -t ${localhost_url} -f openapi \
                            -r ${reportFile} || true
                    """

                    sh """
                    mv ${WORKSPACE}/wrk/scan-report.html ${WORKSPACE}/scan-report.html
                    """

                    echo "Stop container for OWASP ZAP Scan..."
                    echo "Publishing OWASP ZAP Report..."
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: ".",
                        reportFiles: reportFile,
                        reportName: "OWASP ZAP Security Report"
                    ])
                } finally {
                    echo "Cleaning up OWASP container..."
                }
            } else {
                echo "Skipping OWASP scan for branch ${env.BRANCH_NAME}"
            }
        }
    }

}
