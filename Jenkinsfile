pipeline {
    options {
        skipDefaultCheckout()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }
    agent { label 'd3-build-agent' }
    stages {
        stage('Tests') {
            steps {
                script {
                    checkout scm
                    env.WORKSPACE = pwd()
                    docker.image("gradle:4.10.2-jdk8-slim")
                            .inside("-v /var/run/docker.sock:/var/run/docker.sock -v /tmp:/tmp") {
                        sh "gradle test --info"
                        sh "gradle shadowJar"
                        sh "gradle compileIntegrationTestKotlin --info"
                        sh "gradle integrationTest --info"
                    }
                }
            }
            post {
                cleanup {
                    cleanWs()
                }
            }
        }

        stage('Build and push docker images') {
          agent { label 'd3-build-agent' }
          steps {
            script {
              def scmVars = checkout scm
              if (env.BRANCH_NAME ==~ /(master|develop|reserved)/) {
                withCredentials([usernamePassword(credentialsId: 'nexus-d3-docker', usernameVariable: 'login', passwordVariable: 'password')]) {
                  sh "docker login nexus.iroha.tech:19002 -u ${login} -p '${password}'"

                  TAG = env.BRANCH_NAME
                  iC = docker.image("gradle:4.10.2-jdk8-slim")
                  iC.inside("-e JVM_OPTS='-Xmx3200m' -e TERM='dumb'") {
                    sh "gradle shadowJar"
                  }

                  def nexusRepository="nexus.iroha.tech:19002/${login}"

                  def chainAdapterJarFile="/build/libs/chain-adapter-all.jar"

                  chainAdapter = docker.build("${nexusRepository}/chain-adapter:${TAG}", "-f docker/dockerfile --build-arg JAR_FILE=${chainAdapterJarFile} .")

                  chainAdapter.push("${TAG}")
                }
              }
            }
          }
        }
    }
}
