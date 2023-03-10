import com.att.nccicd.config.conf as config
import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput

conf = new config(env).CONF

json = new JsonSlurperClassic()

NET_RETRY_COUNT = env.NET_RETRY_COUNT.toInteger()
RETRY_COUNT = env.RETRY_COUNT.toInteger()
REQ_PROJECT_NAME = env.REQ_PROJECT_NAME
PROJECT_MAP = json.parseText(env.PROJECT_MAP)
DEPENDENCY_PROJECT_LIST = json.parseText(env.DEPENDENCY_PROJECT_LIST)

RELEASE = env.RELEASE

SHELL = "sudo"

MESSAGE = String.format(env.UPLIFT_COMMIT_MESSAGE_TEMPLATE, 'nightly', RELEASE)
TOPIC = String.format(env.UPLIFT_TOPIC_TEMPLATE, RELEASE)

ARTF_REPO = 'openstack'

RELEASE_BRANCH_MAP = json.parseText(RELEASE_BRANCH_MAP)
BRANCH = RELEASE_BRANCH_MAP[RELEASE]

PROJECT_PREFIX = "loci/mos"
IMAGE_BASE = String.format(conf.MOS_IMAGES_BASE_URL, "", RELEASE)
TAG = 'latest'

def compileSshData() {
    sshConfig = ""
    keys = []
    json.parseText(SSH_DATA).each { entry ->
        sshConfig += "Host ${entry.value.resource}\n" +
                     "User ${entry.value.user}\n"
        keys.add(entry.key)
    }
    return [keys, sshConfig]
}

// Compile ssh-agent key names and ssh config from SSH_DATA to be used
// for fetching projects to internal mirror
(KEY_NAMES, SSH_CONFIG) = compileSshData()

def getProjectRepoUrl(prj) {
    return prj.contains("ssh://") ? prj : "${INTERNAL_GERRIT_SSH}/${prj}"
}


def getImageVersions(url) {
    versions = [:]
    utils.retrier(NET_RETRY_COUNT) {
        sh "${SHELL} docker pull ${url}"
    }

    cmd = "${SHELL} docker inspect --format='{{index .RepoDigests 0}}' ${url}"
    versions['url'] = sh(returnStdout: true, script: cmd).trim()
    versions['sha256'] = versions['url'].split('@')[-1]

    cmd = (
        "${SHELL} docker inspect " +
        "--format='{{index .Config.Labels \"org.label-schema.vcs-ref\"}}' " +
        " ${url}"
    )
    versions['vcsRef'] = sh(returnStdout: true, script: cmd).trim()

    cmd = (
        "${SHELL} docker inspect " +
        "--format='{{index .Config.Labels \"org.label-schema.build-date\"}}' " +
        " ${url}"
    )
    versions['date'] = sh(returnStdout: true, script: cmd).trim()

    cmd = (
        "${SHELL} docker inspect " +
        "--format='{{index .Config.Labels \"org.label-schema.requirements-image\"}}' " +
        " ${url}"
    )
    versions['requirements_sha256'] = sh(returnStdout: true, script: cmd).trim().split('@')[-1]

    // Verify that predefined static tag points to the same image
    def res
    def tag = "${versions['vcsRef']}.${versions['date']}"
    utils.retrier(NET_RETRY_COUNT) {
        res = sh (script: "${SHELL} docker pull ${url.split(':')[0]}:${tag}",
                      returnStdout: true)
    }
    if (!res.contains(versions['sha256'])) {
        error("Digest sha256 of latest and static tag do not match!")
    } else {
        print("latest tag matches expected static tag ${tag}")
    }

    return versions
}


def getLatestSetVersions = {
    def imagesVersions = [:]
    PROJECT_MAP.each { projectName, buildTypes ->
        if (!buildTypes) {
            buildTypes = [projectName.split('-')[-1]]
        }
        buildTypes.each { buildType ->
            image_name = "mos-" + buildType
            url = "${IMAGE_BASE}/${image_name}:${TAG}"
            imagesVersions[buildType] = getImageVersions(url)
        }
    }
    return imagesVersions
}


def getProjectsVersions(projectList, branch = BRANCH) {
    projectsVersions = [:]
    projectList.each { projectName ->
        projectRepo = getProjectRepoUrl(projectName)
        utils.retrier (NET_RETRY_COUNT) {
            revision = gerrit.getVersion(projectRepo, branch,
                                         INTERNAL_GERRIT_KEY)
            if (!revision) {
                error("Failed to get project version")
            }
        }
        projectsVersions[projectName] = revision
    }
    return projectsVersions
}


def verifyImagesVersions(imagesVersions, reposVersions) {
    failure = false
    requirements_sha256 = imagesVersions['requirements']['sha256']
    PROJECT_MAP.each { projectName, buildTypes ->
        if (!buildTypes) {
            buildTypes = [projectName.split('-')[-1]]
        }
        buildTypes.each { buildType ->
            imageVersion = imagesVersions[buildType]['vcsRef']
            imageReqSha = imagesVersions[buildType]['requirements_sha256']
            projectVersion = reposVersions[projectName]
            if (imageVersion != projectVersion ||
                  (!buildType.contains('requirements') &&
                   imageReqSha != requirements_sha256)) {
                failure = true
                print ("--- ${buildType}: FAILURE ---")
                imagesVersions[buildType]['status'] = "FAILURE"
            } else {
                print ("--- ${buildType}: SUCCESS ---")
                imagesVersions[buildType]['status'] = "SUCCESS"
            }
            imagesVersions[buildType]['project_version'] = projectVersion
            print ("${buildType} image version: ${imageVersion}" +
                   "\n${projectName} ${BRANCH} version: ${projectVersion}" +
                   "\n${buildType} image requirements sha256: ${imageReqSha}" +
                   "\nlatest requirements sha256: ${requirements_sha256}")
        }
    }
    return failure
}


def verifyDepVersions(upperConstraintsVersions, reposVersions) {
    failure = false
    DEPENDENCY_PROJECT_LIST.each { projectName ->
        upperConstraintsVersion = upperConstraintsVersions[projectName]["uc_version"]
        projectVersion = reposVersions[projectName]
        if (upperConstraintsVersion != projectVersion) {
            failure = true
            print ("--- ${projectName}: FAILURE ---")
            upperConstraintsVersions[projectName]['status'] = "FAILURE"
        } else {
            print ("--- ${projectName}: SUCCESS ---")
            upperConstraintsVersions[projectName]['status'] = "SUCCESS"
        }
        upperConstraintsVersions[projectName]['project_version'] = projectVersion
        print ("${projectName} version in upper-constraints.txt:\n${upperConstraintsVersion}" +
               "\n${projectName} ${BRANCH} version:\n${projectVersion}")
    }
    return failure
}


vm (initScript: 'loci-bootstrap.sh',
        image: 'cicd-ubuntu-18.04-server-cloudimg-amd64',
        flavor: 'm1.medium',
        nodePostfix: 'validation',
        doNotDeleteNode: false) {
    // Create ssh config on slave to control what login is used for
    // what resource
    writeFile file: "${HOME}/.ssh/config", text: SSH_CONFIG
    sh "sudo bash -c 'echo \"nameserver ${DNS_SERVER_TWO}\" > /etc/resolv.conf'"

    sh 'sudo service docker stop ||:'
    sh 'sudo apt-get update && sudo apt-get install -y runc containerd docker.io'
    sh 'sudo service docker start ||:'

    stage('Docker Setup') {
        loci.initEnv(ARTF_SECURE_DOCKER_URL, "jenkins-artifactory",
                     "", NET_RETRY_COUNT)
    }
    stage('Gather projects versions') {
        reposVersions = getProjectsVersions(
            PROJECT_MAP.keySet() + DEPENDENCY_PROJECT_LIST as Set
        )
        print "Projects versions:\n${reposVersions}"
    }
    stage('Gather latest set versions') {
        IMAGES_VERSIONS = getLatestSetVersions()
        print "Images versions:\n${IMAGES_VERSIONS}"
    }
    stage('Verify images versions') {
        imagesHasFailures = verifyImagesVersions(IMAGES_VERSIONS, reposVersions)
    }
    stage('Verify dependency components versions') {
        utils.retrier (NET_RETRY_COUNT) {
            gerrit.cloneToBranch(
                getProjectRepoUrl(REQ_PROJECT_NAME),
                BRANCH,
                REQ_PROJECT_NAME,
                INTERNAL_GERRIT_KEY,
                null
            )
        }
        requirement_ref = IMAGES_VERSIONS['requirements']['vcsRef']
        dir(REQ_PROJECT_NAME) {
            sh "git checkout ${requirement_ref}"
            upperConstraints = readFile 'upper-constraints.txt'
            upperConstraintsVersions = [:]
            depStr = DEPENDENCY_PROJECT_LIST.join('|')
            (upperConstraints =~ /git\+.+?\/\/.*?\/(${depStr})?@(.*)?#.*/).each {
                upperConstraintsVersions[it[1]] = ["uc_version": it[2]]
            }
        }
        depsHasFailures = verifyDepVersions(upperConstraintsVersions,
                                            reposVersions)
    }
    print ("REPORT:\n${IMAGES_VERSIONS}\n${upperConstraintsVersions}")
    if(imagesHasFailures || depsHasFailures) {
        error("Failed to validate images integrity. " +
              "Please check logs for details.")
    } else {
        print("Successfully verified integrity of images")
    }
}

overrideImages = [:]
IMAGES_VERSIONS.each { buildType, data ->
    var_name = "${buildType.replace('-', '_').toUpperCase()}_LOCI"
    overrideImages[var_name] = data["url"]
}

overrideImagesJSON = JsonOutput.toJson(overrideImages)

images = ""
overrideImages.each { k, v -> images += "${k}=${v}\n"}

stage("Test Deployment") {
    if (RECREATE_SNAPSHOT.toBoolean()) {
        print ("Initiating snapshot creation..")
        parameters = [
            stringParam(name: "OVERRIDE_IMAGES", value: overrideImagesJSON),
            stringParam(name: "RELEASE", value: RELEASE),
            booleanParam(name: "INITIAL_DEPLOYMENT", value: true),
            booleanParam(name: "CREATE_SNAPSHOT", value: true),
        ]
        build(
            job: "${JOB_BASE}/TestDeploymentPipeline",
            parameters: parameters,
            wait: false
        )
    }
    if (RUN_DEPLOYMENT.toBoolean()) {
        print ("Starting test deployment with latest set:\n${images}")
        parameters = [
            stringParam(name: "OVERRIDE_IMAGES", value: overrideImagesJSON),
            stringParam(name: "RELEASE", value: RELEASE),
            booleanParam(name: "INITIAL_DEPLOYMENT", value: false),
            booleanParam(name: "CREATE_SNAPSHOT", value: false),
        ]
        utils.runBuild(
            "${JOB_BASE}/TestDeploymentPipeline",
            parameters,
            RETRY_COUNT
        )
    } else {
        print ("Skipping test deployment")
    }
}

currentBuild.description = images

IMAGES_VERSIONS_EXCLUDED = [:]
IMAGES_VERSIONS.each { key, value ->
    if (key =~ /(neutron|nova)/) { return }
    IMAGES_VERSIONS_EXCLUDED[key] = value
}

[
    [
        images: IMAGES_VERSIONS,
        topic: TOPIC + '-full',
        message: MESSAGE + '-full'
    ],
].each {
    parameters = [
        stringParam(name: "IMAGES", value: JsonOutput.toJson(it.images)),
        stringParam(name: "RELEASE", value: RELEASE),
        stringParam(name: "TOPIC", value: it.topic),
        stringParam(name: "COMMIT_MESSAGE", value: it.message),
    ]
    utils.runBuild(
        "${JOB_BASE}/UpliftPipeline",
        parameters,
        RETRY_COUNT
    )
}
