pipeline {
    agent any

    options {
        timestamps()
        ansiColor('xterm')
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        string(name: 'DOCKERHUB_USER', defaultValue: 'sparebuddy',
               description: 'Your Docker Hub username (also the image namespace).')
        booleanParam(name: 'MASTER_CD', defaultValue: true,
               description: 'Master CD path: deploy to the cluster (pods -> Fluent Bit -> ES) and run MAT/regression. Turn off on feature branches to build + push images only, keeping Elasticsearch free of non-master pod logs.')
    }

    tools {
        maven 'Maven-3.9'
    }

    environment {
        IMAGE_NAME   = "${params.DOCKERHUB_USER}/weather-api"
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
        IMAGE        = "${IMAGE_NAME}:${IMAGE_TAG}"
        VALIDATION_IMAGE_NAME = "${params.DOCKERHUB_USER}/validation-service"
        VALIDATION_IMAGE      = "${VALIDATION_IMAGE_NAME}:${IMAGE_TAG}"
        RUN_ID       = "${env.JOB_NAME}-${env.BUILD_NUMBER}"
        APP_BASE_URL = "http://127.0.0.1:30080"
        ES_URL       = "http://host.docker.internal:9200"
        LOG_DIR      = "${env.WORKSPACE}/logs"
        ENV          = "ci"
        KUBE_NS      = "weather"
        KUBECONFIG   = "/var/jenkins_home/.kube/config"
        PATH         = "/usr/local/bin:/usr/bin:/bin:${env.PATH}"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                sh 'mkdir -p "$LOG_DIR"'
            }
        }

        stage('Build') {
            steps {
                echo "[stage=build run=${RUN_ID}] mvn package"
                sh 'mvn -B -ntp clean package -DskipTests'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/weather-api.jar', fingerprint: true
                }
            }
        }

        stage('Docker build & push') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub',
                                                  usernameVariable: 'DH_USER',
                                                  passwordVariable: 'DH_PASS')]) {
                    sh '''
                        # Image namespace must match the logged-in Docker Hub user.
                        export IMAGE_NAME="${DH_USER}/weather-api"
                        export IMAGE="${IMAGE_NAME}:${BUILD_NUMBER}"
                        export VAL_IMAGE_NAME="${DH_USER}/validation-service"
                        export VAL_IMAGE="${VAL_IMAGE_NAME}:${BUILD_NUMBER}"
                        echo "[stage=docker run=${RUN_ID}] building ${IMAGE} and ${VAL_IMAGE}"
                        echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin

                        # weather-api (MS-1)
                        docker build -t "$IMAGE" -t "$IMAGE_NAME:latest" .
                        docker push "$IMAGE"
                        docker push "$IMAGE_NAME:latest"
                        echo "$IMAGE" > pushed-image.txt

                        # validation-service (MS-2/MS-3)
                        docker build -t "$VAL_IMAGE" -t "$VAL_IMAGE_NAME:latest" validation-service
                        docker push "$VAL_IMAGE"
                        docker push "$VAL_IMAGE_NAME:latest"
                        echo "$VAL_IMAGE" > pushed-validation-image.txt
                    '''
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when { expression { return isMasterCd() } }
            steps {
                echo "[stage=deploy run=${RUN_ID}] kubectl apply -> ${KUBE_NS}"
                sh '''
                    IMAGE="$(cat pushed-image.txt)"
                    VAL_IMAGE="$(cat pushed-validation-image.txt)"
                    kubectl apply -f k8s/00-namespaces.yaml

                    # Deploy the downstream validation-service (MS-2/MS-3) first so
                    # weather-api's fan-out calls have something to reach.
                    sed -e "s|VALIDATION_IMAGE_PLACEHOLDER|$VAL_IMAGE|g" \
                        -e "s|BUILD_PLACEHOLDER|${BUILD_NUMBER}|g" \
                        k8s/app/validation-deployment.yaml | kubectl apply -f -
                    kubectl apply -f k8s/app/validation-service.yaml
                    kubectl -n "$KUBE_NS" rollout restart deployment/validation-service
                    kubectl -n "$KUBE_NS" rollout status  deployment/validation-service --timeout=180s

                    # Deploy weather-api (MS-1)
                    sed -e "s|IMAGE_PLACEHOLDER|$IMAGE|g" \
                        -e "s|BUILD_PLACEHOLDER|${BUILD_NUMBER}|g" \
                        k8s/app/deployment.yaml | kubectl apply -f -
                    kubectl apply -f k8s/app/service.yaml
                    kubectl -n "$KUBE_NS" rollout restart deployment/weather-api
                    kubectl -n "$KUBE_NS" rollout status  deployment/weather-api --timeout=180s

                    # Ship pod logs (weather-api + validation-service) to Docker ES.
                    bash scripts/ensure-fluentbit-k8s.sh
                '''
            }
        }

        stage('Wait for endpoint') {
            when { expression { return isMasterCd() } }
            steps {
                sh 'bash scripts/k8s-port-forward.sh start'
            }
        }

        stage('MAT') {
            when { expression { return isMasterCd() } }
            steps {
                echo "[stage=mat run=${RUN_ID}] running Minimum Acceptance Tests"
                sh '''
                    mvn -B -ntp -Pmat test \
                        -Dapi.base.url="$APP_BASE_URL" \
                        -Dtest.stage=mat \
                        -Dtest.run.id="$RUN_ID" \
                        -DLOG_DIR="$LOG_DIR" \
                        -DBUILD_NUMBER="$BUILD_NUMBER" \
                        -DENV="$ENV" \
                        -Dkibana.url=http://localhost:5601
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                    sh 'bash scripts/ship-test-logs-to-es.sh "$LOG_DIR/tests-mat.json" mat || true'
                    archiveArtifacts artifacts: 'logs/flow-execution-report-mat.html, logs/flows/**', allowEmptyArchive: true, fingerprint: true
                }
            }
        }

        stage('Regression') {
            when { expression { return isMasterCd() } }
            steps {
                echo "[stage=regression run=${RUN_ID}] running regression suite"
                sh '''
                    mvn -B -ntp -Pregression test \
                        -Dapi.base.url="$APP_BASE_URL" \
                        -Dtest.stage=regression \
                        -Dtest.run.id="$RUN_ID" \
                        -DLOG_DIR="$LOG_DIR" \
                        -DBUILD_NUMBER="$BUILD_NUMBER" \
                        -DENV="$ENV" \
                        -Dkibana.url=http://localhost:5601
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                    sh 'bash scripts/ship-test-logs-to-es.sh "$LOG_DIR/tests-regression.json" regression || true'
                    archiveArtifacts artifacts: 'logs/flow-execution-report-regression.html, logs/flows/**', allowEmptyArchive: true, fingerprint: true
                }
            }
        }
    }

    post {
        always {
            echo "[stage=teardown run=${RUN_ID}] archiving logs and showing pod status"
            sh '''
                kubectl -n "$KUBE_NS" get pods -o wide || true
                kubectl -n "$KUBE_NS" logs -l app=weather-api --tail=50 || true
                kubectl -n "$KUBE_NS" logs -l app=validation-service --tail=50 || true
                bash scripts/k8s-port-forward.sh stop || true
                bash scripts/prepare-flow-report-for-jenkins.sh || true
            '''
            archiveArtifacts artifacts: 'logs/**/*.json, logs/**/*.log, logs/flow-execution-report*.html, logs/flows/**, logs/flow-report/**', allowEmptyArchive: true, fingerprint: true
            publishHTML(target: [
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'logs/flow-report',
                reportFiles: 'flow-execution-report.html',
                reportName: 'Flow Execution Report',
                reportTitles: 'Flow Execution Report'
            ])
        }
        failure {
            echo "[stage=analyze run=${RUN_ID}] build failed - running CD log analyzer"
            // Triage the failure, collect only the evidence that matters, and write
            // logs/analyzer/report.md + evidence.json. Never fail the post step.
            sh '''
                cd-analyze \
                    --workspace "$WORKSPACE" \
                    --job "$JOB_NAME" \
                    --build "$BUILD_NUMBER" \
                    --jenkins-url "$JENKINS_URL" \
                    --console-log "$JENKINS_HOME/jobs/$JOB_NAME/builds/$BUILD_NUMBER/log" \
                    --es-url "$ES_URL" \
                    --es-index "weather-logs-*" \
                    --kube-ns "$KUBE_NS" \
                    --out-dir "$WORKSPACE/logs/analyzer" || true
            '''
            archiveArtifacts artifacts: 'logs/analyzer/**', allowEmptyArchive: true, fingerprint: true
            // Surface the one-line root cause directly on the build page.
            script {
                def desc = sh(
                    script: '''jq -r '.analysis.suspected_cause // .analysis.root_cause // "see CD Failure Analysis report"' "$WORKSPACE/logs/analyzer/evidence.json" 2>/dev/null || echo "analyzer output unavailable"''',
                    returnStdout: true
                ).trim()
                if (desc) {
                    currentBuild.description = "CD failure: ${desc.take(180)}"
                }
            }
            // Render the analyzer's HTML report in the Jenkins UI.
            publishHTML(target: [
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'logs/analyzer',
                reportFiles: 'report.html',
                reportName: 'CD Failure Analysis',
                reportTitles: 'CD Failure Analysis'
            ])
        }
        success {
            echo "Build succeeded. Pod logs: Kibana Discover -> data view 'Weather pod logs' (weather-logs-*). Test logs: 'Weather test logs'."
            echo "Open http://localhost:5601/app/discover — time range Last 24 hours, filter e.g. build:${env.BUILD_NUMBER}"
        }
    }
}

// Master CD path = the MASTER_CD param is on AND, for multibranch jobs, the branch
// is a master/main CD branch. Single-pipeline jobs (no BRANCH_NAME) honour the param
// alone. Only this path deploys to the cluster, so only master pod logs reach ES.
boolean isMasterCd() {
    if (!params.MASTER_CD) {
        return false
    }
    def branch = env.BRANCH_NAME
    return branch == null || branch ==~ /(?i)(master|master-cd|main)/
}
