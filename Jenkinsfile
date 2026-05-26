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
    }

    tools {
        maven 'Maven-3.9'
    }

    environment {
        IMAGE_NAME   = "${params.DOCKERHUB_USER}/weather-api"
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
        IMAGE        = "${IMAGE_NAME}:${IMAGE_TAG}"
        RUN_ID       = "${env.JOB_NAME}-${env.BUILD_NUMBER}"
        APP_BASE_URL = "http://host.docker.internal:30080"
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
                        echo "[stage=docker run=${RUN_ID}] building ${IMAGE}"
                        echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
                        docker build -t "$IMAGE" -t "$IMAGE_NAME:latest" .
                        docker push "$IMAGE"
                        docker push "$IMAGE_NAME:latest"
                        echo "$IMAGE" > pushed-image.txt
                    '''
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                echo "[stage=deploy run=${RUN_ID}] kubectl apply -> ${KUBE_NS}"
                sh '''
                    IMAGE="$(cat pushed-image.txt)"
                    kubectl apply -f k8s/00-namespaces.yaml
                    sed "s|IMAGE_PLACEHOLDER|$IMAGE|g" k8s/app/deployment.yaml | kubectl apply -f -
                    kubectl apply -f k8s/app/service.yaml
                    kubectl -n "$KUBE_NS" rollout restart deployment/weather-api
                    kubectl -n "$KUBE_NS" rollout status  deployment/weather-api --timeout=180s
                '''
            }
        }

        stage('Wait for endpoint') {
            steps {
                sh '''
                    echo "Waiting for $APP_BASE_URL/api/health ..."
                    for i in $(seq 1 60); do
                        if curl -fsS "$APP_BASE_URL/api/health" >/dev/null; then
                            echo "App is reachable after ${i}s"; exit 0
                        fi
                        sleep 2
                    done
                    echo "App not reachable"; kubectl -n "$KUBE_NS" get pods; exit 1
                '''
            }
        }

        stage('MAT') {
            steps {
                echo "[stage=mat run=${RUN_ID}] running Minimum Acceptance Tests"
                sh '''
                    mvn -B -ntp -Pmat test \
                        -Dapi.base.url="$APP_BASE_URL" \
                        -Dtest.stage=mat \
                        -Dtest.run.id="$RUN_ID" \
                        -DLOG_DIR="$LOG_DIR" \
                        -DBUILD_NUMBER="$BUILD_NUMBER" \
                        -DENV="$ENV"
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
            steps {
                echo "[stage=regression run=${RUN_ID}] running regression suite"
                sh '''
                    mvn -B -ntp -Pregression test \
                        -Dapi.base.url="$APP_BASE_URL" \
                        -Dtest.stage=regression \
                        -Dtest.run.id="$RUN_ID" \
                        -DLOG_DIR="$LOG_DIR" \
                        -DBUILD_NUMBER="$BUILD_NUMBER" \
                        -DENV="$ENV"
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
                bash scripts/merge-flow-reports.sh || true
            '''
            archiveArtifacts artifacts: 'logs/**/*.json, logs/**/*.log, logs/flow-execution-report*.html, logs/flows/**', allowEmptyArchive: true, fingerprint: true
            publishHTML(target: [
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'logs',
                reportFiles: 'flow-execution-report.html',
                reportName: 'Flow Execution Report',
                reportTitles: 'Flow Execution Report'
            ])
        }
        success {
            echo "Build succeeded. View logs in Kibana: http://localhost:5601"
        }
    }
}
