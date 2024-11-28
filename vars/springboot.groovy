def call(String servicename, String githubrepo, String healthcheck, String stack,) {

    pipeline {
        environment {
            ecr_base_url = "numeral.dkr.ecr.ap-south-1.amazonaws.com"    // Your ECR URL
            region = "ap-south-1"               // Region of the ECR
            cluster_name = "CLUSTER-NAME"       // Your cluster name
        }

        parameters {
            string(name: 'branch', defaultValue: 'staging', description: '')     // Branch to use
            choice(choices: ['stage', 'dev', 'beta'], description: 'Environment', name: 'SPRING_ENV')    // Environment to use
        }

        agent any

        stages {
            stage('Git Pull') {
                steps {
                    script {
                        env.ecr_githubrepo = params.SPRING_ENV + "-$servicename"    
                        env.ecr_url = env.ecr_base_url + "/" + params.SPRING_ENV + "-$servicename" 
                        git_vars = git branch: "${params.branch}", poll: false, credentialsId: 'git', url: "https://github.com/${githubrepo}.git"
                    }
                }
            }

            stage('Unit Test & Build') {
                steps {
                    script {
                        echo "Starting unit test and build on ${stack} stack"
                        if (${stack} == 'java') {
                            sh """
                                mvn clean test -Dspring.profiles.active=${SPRING_ENV} -U
                                mvn install -Dmaven.test.skip=true -U
                            """
                        } 
                        else if (${stack} == 'python') {
                            script {
                                    sh "python3.9 -m pytest || true"                                    
                            }
                        }
                        else if (${stack} == 'node') {
                            script {
                                    sh """
                                        npm i
                                        npm run test
                                        npm build
                                    """
                                }
                            }
                        else {
                            echo "Unknown stack, do nothing (You can add more according to your project requirements)"
                        }
                    }
                }
            }

            stage('Docker Build') {
                steps {
                    sh label: 'Build',
                    script: """
                        docker build -t ${ecr_githubrepo} . ;
                    """
                }
            }

            stage("Integration Test") {
                steps {
                    script {
                       echo "Starting integration test on ${stack} stack" // This is a placeholder for integration test
                    }
                }
            }

            stage('Sonar Analysis') {
                steps {
                    script{
                        echo "Starting Sonar Analysis on ${stack} stack" // This is a placeholder for Sonar Analysis
                    }
                }
            }

            stage('Docker Publish') {
                steps {
                    sh label: 'Publish',
                    script: """
                        aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${ecr_base_url}
                        docker tag ${ecr_githubrepo}:$latest ${ecr_url}:$latest;
                        docker push ${ecr_url}:$latest
                    """
                }
            }

            stage('ECS Deploy') {
                steps {
                    sh label: 'Deploy',
                    script: """
                        aws ecs update-service --cluster ${cluster_name} --service ${params.SPRING_ENV}-${servicename} --force-new-deployment
                    """
                }
            }
        }

        post {
            always {
             script{
                echo "Post Actions can be comprises of Cleaning task"
             }
            }
        }
    }
}
