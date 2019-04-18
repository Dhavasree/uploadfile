package pipeline.views

import javaposse.jobdsl.dsl.views.BuildPipelineView

public class <project_name_camelcase>PipelineView {
  static make(Map common_vars, BuildPipelineView view) {
    view.with {
      description("${common_vars.app} Delivery Pipeline.")
      filterBuildQueue()
      filterExecutors()
      title("${common_vars.app} Delivery Pipeline")
      displayedBuilds(10)
      selectedJob("${common_vars.jobPrefix}_pipeline_configuring")
      alwaysAllowManualTrigger()
      showPipelineParameters()
      refreshFrequency(5)
    }
  }
}
