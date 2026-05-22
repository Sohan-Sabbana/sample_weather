pipeline {
    agent any

    options {
        timestamps()
        ansiColor('xterm')
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        // A single id that identifies this entire pipeline run. Every JSON log
        // line (app + tests) emitted during this build will carry it, so the
        // CD log analyzer can pull the full picture from Elasticsearch with:
        //     service:weather-api* AND build:"${BUILD_NUMBER}"
        RUN_ID       = "${env.JOB_NAME}-${env.BUILD_NUMBER}"
        APP_PORT     = "8080"
        APP_BASE_URL = "http://localhost:8080"
        LOG_DIR      = "${env.WORKSPACE}/logs"
        ENV          = "ci"
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
                echo "[stage=build run=${RUN_ID}] building artifact"
                sh 'mvn -B -ntp clean package -DskipTests'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/weather-api.jar', fingerprint: true
                }
            }
        }

        stage('Deploy') {
            steps {
                echo "[stage=deploy run=${RUN_ID}] starting app on port ${APP_PORT}"
                sh '''
                    set -e
                    pkill -f weather-api.jar || true
                    nohup java \
                        -DLOG_DIR="$LOG_DIR" \
                        -DENV="$ENV" \
                        -DBUILD_NUMBER="$BUILD_NUMBER" \
                        -Dserver.port="$APP_PORT" \
                        -jar target/weather-api.jar > "$LOG_DIR/app-stdout.log" 2>&1 &
                    echo $! > .app.pid

                    echo "Waiting for /api/health ..."
                    for i in $(seq 1 60); do
                        if curl -fsS "$APP_BASE_URL/api/health" >/dev/null; then
                            echo "App is up after ${i}s"; exit 0
                        fi
                        sleep 1
                    done
                    echo "App failed to start within 60s"; tail -n 200 "$LOG_DIR/app-stdout.log"; exit 1
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
                }
            }
        }
    }

    post {
        always {
            echo "[stage=teardown run=${RUN_ID}] stopping app and archiving logs"
            sh '''
                if [ -f .app.pid ]; then
                    kill $(cat .app.pid) || true
                    rm -f .app.pid
                fi
            '''
            // Archive raw JSON logs so the CD log analyzer can pick them up
            // (or your Filebeat sidecar can ship them straight to Elasticsearch).
            archiveArtifacts artifacts: 'logs/**/*.json, logs/**/*.log', allowEmptyArchive: true, fingerprint: true
        }
    }
}
