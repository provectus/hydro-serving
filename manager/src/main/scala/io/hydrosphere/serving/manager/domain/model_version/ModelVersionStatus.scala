package io.hydrosphere.serving.manager.domain.model_version

object ModelVersionStatus extends Enumeration {
  type ModelVersionStatus = Value
  val Started, Finished, Failed = Value
}