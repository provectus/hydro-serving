package io.hydrosphere.serving.manager.service

import io.hydrosphere.serving.manager.ManagerConfigurationImpl
import io.hydrosphere.serving.manager.controller.model_source.AddLocalSourceRequest
import io.hydrosphere.serving.manager.model.{LocalSourceParams, ModelSourceConfig}
import io.hydrosphere.serving.manager.test.FullIntegrationSpec

class SourceServiceITSpec extends FullIntegrationSpec {

  override def configuration: ManagerConfigurationImpl = {
    val sources = Seq(
      ModelSourceConfig[LocalSourceParams](
        1,
        "test_config",
        LocalSourceParams(getClass.getResource("/models").getPath)
      ).toAux
    )
    super.configuration.copy(modelSources = sources)
  }

  "SourceService" should {
    "add a local source" in {
      val req = AddLocalSourceRequest(
        "test_api", getClass.getResource("/models").getPath
      )
      managerServices.sourceManagementService.addLocalSource(req).map { maybeSourceConfig =>
        assert(maybeSourceConfig.isRight)
        val modelSourceConfig = maybeSourceConfig.right.get
        assert(modelSourceConfig.name === req.name)
        assert(modelSourceConfig.params.isInstanceOf[LocalSourceParams], modelSourceConfig.params)
        assert(modelSourceConfig.params.asInstanceOf[LocalSourceParams].path === req.path)
      }
    }

    "reject similar source" in {
      val reqSuccess = AddLocalSourceRequest(
        "test", getClass.getResource("/models").getPath
      )
      val reqFail = AddLocalSourceRequest(
        "test", getClass.getResource("/models").getPath
      )
      for {
        successSource <- managerServices.sourceManagementService.addLocalSource(reqSuccess)
        failSource <- managerServices.sourceManagementService.addLocalSource(reqFail)
      } yield {
        assert(successSource.isRight)
        assert(failSource.isLeft)
      }
    }

    "list all sources (config+db)" in {
      managerServices.sourceManagementService.allSourceConfigs.map { sources =>
        println(sources)
        assert(sources.exists(_.name == "test_config"))
        assert(sources.exists(_.name == "test_api"))
        assert(sources.exists(_.name == "test"))
      }
    }
  }
}
