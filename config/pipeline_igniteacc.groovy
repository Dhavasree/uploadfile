import com.manheim.releng.pipeworks.*
import com.manheim.releng.pipeworks.builders.*
import com.manheim.releng.pipeworks.configurators.*
import pipeline.configurators.*
import pipeline.jobs.*
import pipeline.views.*

////////////////////////////////////////////
//  MASTER BRANCH PIPELINE CONFIGURATION  //
////////////////////////////////////////////

// Step1 - Setup app name
//   Find <project_name> and replace with the name of the project
// Step2 - Setup job prefix
//   Find <project_name_wo_special_characters> and replace with
//     name of project without hyphens or underscores
// Step 3 - Setup pipeline view name
//   Find <project_name_camelcase> and replace with
//     name of project without hyphens or underscores and
//     converted to camel case

def jobs = []

def common_vars = [
        pipeworksVersion    : '3.18.1',
        team                : 'dataengineering',
        app                 : '<project_name>',
        jobPrefix           : '<project_name_wo_special_characters>',
        chatRoom            : 'dataengineeringteam',
        repos               : [
                [
                        url   : 'git@ghe.coxautoinc.com:CAIS-DataEngineering/<project_name>.git',
                        branch: '${APP_REVISION}',
                ],
        ],
        configuring_repos   : [
                [
                        url   : 'git@ghe.coxautoinc.com:CAIS-DataEngineering/<project_name>.git',
                        branch: '${BRANCH}',
                ],
        ],
        externalDependencies: [
                [
                        name           : 'AWS',
                        relationship   : 'Internal App',
                        integrationType: 'Web Services',
                        outageSymptom  : 'Degraded service to customer',
                ]
        ],
        changeRequest       : [
                bau          : '152',
                product      : [
                        name : 'Software Delivery Pipeline',
                        tier1: 'Software',
                        tier2: 'Application',
                        tier3: 'Release Management',
                ],
                coordinator  : [
                        firstName: 'Alex',
                        lastName : 'Gavtadze',
                        loginName: 'agavtadze',
                ],
                notifications: [
                        replyTo   : 'alexander.gavtadze@manheim.com',
                        recipients: [
                                'alexander.gavtadze@coxautoinc.com',
                                'sujith.kannan@coxautoinc.com'
                        ],
                ],
        ],
        pipelineContext     : this
]

def over30 = JobDslVersion.isGreaterThan('1.30')
if (over30) {
    folder("${common_vars.app}")
} else {
    folder {
        name("${common_vars.app}")
    }
}

def setup_env_command = '''
  export SERVERLESS_PATH="/home/jenkins/.nodenv/versions/6.7.0/bin/"
  export PATH=$PATH:/home/jenkins/.nodenv/versions/6.7.0/bin/
'''


def setup_serverless_command = '''

  # setup serverless framework
  sudo npm install -g serverless
  
  # setup sonar scanner
  sudo npm install -g sonarqube-scanner

  # Install moto - mock for boto3
  sudo pip install moto

  # Install pytest and pytest-cov - testing and test coverage library for python
  sudo pip install pytest
  sudo pip install pytest-cov
  
  # Setup Serverless plugins
  serverless plugin install -n serverless-prune-plugin
  serverless plugin install -n serverless-plugin-lambda-dead-letter

'''

jobs << new ConfigureJobBuilder(name: "pipeline_configuring", folder: "${common_vars.app}", label: 'dev', parameters: [strings: [BRANCH: 'master']])
        .withDownstreamJob([name: 'repo_tagging', passRevision: false])

jobs << new TagRepoJobBuilder(folder: "${common_vars.app}", label: 'dev')
        .withDownstreamJob([name: 'testing_and_packaging', passRevision: false, passCurrent: true])

jobs << new SimpleJobBuilder(name: 'testing_and_packaging', folder: "${common_vars.app}", label: 'dev',
        command: """
    ${setup_env_command}
    ${setup_serverless_command}
    # Build application artifact

    # Ensure all python files are in root directory
    # Install any dependencies
    # Ex) pip install module-name -t ./

  """)
        .withConfigurator(new SimpleConfigurator())
        .withDownstreamJob([name: 'code_quality_gate', passRevision: false, passCurrent: true])

jobs << new SimpleJobBuilder(name: 'code_quality_gate', folder: "${common_vars.app}", label: 'dev',
        command: """
    ${setup_env_command}
    # Execute sonar scanner
    export AWS_DEFAULT_REGION="us-east-1"
    export AWS_REGION="us-east-1"
    coverage erase
    pytest tests/ --cov=src
    coverage xml -i
    sonar-scanner -Dsonar.host.url=http://sonarqube.awsdataservnp.manheim.com:9000 -Dsonar.login=ba4eb3dcbd5cab19eb227fe8c6daea74bf0d6ee0
    . \$WORKSPACE/config/pipeline/utils/sonarQualityGate.sh --source-only
  """)
        .withConfigurator(new SimpleConfigurator())
        .withDownstreamJob([name: 'deploying_nonprod', passRevision: false, passCurrent: true])


jobs << new SimpleJobBuilder(name: 'deploying_nonprod', folder: "${common_vars.app}", label: 'dev',
        command: """
    ${setup_env_command}
    ${setup_serverless_command}
    export environment="nonprod"
    export AWS_ROLE_EXTERNAL_ID="bento_dev_dataservices"
    export AWS_DEFAULT_REGION="us-east-1"
    export AWS_REGION="us-east-1"
    export AWS_ROLE_ARN="arn:aws:iam::423319072129:role/bento_dev_dataservices_iam_role"
    awssume bundle exec sls deploy -e nonprod --stage nonprod --force
   """)
        .withConfigurator(new SimpleConfigurator())
        .withDownstreamJob([name: 'release_tagging', manual: true, passRevision: false, passCurrent: true])

jobs << new SimpleJobBuilder(name: 'deploying_uat', folder: "${common_vars.app}", label: 'dev',
        command: """
    ${setup_env_command}
    ${setup_serverless_command}
    sls deploy -s uat -v--force
   """)
        .withConfigurator(new SimpleConfigurator())

jobs << new TagRepoJobBuilder(name: 'release_tagging', folder: "${common_vars.app}", label: 'dev', tagPrefix: 'rv')
        .withDownstreamJob([name: 'changelog_generating', passRevision: false, passCurrent: true])

jobs << new CreateChangelogJobBuilder(folder: "${common_vars.app}", label: 'dev', tagPrefix: 'rv')
        .withDownstreamJob([name: 'crq_opening', passRevision: false, passCurrent: true, file: 'CHANGELOG.properties'])

jobs << new OpenCrqJobBuilder(folder: "${common_vars.app}", label: 'dev', summary: "${common_vars.app} - Deploy - Production", rbenvVersion: '2.3.1')
        .withClosure({ steps { environmentVariables { propertiesFile('PIPELINE.properties') } } })
        .withConfigurator(new ReleaseEmailConfigurator(
        recipients: common_vars.changeRequest.notifications.recipients,
        subject: "${common_vars.team} - ${common_vars.app} - Deploy - Production",
        externalDependencies: common_vars.externalDependencies))
        .withDownstreamJob([name: 'deploying_production', passRevision: false, passCurrent: true])

jobs << new SimpleJobBuilder(name: 'deploying_production', folder: "${common_vars.app}", label: 'dev',
        command: """
    ${setup_env_command}
    ${setup_serverless_command}
    export environment="production"
    export AWS_ROLE_EXTERNAL_ID="bento_prod_dataservices"
    export AWS_DEFAULT_REGION="us-east-1"
    export AWS_ROLE_ARN="arn:aws:iam::678977883400:role/acct-managed/bento_prod_awsdataservices_iam_role"
    awssume bundle exec sls deploy -e production --stage production --force
   """)
        .withConfigurator(new SimpleConfigurator())

jobs << new CloseCrqJobBuilder(folder: "${common_vars.app}", label: 'dev', rbenvVersion: '2.3.1')
        .withConfigurator(new EmailConfigurator(
        recipients: common_vars.changeRequest.notifications.recipients,
        subject: "SUCCESS: ${common_vars.team} - ${common_vars.app} - Deploy - Production",
        body: '<p>The change associated with ${CHANGEID} has succeeded.</p>'))

jobs << new BackOutCrqJobBuilder(folder: "${common_vars.app}", label: 'dev', rbenvVersion: '2.3.1')
        .withConfigurator(new EmailConfigurator(
        recipients: common_vars.changeRequest.notifications.recipients,
        subject: "FAIL: ${common_vars.team} - ${common_vars.app} - Deploy - Production",
        body: '<p>The change associated with ${CHANGEID} has failed.</p>'))


def slackConfig = new SlackConfigurator(
        token: 'VHBq2fqZWcBWLTrsVdInHsex',
        chatRoom: "${common_vars.chatRoom}",
        notifyOnEvents: ['failure', 'backToNormal'],
        includeCommitAuthors: true,
        includeCommitTitles: true,
)

def scmConfig = new ScmConfigurator(repos: common_vars['repos'])
def scmPipelineConfig = new ScmConfigurator(repos: common_vars['configuring_repos'])

jobs.each { j ->
    j = j.build(over30 ? freeStyleJob('empty', {}) : job({}), common_vars)
    j.with {
        logRotator(-1, 10, -1, -1)
    }
    switch (j.name) {
        case ~/.*pipeline_config.*$/:
            scmPipelineConfig.addToJob(j)
            break
        default:
            scmConfig.addToJob(j)
            break
    }
    slackConfig.addToJob(j)
}

<project_name_camelcase>PipelineView.make(common_vars, over30 ? buildPipelineView("${common_vars.app}/Pipeline_${common_vars.app}") : view(type: BuildPipelineView) {
})
