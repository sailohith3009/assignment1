import com.att.nccicd.config.conf as config
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

conf = new config(env).CONF
json = new JsonSlurperClassic()

EVENT_TYPE = env.EVENT_TYPE ? env.EVENT_TYPE : 'manual'
RETRY_COUNT = env.RETRY_COUNT.toInteger()
SUPPORTED_RELEASES = json.parseText(env.SUPPORTED_RELEASES)

def getProjName(String project) {
    project.split('/')[-1]
}

def getRefParamName(String project) {
    getProjName(project).toUpperCase().replace('-', '_') + "_REF"
}

reqRefParam = getRefParamName(env.REQ_PROJECT_NAME)
reqRef = env."${reqRefParam}"

if (REQUIREMENTS_LOCI_IMAGE && (DEPENDENCY_LIST || reqRef)) {
    error('Can not specify REQUIREMENTS_LOCI_IMAGE with DEPENDENCY_LIST ' +
          'or ${reqRefParam}')
}

stage("${REQ_PROJECT_NAME} image build") {
    if (DEPENDENCY_LIST || reqRef) {
        parameters = [
            stringParam(name: 'DEPENDENCY_LIST', value: DEPENDENCY_LIST),
            stringParam(name: 'EVENT_TYPE', value: EVENT_TYPE),
            stringParam(name: 'PROJECT_REF', value: reqRef),
            stringParam(name: 'RELEASE', value: RELEASE),
        ]
        print "Building image for ${REQ_PROJECT_NAME} with ${parameters}"
        job = utils.runBuild(
            "${JOB_BASE}/${REQ_PROJECT_NAME}",
            parameters,
            RETRY_COUNT
        )
        REQUIREMENTS_LOCI_IMAGE = job.getBuildVariables()["IMAGE_SHA"]
    } else {
        print ("Skipping build for ${REQ_PROJECT_NAME} as there " +
               'are no related changes.')
    }
}

PROJECT_MAP = json.parseText(env.PROJECT_MAP)[RELEASE]
results = [:]
runningSet = [:]
def i = 0
PROJECT_MAP.each { projectName, buildTypes ->
    if (projectName == REQ_PROJECT_NAME) { return }
    // Don't build component image with both default refspec and
    // default requirement image
    if (!(REQUIREMENTS_LOCI_IMAGE || env."${getRefParamName(projectName)}")) {
        print ("Skipping build for ${projectName} as there are no " +
               "related changes.")
        return
    }
    if (!buildTypes) {
        buildTypes = [projectName.split('-')[-1]]
    }
    buildTypes.each { buildType ->
        def y = i
        runningSet[buildType] = {
            sleep 90*y
            stage("mos-${buildType} image build") {
                projectRef = env."${getRefParamName(projectName)}"
                parameters = [
                    stringParam(name: 'PROJECT_REF', value: projectRef),
                    stringParam(name: 'RELEASE', value: RELEASE),
                    stringParam(name: 'REQUIREMENTS_LOCI_IMAGE',
                                value: REQUIREMENTS_LOCI_IMAGE),
                    stringParam(name: 'EVENT_TYPE', value: EVENT_TYPE),
                ]
                print "Building image for ${buildType} with ${parameters}"
                results[buildType] = utils.runBuild(
                    "${JOB_BASE}/mos-${buildType}",
                    parameters,
                    RETRY_COUNT
                ).getBuildVariables()
                if (EVENT_TYPE == 'patchset-created' &&
                        SUPPORTED_RELEASES.contains(RELEASE)) {
                    IMAGE_NAME = results[buildType]["IMAGE_SHA"]
                    print "invoking Clair scan on IMAGE: ${IMAGE_NAME}"
                    try {
                        utils.runBuild(
                            env.JOB_CLAIR,
                            [stringParam(name: 'docker_image', value: IMAGE_NAME),
                             booleanParam(name: 'open_img', value: true)],
                            RETRY_COUNT
                        )
                    } catch (e) {
                          print "==== Security vulnerability found. Please check the " +
                                "console log for detailed information.==="
                          throw e
                    }
                }
            }
        }
        i++
    }
}

parallel runningSet

overrideImages = [:]
imagesVersions = [:]
results.each { buildType, buildVars ->
    overrideImages[buildVars["LOCI_IMAGE_VAR"]] = buildVars["IMAGE_SHA"]
    imagesVersions[buildType] = [
        'vcsRef': buildVars["PROJECT_VERSION"],
        'date': buildVars["BUILD_TIMESTAMP"],
        'url': buildVars["IMAGE_SHA"],
    ]
}
overrideImagesJSON = JsonOutput.toJson(overrideImages)
parameters = [
    stringParam(name: "OVERRIDE_IMAGES", value: overrideImagesJSON),
    stringParam(name: "RELEASE", value: RELEASE),
]

images = ""
overrideImages.each { k, v -> images += "${k}=${v}\n"}
if (REQUIREMENTS_LOCI_IMAGE) {
    images += "REQUIREMENTS_LOCI=${REQUIREMENTS_LOCI_IMAGE}"
}
currentBuild.description = images

if (EVENT_TYPE != 'change-merged' && params.RUN_DEPLOYMENT) {
    stage("Test Deployment") {
        utils.runBuild(
            "${JOB_BASE}/TestDeploymentPipeline",
            parameters,
            RETRY_COUNT,
        )
    }
}

env.IMAGES = images

print JsonOutput.toJson(imagesVersions)

if (UPLIFT_IMAGES.toBoolean()) {
    def upliftCommitMessage, upliftTopic
    if (EVENT_TYPE == 'change-merged') {
        upliftCommitMessage = String.format(env.UPLIFT_COMMIT_MESSAGE_TEMPLATE,
                                            'merge event', RELEASE)
        upliftTopic = String.format(env.UPLIFT_TOPIC_TEMPLATE, RELEASE)
    } else {
        upliftCommitMessage = '[DO NOT MERGE] FOR TEST ONLY'
        upliftTopic = "test-${RELEASE}-uplift"
    }
    parameters = [
        stringParam(name: "IMAGES",
                    value: JsonOutput.toJson(imagesVersions)),
        stringParam(name: "RELEASE", value: RELEASE),
        stringParam(name: "TOPIC", value: upliftTopic),
        stringParam(name: "COMMIT_MESSAGE", value: upliftCommitMessage),
    ]
    utils.runBuild(
        "${JOB_BASE}/UpliftPipeline",
        parameters,
        RETRY_COUNT
    )
}
