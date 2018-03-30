package io.hydrosphere.serving.manager.service.modelsource.local

import io.hydrosphere.serving.manager.model.{LocalSourceParams, ModelSourceConfig}
import io.hydrosphere.serving.manager.service.modelsource.SourceDef

case class LocalSourceDef(
  name: String
) extends SourceDef

object LocalSourceDef{
  def fromConfig(localModelSourceConfiguration: ModelSourceConfig[LocalSourceParams]): LocalSourceDef =
    new LocalSourceDef(localModelSourceConfiguration.name)
}