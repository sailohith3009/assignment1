//This groovy file is used for Jenkins node methods.

def node_create(String name, String host, String port = '22',
                String key = 'jenkins-slave-ssh', Number numOfExecutors = 2) {
    config = node_config(name, host, key, numOfExecutors, port)
    withCredentials([usernamePassword(credentialsId: 'jenkins-token',
                                      usernameVariable: 'JENKINS_USER',
                                      passwordVariable: 'JENKINS_TOKEN')]) {

        if ( JENKINS_CLI_URL =~ "http[s://|://].*" ) {
            opts = "-s \$JENKINS_CLI_URL -auth \$JENKINS_USER:\$JENKINS_TOKEN"
            java_args = "-Djavax.net.ssl.trustStore=/var/jenkins_home/JenkinsKeystore -Djavax.net.ssl.trustStorePassword=changeit -jar"
        } else {
            opts = "-s http://\$JENKINS_CLI_URL -auth \$JENKINS_USER:\$JENKINS_TOKEN"
            java_args = "-jar"
        }
        cmd = "echo '${config}' | java ${java_args} \$JENKINS_CLI ${opts} create-node ${name}"
        sh (script: cmd, returnStatus: true)
    }
}


def node_delete(String name) {
    withCredentials([usernamePassword(credentialsId: 'jenkins-token',
                                      usernameVariable: 'JENKINS_USER',
                                      passwordVariable: 'JENKINS_TOKEN')]) {

        if ( JENKINS_CLI_URL =~ "http[s://|://].*" ) {
            opts = "-s \$JENKINS_CLI_URL -auth \$JENKINS_USER:\$JENKINS_TOKEN"
            java_args = "-Djavax.net.ssl.trustStore=/var/jenkins_home/JenkinsKeystore -Djavax.net.ssl.trustStorePassword=changeit -jar"
        } else {
            opts = "-s http://\$JENKINS_CLI_URL -auth \$JENKINS_USER:\$JENKINS_TOKEN"
            java_args = "-jar"
        }
        cmd = "java ${java_args} \$JENKINS_CLI $opts delete-node $name"
        code = sh (script: cmd , returnStatus: true)
        // todo: handle exit code properly
    }
}

//jenkins-slave-ssh is already in use for the foundry.  We need to standardize to something not in use.
def node_config(String name, String host, String key, Number numOfExecutors, String port) {
    config = """<slave>
        <name>${name}</name>
        <description></description>
        <remoteFS>/home/ubuntu/jenkins</remoteFS>
        <numExecutors>${numOfExecutors}</numExecutors>
        <mode>EXCLUSIVE</mode>
        <retentionStrategy class=\"hudson.slaves.RetentionStrategy\$Always\"/>
        <launcher class=\"hudson.plugins.sshslaves.SSHLauncher\" plugin=\"ssh-slaves@1.5\">
        <host>${host}</host>
        <port>${port}</port>
        <credentialsId>${key}</credentialsId>
        <maxNumRetries>0</maxNumRetries>
        <retryWaitTime>0</retryWaitTime>
        </launcher>
        <label>${name}</label>
        <nodeProperties/>
        <sshHostKeyVerificationStrategy class="hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy"/>
        <userId>ubuntu</userId>
        </slave>"""
    return config
}
