package io.hydrosphere.serving.manager.service

import java.nio.file.{Files, Path, Paths}
import java.time.LocalDateTime

import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.controller.model.UploadedEntity.ModelUpload
import io.hydrosphere.serving.manager.model.Model
import io.hydrosphere.serving.manager.model.api.{ModelMetadata, ModelType}
import io.hydrosphere.serving.manager.repository.ModelRepository
import io.hydrosphere.serving.manager.service.modelsource.local.{LocalModelSource, LocalSourceDef}
import io.hydrosphere.serving.manager.util.TarGzUtils
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{Matchers, Mockito}
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Success

class ModelManagementSpec extends GenericUnitTest with MockitoSugar {
  private[this] val dummyModel = Model(
    id = 1,
    name = "test1",
    source = "local:test1",
    modelType = ModelType.Unknown("test"),
    description = None,
    modelContract = ModelContract.defaultInstance,
    created = LocalDateTime.now(),
    updated = LocalDateTime.now()
  )
  import scala.concurrent.ExecutionContext.Implicits._

  def packModel(str: String): Path = {
    val temptar = Files.createTempFile("test_tf_model", ".tar.gz")
    TarGzUtils.compress(Paths.get(getClass.getResource(str).getPath), temptar, None)
    temptar
  }

  "Model management service" should "index deleted models" in {
    val modelRepo = mock[ModelRepository]
    val sourceMock = mock[SourceManagementService]

    Mockito.when(sourceMock.index("local:test1")).thenReturn(
      Future.successful(
        Success(None)
      )
    )
    Mockito.when(modelRepo.delete(1L)).thenReturn(Future.successful(1))
    Mockito.when(modelRepo.getMany(Set(1L))).thenReturn(
      Future.successful(
        Seq(dummyModel)
      )
    )

    val modelManagementService = new ModelManagementServiceImpl(modelRepo, null, null, null, null, null, sourceMock)

    val f = modelManagementService.indexModels(Set(1L)).map { statuses =>
      println(statuses)
      assert(statuses.nonEmpty)
      assert(!statuses.exists(_.isInstanceOf[IndexError]))
      assert(statuses.forall(_.isInstanceOf[ModelDeleted]))
    }

    Await.result(f, 10 seconds)
  }

  it should "index updated models" in {
    val modelRepo = mock[ModelRepository]
    val sourceMock = mock[SourceManagementService]

    Mockito.when(sourceMock.index("local:test1")).thenReturn(
      Future.successful(
        Success(Some(
          ModelMetadata(
            "newModel",
            ModelType.Unknown("test2"),
            ModelContract.defaultInstance.copy(modelName = "newmodel")
          )
        ))
      )
    )
    Mockito.when(modelRepo.update(Matchers.any(classOf[Model]))).thenReturn(Future.successful(1))
    Mockito.when(modelRepo.getMany(Set(1L))).thenReturn(
      Future.successful(
        Seq(dummyModel)
      )
    )

    val modelManagementService = new ModelManagementServiceImpl(modelRepo, null, null, null, null, null, sourceMock)

    val f = modelManagementService.indexModels(Set(1L)).map { statuses =>
      println(statuses)
      assert(statuses.nonEmpty)
      assert(!statuses.exists(_.isInstanceOf[IndexError]))
      assert(statuses.forall(_.isInstanceOf[ModelUpdated]))
    }

    Await.result(f, 10 seconds)
  }

  it should "upload new model" in {
    val testSourcePath = Files.createTempDirectory("upload-test").toString
    println("Test source path: " + testSourcePath)
    val upload = ModelUpload(
      "test",
      "unknown:unknown",
      ModelContract.defaultInstance,
      None,
      None,
      packModel("/test_models/tensorflow_model")
    )
    println(upload)
    val model = Model(
      id = 1,
      name = "tensorflow_model",
      source = "test:tensorflow_model",
      modelType = ModelType.Tensorflow(),
      description = None,
      modelContract = ModelContract.defaultInstance,
      created = LocalDateTime.now(),
      updated = LocalDateTime.now()
    )
    val modelRepo = mock[ModelRepository]
    val sourceMock = mock[SourceManagementService]

    Mockito.when(sourceMock.getSources).thenReturn(
      Future.successful(
        List(
          new LocalModelSource(LocalSourceDef("test", Some(testSourcePath)))
        )
      )
    )
    Mockito.when(modelRepo.get("test")).thenReturn(Future.successful(None))
    Mockito.when(modelRepo.create(Matchers.any(classOf[Model]))).thenReturn(Future.successful(model))

    val modelManagementService = new ModelManagementServiceImpl(modelRepo, null, null, null, null, null, sourceMock)

    val f = modelManagementService.uploadModelTarball(upload).map{ maybeModel =>
      maybeModel shouldBe defined
      val rModel = maybeModel.get
      rModel should equal(model)
    }
    Await.result(f, 20 seconds)
  }

  it should "upload existing model" in {
    val testSourcePath = Files.createTempDirectory("upload-test").toString
    println("Test source path: " + testSourcePath)
    val upload = ModelUpload(
      "test",
      "unknown:unknown",
      ModelContract.defaultInstance,
      None,
      None,
      packModel("/test_models/tensorflow_model")
    )
    println(upload)
    val model = Model(
      id = 1,
      name = "tensorflow_model",
      source = "test:tensorflow_model",
      modelType = ModelType.Tensorflow(),
      description = None,
      modelContract = ModelContract.defaultInstance,
      created = LocalDateTime.now(),
      updated = LocalDateTime.now()
    )
    val modelRepo = mock[ModelRepository]
    val sourceMock = mock[SourceManagementService]

    Mockito.when(sourceMock.getSources).thenReturn(
      Future.successful(
        List(
          new LocalModelSource(LocalSourceDef("test", Some(testSourcePath)))
        )
      )
    )
    Mockito.when(modelRepo.update(Matchers.any(classOf[Model]))).thenReturn(Future.successful(1))
    Mockito.when(modelRepo.get("test")).thenReturn(Future.successful(Some(model)))
    Mockito.when(modelRepo.get(1)).thenReturn(Future.successful(Some(model)))

    val modelManagementService = new ModelManagementServiceImpl(modelRepo, null, null, null, null, null, sourceMock)

    val f = modelManagementService.uploadModelTarball(upload).map{ maybeModel =>
      maybeModel shouldBe defined
      val rModel = maybeModel.get
      rModel.name should equal("test")
      rModel.source should equal("test:tensorflow_model")
    }
    Await.result(f, 20 seconds)
  }

  it should "add a new model" in {
    val sourceName = "test"
    val modelName = "tensorflow_model"
    val modelPath = getClass.getResource("/test_models/tensorflow_model").getPath

    val modelRepo = mock[ModelRepository]
    val sourceMock = mock[SourceManagementService]

    Mockito.when(sourceMock.getSource(sourceName)).thenReturn(Future.successful(Some(
      new LocalModelSource(LocalSourceDef(sourceName, None))
    )))
    Mockito.when(modelRepo.get(Matchers.any())).thenReturn(
      Future.successful(None)
    )
    Mockito.when(modelRepo.create(Matchers.any())).thenAnswer(new Answer[Future[Model]] {
      override def answer(invocation: InvocationOnMock): Future[Model] = {
        val model = invocation.getArguments.head.asInstanceOf[Model]
        Future.successful(model.copy(id = 1))
      }
    })

    val modelManagementService = new ModelManagementServiceImpl(modelRepo, null, null, null, null, null, sourceMock)

    val f = modelManagementService.addModel(sourceName, modelPath).map{ maybeModel =>
      maybeModel shouldBe defined
      val model = maybeModel.get
      model.name should equal(modelPath)
    }

    Await.result(f, 20 seconds)
  }

  it should "reject an addition of existing model" in {
    val sourceName = "test"
    val modelName = "tensorflow_model"
    val modelPath = getClass.getResource("/test_models/tensorflow_model").getPath

    val modelRepo = mock[ModelRepository]
    val sourceMock = mock[SourceManagementService]

    Mockito.when(sourceMock.getSource(sourceName)).thenReturn(Future.successful(Some(
      new LocalModelSource(LocalSourceDef(sourceName, None))
    )))
    Mockito.when(modelRepo.get(Matchers.any())).thenReturn(
      Future.successful(Some(dummyModel))
    )

    val modelManagementService = new ModelManagementServiceImpl(modelRepo, null, null, null, null, null, sourceMock)

    val f = modelManagementService.addModel(sourceName, modelPath).map{ maybeModel =>
      maybeModel shouldBe empty
    }

    Await.result(f, 20 seconds)
  }
}
