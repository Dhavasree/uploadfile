package pipeline.configurators

import javaposse.jobdsl.dsl.Job
import com.manheim.releng.pipeworks.configurators.EmailConfigurator

public class ReleaseEmailConfigurator extends EmailConfigurator {
  Boolean includeTests = Boolean.TRUE
  Boolean includeRollbackPlan = Boolean.TRUE
  Boolean includeChangeId = Boolean.TRUE
  List externalDependencies = []

  Job addToJob(Job job) {
    super.withClosure( {
      wrappers {
        buildUserVars()
      }
    } )

    def dependenciesHtml = ""
    if (externalDependencies != null && externalDependencies.size() > 0) {
      dependenciesHtml += "<h2>Dependencies:</h2>"
      dependenciesHtml += "<ul>"
      externalDependencies.each { dep ->
        dependenciesHtml += """
          <li>
            <b>${dep.name}</b>
            <ul>
              <li><i>Integration Type</i>: ${dep.integrationType}</li>
              <li><i>Integration Relationship</i>: ${dep.relationship}</li>
              <li><i>Outage Effects</i>: ${dep.outageSymptom}</li>
            </ul>
          </li>
        """
      }
      dependenciesHtml += "</ul>"
    }

    def testsHtml = ""
    if (this.includeTests == true) {
      testsHtml += """
      <h2>Tests:</h2>
      <ul>
        <li><i>Units</i>: \${UNIT_TEST_URL}</li>
        <li><i>Integration Tests</i>: \${INTEGRATION_TEST_URL}</li>
      </ul>
      """
    }

    def rollbackPlanHtml = ""
    if (this.includeRollbackPlan == true) {
      rollbackPlanHtml += """
      <h2>Rollback Plan:</h2>
      <p>Revert the commits causing breakage and run the pipeline_sharedacc again.</p>
      """
    }

    def notesHtml = "<h2>Release Notes:</h2>"
    if (this.includeChangeId == true) {
      notesHtml = "<h2>Release Notes (\${CHANGEID}):</h2>"
    }
    notesHtml += "<ul>"
    notesHtml += "\${CHANGELOG_HTML}"
    notesHtml += "</ul>"

    def userHtml = "<p>Deployment process started by: \${BUILD_USER} (<a href=\"mailto:\${BUILD_USER_EMAIL}\">\${BUILD_USER_ID}</a>)</p>"

    this.body = """
      ${notesHtml}
      ${dependenciesHtml}
      ${rollbackPlanHtml}
      ${testsHtml}
      ${userHtml}
    """

    super.addToJob(job)
  }
}