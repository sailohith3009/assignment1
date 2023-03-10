import jenkins.model.Jenkins
import com.att.nccicd.config.conf as config
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions

DumperOptions options = new DumperOptions()
options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
options.setPrettyFlow(true)

yaml = new Yaml(options)

conf = new config(env).CONF
RETRY_COUNT = RETRY_COUNT.toInteger()

LABEL = "DebugDeployment-${GERRIT_CHANGE_OWNER_EMAIL.split('@')[0]}"
LAUNCH_NODE = 'jenkins-node-launch'

json = new JsonSlurperClassic()

projectList = json.parseText(PROJECT_LIST)
validCharts = []
projectList.each { validCharts.add(it.split("-")[-1]) }

if (GERRIT_CHANGE_OWNER_EMAIL != GERRIT_EVENT_ACCOUNT_EMAIL) {
    error("Wrong user")
}

def keyForValue(map, value) {
    map.find { it.value == value }?.key
}
RELEASE_BRANCH_MAP = json.parseText(RELEASE_BRANCH_MAP)
RELEASE = keyForValue(RELEASE_BRANCH_MAP, GERRIT_BRANCH)

def setPubKey() {
    def keys
    node (LAUNCH_NODE) {
        withCredentials([usernamePassword(credentialsId: 'gerrit_http_creds',
                                          usernameVariable: 'USERNAME',
                                          passwordVariable: 'PASSWORD')]) {
            cmd = ("wget --no-proxy --http-user ${USERNAME} --http-password ${PASSWORD} " +
                   "https://${INTERNAL_GERRIT_URL}/a/accounts/" +
                   "${GERRIT_CHANGE_OWNER_EMAIL}/sshkeys")
            sh cmd
            keys = json.parseText(sh(script: 'tail -n +2 sshkeys',
                                     returnStdout: true))
            sh 'rm sshkeys'
        }
    }
    node (LABEL) {
        keys.each {
            sh "echo \"${it.ssh_public_key}\" >> ${HOME}/.ssh/authorized_keys"
        }
    }
}

def getHost(name) {
  def launcher = Jenkins.getInstance().getComputer(name).getNode().getLauncher()
  return [launcher.getHost(), launcher.getPort()]
}


def waitForNode(limit) {
    def i = 0
    while (!nodesByLabel(label: LABEL)) {
        if (i >= limit) {
            return false
        }
        sleep 60
        i ++
    }
    return true
}


def waitForDeployment(limit) {
    def i = 0
    try {
        node (LABEL) {
            dir("${WORKSPACE}/../DebugDeploymentPipeline") {
                while (!fileExists("config/config")) {
                    if (i >= limit) {
                        return false
                    }
                    sleep 60
                    i ++
                }
            }
        }
    } catch (Exception e) {
        return false
    }
    return true
}


def deploy = {
    build(
        job: "${JOB_BASE}/DebugDeploymentPipeline",
        parameters: [
            stringParam(name: 'RELEASE', value: RELEASE),
            stringParam(name: 'LABEL', value: LABEL)
        ],
        wait: false
    )
}


def lock () {
    dir ("${WORKSPACE}/../DebugDeploymentPipeline/config") {
        while (fileExists("lock")) { sleep 1 }
        sh "touch lock"
    }
}


def unlock () {
    dir ("${WORKSPACE}/../DebugDeploymentPipeline/config") {
        sh "rm lock"
    }
}


def reply (message) {
    node (LAUNCH_NODE){
        sshagent([INTERNAL_GERRIT_KEY]) {
            sh ("ssh -p ${GERRIT_PORT} ${INTERNAL_GERRIT_USER}@${GERRIT_HOST} " +
                "gerrit review --project \"${GERRIT_PROJECT}\" " +
                "--message '\"${message}\"' --verified -1 '${GERRIT_PATCHSET_REVISION}'")
        }
    }
}

comment = new String(GERRIT_EVENT_COMMENT_TEXT.decodeBase64())
commands = []
(comment =~ /debug(.*)/).each {
    commands.add(it[1].trim())
}
if (commands.size() > 1) {
    message = "You may not specify more than 1 command at a time"
    reply(message)
    error(message)
}
args = commands[0].split()
command = args[0]
params = []
if (args.size() > 1) {
    params = args[1..-1]
}


def help = { cmd ->
    def message = ""
    switch (cmd) {
        case "init":
            message = "Usage: debug init\n\nCreate environment for troubleshooting"
            break
        case "stop":
            message = "Usage: debug stop\n\nDestroy environment"
            break
        case "add":
            message = (
                "Usage: debug add [<chart>] [<repo>] [<revision>] " +
                "[--tag <tag>] [--exclude-tag <tag>]\n\n" +
                "Add repo to images configuration"
            )
            break
        case "remove":
            message = ("Usage: debug remove [<chart>] [<repo>] " +
                       "[--tag <tag>] [--exclude-tag <tag>]\n\n" +
                       "Remove repo from images configuration")
            break
        case "apply":
            message = "Usage: debug apply\n\nApply current images configuration to environment"
            break
        case "switch":
            message = ("Usage: debug switch <revision>\n\n" +
                       "Switch current configuration to previously created one")
            break
        case "status":
            message = "Usage: debug status\n\nShow currently deployed configuration"
            break
        default:
            message = """debug init  Create environment for troubleshooting
debug stop  Destroy environment
debug add  Add project to configuration
debug remove  Remove project from configuration
debug apply  Apply configuration to the environment
debug switch  Switch configuration to previously created version
debug status  Show currently deployed configuration
"""
    }
    reply(message)
}


def assertEnvExists(cmd) {
    if (!nodesByLabel(label: LABEL)) {
        msg = "${cmd}: environment is not ready or does not exist"
        reply(msg)
        error(msg)
    }
}


def assertEnvReady(cmd) {
    assertEnvExists(cmd)
    def ready = true
    node(LABEL) {
        dir("${WORKSPACE}/../DebugDeploymentPipeline") {
            if (!fileExists("config/config")) {
                ready = false
            }
        }
    }
    if (ready) { return }
    msg = "${cmd}: environment deployment is not finished"
    reply(msg)
    error(msg)
}


def stop = { params ->
    if (params) {
        help("stop")
        error("Wrong parameters")
    }
    assertEnvExists("stop")
    node(LABEL) {
        sh "sudo rm -rf ../DebugDeploymentPipeline"
    }
    sleep 60
    reply("stop: environment is terminated")
}


def init = { params ->
    if (params) {
        help("init")
        error("Wrong parameters")
    }
    if (waitForNode(4)) {
        ip = getHost(LABEL)
        reply("init: Environment already exists or being deployed.")
        error("Environment already exists")
    }
    reply("init: Initiating a deployment of environment. " +
          "Approximate waiting time: 30 minutes")
    deploy()
    if (!waitForNode(15)) {
        reply("init: Failed to create environment. Please try again or contact " +
              "CI/CD team")
        error("Failed to create environment.")
    }
    if (waitForDeployment(30)) {
        setPubKey()
        (ip, port) = getHost(LABEL)
        reply("init: Environment deployment is successfully finished. " +
              "You may access it with:\n\nssh -p ${port} ubuntu@${ip}\n\nusing your Gerrit key")
    } else {
        reply("init: Failed to create environment. Please try again or contact " +
              "CI/CD team")
        error("Deployment failed")
    }
}


def populateNamedParams(paramName, args, storage) {
    while (true) {
        index = args.findIndexOf { key -> key == paramName }
        if (index < 0) {
            break
        }
        args.remove(index)
        if (args.size() < index + 1) {
            error("Unable to parse parameters: ${paramName}")
        }
        storage.add(args[index])
        args.remove(index)
    }
}

def parseArgs(args, positional, named) {
    try {
        def params = [:]
        named.each {
            params[it] = []
            populateNamedParams("--${it}", args, params[it])
        }
        arg = args.find { key -> key =~ /^--.*/ }
        if (arg) {
            error("Not recoginzed parameter: ${arg}")
        }
        positional.each {
            def arg = null
            if (args) {
                arg = args[0]
                args.remove(0)
            }
            params[it] = arg
        }
        if (args) {
            error("Not recognized arguments: ${args}")
        }
        return params
    } catch (Exception e) {
        reply("Error occured while parsing arguments:\n${e}")
        throw(e)
    }
}


def deepCopy(map) {
    json.parseText(JsonOutput.toJson(map))
}

def updateDeathTime() {
    deathTime = System.currentTimeMillis() + 14400000
    deathTimeReadable = new Date(deathTime).format(
        "yyyy-MM-dd HH:mm:ss 'UTC'")
    writeFile(
        file: "deathTime",
        text: deathTime.toString().bytes.encodeBase64().toString()
    )
    return deathTimeReadable
}


def modify = { method, params ->
    if (!validCharts.contains(params.chart)) {
        msg = "Invalid chart. Valid charts: ${validCharts}"
        reply(msg)
        error(msg)
    }
    if (params['tag'] && params['exclude-tag']) {
        msg = "You may not specify both --tag and --exclude-tag"
        reply(msg)
        error(msg)
    }
    node(LABEL) {
        dir("${WORKSPACE}/../DebugDeploymentPipeline/config") {
            lock()
            def data
            data = json.parseText(
                new String((readFile("config")).trim().decodeBase64())
            )
            def oldConf = data.config ?: [:]
            def newConf = deepCopy(oldConf)
            def chartConfig = newConf[params.chart] ?: [:]
            def defaultTagConfig = chartConfig.default ?: [:]
            def chartOverridesConfig = chartConfig.overrides ?: [:]
            if (params['tag']) {
                params['tag'].each {
                    def tagConfig = (
                        chartOverridesConfig[it] != null ?
                        chartOverridesConfig[it] : defaultTagConfig.clone()
                    )
                    method(tagConfig)
                    chartOverridesConfig[it] = tagConfig
                }
            } else {
                params['exclude-tag'].each {
                    chartOverridesConfig[it] = (
                        chartOverridesConfig[it] != null ?
                        chartOverridesConfig[it] : defaultTagConfig.clone()
                    )
                }
                method(defaultTagConfig)
                chartOverridesConfig.each { tag, tagConfig ->
                    if (params['exclude-tag'].contains(tag)) { return }
                    method(tagConfig)
                    chartOverridesConfig[tag] = tagConfig
                }
            }
            chartOverridesConfig.each { tag, tagConfig ->
                if (tagConfig == defaultTagConfig) {
                    chartOverridesConfig.remove(tag)
                }
            }
            if (defaultTagConfig) {
                chartConfig.default = defaultTagConfig
            } else {
                chartConfig.remove("default")
            }
            if (chartOverridesConfig) {
                chartConfig.overrides = chartOverridesConfig
            } else {
                chartConfig.remove("overrides")
            }

            if (chartConfig) {
                newConf[params.chart] = chartConfig
            } else {
                newConf.remove(params.chart)
            }

            def deathTimeReadable = updateDeathTime()
            if (oldConf != newConf) {
                data.config = newConf
                data = JsonOutput.toJson(data)
                writeFile(file: "config",
                          text: data.bytes.encodeBase64().toString())
                sh "git add config; git commit -m 'Update from ${GERRIT_REFSPEC}'"
                def revision = sh (returnStdout: true,
                                   script: "git rev-parse HEAD").trim()
                reply("New configuration version ${revision} is created:\n" +
                      "${yaml.dump(newConf)}" +
                      "\n\nType <debug apply> to apply it.\n" +
                      "New death time: ${deathTimeReadable}")
            } else {
                reply("No changes in configuration. New death time: ${deathTimeReadable}")
            }
            unlock()
        }
    }
}


def processChart(params) {
    component = params.component
    if (component && component.contains("://") && !(component =~ /^http[s]{0,1}:\/\//)) {
        reply ("Only anonymous http/http(s) is allowed for external projects. " +
               "For internal projects please use project name only.")
        error ("Invalid configuration")
    }
    if (!params.chart) {
        if (projectList.contains(GERRIT_PROJECT)) {
            params.chart = GERRIT_PROJECT.split('-')[-1]
            params.component = GERRIT_PROJECT
        } else {
            msg = "Invalid arguments. No valid chart found"
            reply(msg)
            error(msg)
        }
    }
}


def add = { args ->
    if (args && ["help", "--help", "-h"].contains(args[0])) {
        help("add")
        return
    }
    assertEnvReady("add")
    def positional = ['chart', 'component', 'ref']
    def named = ['tag', 'exclude-tag']
    def params = parseArgs(args, positional, named)
    processChart(params)
    params.ref = params.ref ?: (params.component == GERRIT_PROJECT ?
                                GERRIT_REFSPEC : 'master')
    modify({ map -> map[params.component] = params.ref }, params)
}


def remove = { args ->
    if (args && ["help", "--help", "-h"].contains(args[0])) {
        help("remove")
        return
    }
    assertEnvReady("remove")
    def positional = ['chart', 'component']
    def named = ['tag', 'exclude-tag']
    def params = parseArgs(args, positional, named)
    processChart(params)
    modify({ map -> map.remove(params.component) }, params)
}


def apply = { params ->
    if (params) {
        help ("apply")
        error("Wrong parameters")
    }
    assertEnvReady()
    node(LABEL) {
        dir("${WORKSPACE}/../DebugDeploymentPipeline/config") {
            if (fileExists("apply")) {
                reply("Previous apply command is not yet processed.")
                error("apply already exist")
            }
            lock()
            def headRevision = sh (returnStdout: true,
                                   script: "git rev-parse HEAD").trim()

            def deployedRevision = ""
            if (fileExists("deployedRevision")) {
                deployedRevision = (readFile("deployedRevision")).trim()
            }
            def deathTimeReadable = updateDeathTime()
            if (headRevision == deployedRevision) {
                reply ("Nothing to apply.\nConfiguration version " +
                       "${headRevision} is already deployed." +
                       "\n\nNew death time: ${deathTimeReadable}")
                unlock()
                return
            }
            reply("Applying ${headRevision} to the environment" +
                  "\n\nNew death time: ${deathTimeReadable}")
            writeFile file: "apply", text: headRevision
            unlock()
            while (true) {
                sleep 30
                if (!fileExists("deployedRevision") && !fileExists("failedRevision")) {
                    continue
                }
                if (fileExists("failedRevision")) {
                    sleep 1
                    def failedRevision = readFile("failedRevision").trim()
                    sh "rm failedRevision"
                    reply("Failed to apply ${failedRevision}")
                    return
                }
                deployedRevision = (readFile("deployedRevision")).trim()
                if (deployedRevision == headRevision) {
                    reply("Configuration version ${headRevision} is applied.")
                    return
                }
            }
        }
    }
}


def checkout = { params ->
    if (params.size() != 1) {
        help ("switch")
        error("Wrong parameters")
    }
    def revision = params[0]
    assertEnvReady()
    node(LABEL) {
        dir("${WORKSPACE}/../DebugDeploymentPipeline/config") {
            lock()
            def headRevision = sh (returnStdout: true,
                                   script: "git rev-parse HEAD").trim()
            if (revision == headRevision) {
                reply ("Coniguration version is already ${revision}")
                unlock()
                return
            }
            def status = sh (script: "git rev-parse ${revision}",
                             returnStatus: true)
            if (status) {
                reply ("${revision} not found in config repository")
                unlock()
                return
            }
            def branch = "master-${headRevision}"
            status = sh (script: "git rev-parse ${branch}",
                         returnStatus: true)
            if (status) {
                sh "git branch master-${headRevision}"
            }
            sh ("git reset --hard ${revision}")
            def data = json.parseText(
                new String((readFile("config")).trim().decodeBase64())
            )
            def dataConfig = data.config ?: [:]
            reply ("Switched configuration version to ${revision}\n" +
                   "${yaml.dump(dataConfig)}")
            unlock()
        }
    }
}


def status = { params ->
    if (params) {
        help ("status")
        error("Wrong parameters")
    }
    assertEnvReady()
    node(LABEL) {
        dir("${WORKSPACE}/../DebugDeploymentPipeline/config") {
            lock()
            def deathTimeReadable = updateDeathTime()
            if (fileExists("deployedRevision")) {
                deployedRevision = (readFile("deployedRevision")).trim()
                sh "git checkout ${deployedRevision} -- config"
                def data = json.parseText(
                    new String((readFile("config")).trim().decodeBase64()))
                sh "git reset --hard HEAD"

                def dataConfig = data.config ?: [:]
                reply ("Deployed config version ${deployedRevision}:\n" +
                       "${yaml.dump(dataConfig)}" +
                       "\n\nNew death time: ${deathTimeReadable}")
            } else {
                reply ("No changes are deployed." +
                       "\n\nNew death time: ${deathTimeReadable}")
            }
            unlock()
        }
    }
}


def getExecutor = { cmd ->
    def result
    switch (cmd) {
        case ~/|-h|(--|)help/:
            result = help
            break
        case "init":
            result = init
            break
        case "stop":
            result = stop
            break
        case "add":
            result = add
            break
        case "remove":
            result = remove
            break
        case "apply":
            result = apply
            break
        case "switch":
            result = checkout
            break
        case "status":
            result = status
            break
        default:
            result = help
    }
    return result
}

getExecutor(command)(params)
