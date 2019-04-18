package pipeline.jobs

import javaposse.jobdsl.dsl.Job
import com.manheim.releng.pipeworks.builders.SimpleJobBuilder
import com.manheim.releng.pipeworks.configurators.HipChatNotificationConfigurator
import com.manheim.releng.pipeworks.configurators.SimpleConfigurator

public class DeploymentJobBuilder extends SimpleJobBuilder {
  String env
  String label = 'kitchensink'
  String tagPrefix
  Boolean terminateInactive = true

  Job build(Job job, Map project) {
    this.folder = project.app
    if (this.name == null) {
      this.name = 'deploying_' + this.env
    }

    if (this.tagPrefix != null && !this.tagPrefix.isEmpty()) {
      withConfigurator(new SimpleConfigurator().withClosure(
        {
          steps {
            shell("PREVIOUS_TAG=\$((${this.tagPrefix.toUpperCase()} - 1)); echo PREVIOUS_TAG=\$PREVIOUS_TAG > env.properties")
            environmentVariables { propertiesFile('env.properties') }
          }
        }))

      def message = "Deployment Complete: ${this.env.toUpperCase()} (<a href='http://github.ove.local/Manheim/inventory-match/compare/${this.tagPrefix}_\$PREVIOUS_TAG...${this.tagPrefix}_\$${this.tagPrefix.toUpperCase()}'>changes</a>)"
      withConfigurator(new HipChatNotificationConfigurator(message: message, token: '6a79369ac56e6d33cb4a510d828253', roomId: project.chatRoom))
    }

    String awssume_vars = ""
    String awssume_cmd = ""

    if (this.env == "production" || this.env == "uat") {
      awssume_vars = "AWS_ROLE_EXTERNAL_ID=bento_ods AWS_DEFAULT_REGION=us-east-1 AWS_ROLE_ARN=arn:aws:iam::664643933020:role/bento_prod_ods_iam_role"
      awssume_cmd = "awssume"
    }

    String terminateCommand = ""

    if (this.terminateInactive) {
      terminateCommand = """
        # Terminate inactive (blue) instance in ${this.env} environment
        bundle exec cap ${this.env} terminate_inactive
      """
    }

    this.command = """
      cd config/deploy

      # Setup JAVA_HOME
      #export JAVA_HOME="/usr/java/default"

      # Setup Ruby
      export RBENV_ROOT=~/.rbenv RBENV_VERSION=2.3.1

      export PATH="\${JAVA_HOME}/bin:/bin:/usr/bin:\${RBENV_ROOT}/shims:\${RBENV_ROOT}/bin:\${PATH}"

      if [ ! -f \${RBENV_ROOT}/shims/.rbenv-shim ]; then
        for i in {1..5}; do rbenv rehash && break || sleep 5; done
      fi

      # Install Gem Dependencies
      bundle install --path vendor/bundle

      # Deploy application to ${this.env} environment
      BUILD_VERSION=\${APP_REVISION} ${awssume_vars} bundle exec ${awssume_cmd} cap ${this.env} deploy

      ${terminateCommand}
    """

    super.build(job, project)

    job.with {
      description("This job deploys ${project.app} to the ${this.env} environment.")
    }

    job
  }
}
