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
                echo "[stage=docker run=${RUN_ID}] building ${IMAGE}"
                withCredentials([usernamePassword(credentialsId: 'dockerhub',
                                                  usernameVariable: 'sparebuddy',
                                                  passwordVariable: '[Credentials]')]) {
                    sh '''
                        echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
                        docker build -t "$IMAGE" -t "$IMAGE_NAME:latest" .
                        docker push "$IMAGE"
                        docker push "$IMAGE_NAME:latest"
                    '''
                }
            }
        }


        stage('Deploy to Kubernetes') {
            steps {
                echo "[stage=deploy run=${RUN_ID}] kubectl apply -> ${KUBE_NS}"
                sh '''
                    kubectl apply -f k8s/00-namespaces.yaml
                    # Substitute image into the Deployment manifest
                    sed "s|IMAGE_PLACEHOLDER|$IMAGE|g" k8s/app/deployment.yaml | kubectl apply -f -
                    kubectl apply -f k8s/app/service.yaml


                    # Force a rollout if image tag is the same (e.g. re-build of latest)
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
