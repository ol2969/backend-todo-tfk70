import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2024.03"

project {

    vcsRoot(Tfk70backend)

    buildType(Build)
    buildType(Build1)
}

object Tfk70backend : GitVcsRoot({
    name = "TFK70 backend"
    url = "https://github.com/TFK70/backend-todo-list.git"
    branch = "refs/heads/master"
    branchSpec = "refs/heads/*"
})

object Build : BuildType({
    name = "Build"

    vcs {
        root(Tfk70backend)
    }

    steps {
        gradle {
            name = "Gradle build"
            id = "Gradle_build"
            tasks = "clean build"
            buildFile = "build.gradle"
        }
        dockerCommand {
            name = "Docker build"
            id = "Docker_build"
            commandType = build {
                source = file {
                    path = "Dockerfile"
                }
                namesAndTags = "ghcr.io/ol2969/test-backend:1.1"
            }
        }
        script {
            name = "Docker login"
            id = "Docker_login"
            scriptContent = "echo %env.GH_TOKEN% | docker login ghcr.io -u %env.GH_USER% --password-stdin"
        }
        dockerCommand {
            name = "Docker push"
            id = "Docker_push"
            commandType = push {
                namesAndTags = "ghcr.io/ol2969/test-backend:1.1"
            }
        }
        script {
            name = "Run Postgres"
            id = "simpleRunner"
            enabled = false
            scriptContent = "docker compose -f docker-compose.yml up -d"
        }
        script {
            name = "Run app"
            id = "Apply_specs"
            scriptContent = "docker run -d --name backend -p 8080:8080 -e SPRING_PROFILES_ACTIVE=docker -e DATABASE_USER=program -e DATABASE_PASSWORD=test -e DATABASE_PORT=5432 -e DATABASE_NAME=todo_list -e DATABASE_URL=postgres --network backend-todo-tfk70_default ghcr.io/ol2969/test-backend:1.1"
        }
    }

    triggers {
        vcs {
        }
    }

    features {
        perfmon {
        }
    }
})

object Build1 : BuildType({
    name = "Deploy into minikube"

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            name = "Apply specs"
            id = "Apply_specs"
            scriptContent = """
                kubectl apply -f deployment/common/namespace.yml
                kubectl apply -f deployment/postgres/postgres.yml
                kubectl apply -f deployment/postgres/postgres-service.yml
                kubectl apply -f deployment/backend/deployment.yml
                kubectl apply -f deployment/backend/service.yml
                kubectl apply -f deployment/backend/gateway.yml
            """.trimIndent()
        }
    }

    triggers {
        vcs {
        }
    }

    features {
        perfmon {
        }
    }
})
