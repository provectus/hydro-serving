package io.hydrosphere.serving.manager.it.service

import cats.data.EitherT
import cats.instances.all._
import io.hydrosphere.serving.manager.controller.application._
import io.hydrosphere.serving.manager.controller.model.ModelUpload
import io.hydrosphere.serving.manager.it.FullIntegrationSpec
import io.hydrosphere.serving.manager.model.db._
import io.hydrosphere.serving.manager.service.model_build.BuildModelRequest
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class ApplicationServiceITSpec extends FullIntegrationSpec with BeforeAndAfterAll {
  implicit val awaitTimeout = 50.seconds
  val upload1 = ModelUpload(
    packModel("/models/dummy_model"),
    name = Some("m1")
  )
  val upload2 = ModelUpload(
    packModel("/models/dummy_model_2"),
    name = Some("m2")
  )

  "Application service" should {
    "create a simple application" in {
      for {
        modelBuild <- managerServices.modelBuildManagmentService.buildModel(BuildModelRequest(1))
        appRequest = CreateApplicationRequest(
          name = "testapp",
          namespace = None,
          executionGraph = ExecutionGraphRequest(
            stages = List(
              ExecutionStepRequest(
                services = List(
                  SimpleServiceDescription(
                    runtimeId = 1, // dummy runtime id
                    modelVersionId = Some(modelBuild.right.get.request.modelBuild.id),
                    environmentId = None,
                    weight = 0,
                    signatureName = "default"
                  )
                )
              )
            )
          ),
          kafkaStreaming = List.empty
        )
        version = awaitVersion(modelBuild.right.get.request.modelBuild.id)
        appResult <- managerServices.applicationManagementService.createApplication(
          appRequest.name,
          appRequest.namespace,
          appRequest.executionGraph,
          appRequest.kafkaStreaming
        )
      } yield {
        println(appResult)
        val app = appResult.right.get
        println(app)
        val expectedGraph = ApplicationExecutionGraph(
          List(
            ApplicationStage(
              List(
                WeightedService(
                  ServiceKeyDescription(
                    runtimeId = 1,
                    modelVersionId = Some(version.id),
                    environmentId = None
                  ),
                  weight = 100,
                  signature = None
                )
              ),
              None
            )
          )
        )
        assert(app.name === appRequest.name)
        assert(app.contract === modelBuild.right.get.request.modelBuild.model.modelContract)
        assert(app.executionGraph === expectedGraph)
      }
    }

    "create a multi-service stage" in {
      for {
        buildResult <- managerServices.modelBuildManagmentService.buildModel(BuildModelRequest(1))
        build = buildResult.right.get
        version = awaitVersion(build.request.modelBuild.id)
        appRequest = CreateApplicationRequest(
          name = "MultiServiceStage",
          namespace = None,
          executionGraph = ExecutionGraphRequest(
            stages = List(
              ExecutionStepRequest(
                services = List(
                  SimpleServiceDescription(
                    runtimeId = 1, // dummy runtime id
                    modelVersionId = Some(version.id),
                    environmentId = None,
                    weight = 50,
                    signatureName = "default_spark"
                  ),
                  SimpleServiceDescription(
                    runtimeId = 1, // dummy runtime id
                    modelVersionId = Some(version.id),
                    environmentId = None,
                    weight = 50,
                    signatureName = "default_spark"
                  )
                )
              )
            )
          ),
          kafkaStreaming = List.empty
        )
        appRes <- managerServices.applicationManagementService.createApplication(
          appRequest.name,
          appRequest.namespace,
          appRequest.executionGraph,
          appRequest.kafkaStreaming
        )
      } yield {
        assert(appRes.isRight, appRes)
        val app = appRes.right.get
        println(app)
        val expectedGraph = ApplicationExecutionGraph(
          List(
            ApplicationStage(
              List(
                WeightedService(
                  ServiceKeyDescription(
                    runtimeId = 1,
                    modelVersionId = Some(version.id),
                    environmentId = None
                  ),
                  weight = 50,
                  signature = build.request.modelBuild.model.modelContract.signatures.find(_.signatureName == "default_spark")
                ),
                WeightedService(
                  ServiceKeyDescription(
                    runtimeId = 1,
                    modelVersionId = Some(version.id),
                    environmentId = None
                  ),
                  weight = 50,
                  signature = build.request.modelBuild.model.modelContract.signatures.find(_.signatureName == "default_spark")
                )
              ),
              build.request.modelBuild.model.modelContract.signatures.find(_.signatureName == "default_spark").map(_.withSignatureName("0"))
            )
          )
        )
        assert(app.name === appRequest.name)
        assert(app.executionGraph === expectedGraph)
      }
    }

    "create and update an application with kafkaStreaming" in {
      for {
        buildResult <- managerServices.modelBuildManagmentService.buildModel(BuildModelRequest(1))
        version = awaitVersion(buildResult.right.get.request.modelBuild.id)
        appRequest = CreateApplicationRequest(
          name = "kafka_app",
          namespace = None,
          executionGraph = ExecutionGraphRequest(
            stages = List(
              ExecutionStepRequest(
                services = List(
                  SimpleServiceDescription(
                    runtimeId = 1, // dummy runtime id
                    modelVersionId = Some(version.id),
                    environmentId = None,
                    weight = 100,
                    signatureName = "default"
                  )
                )
              )
            )
          ),
          kafkaStreaming = List(
            ApplicationKafkaStream(
              sourceTopic = "source",
              destinationTopic = "dest",
              consumerId = None,
              errorTopic = None
            )
          )
        )
        appRes <- managerServices.applicationManagementService.createApplication(
          appRequest.name,
          appRequest.namespace,
          appRequest.executionGraph,
          appRequest.kafkaStreaming
        )
        app = appRes.right.get

        appResNew <- managerServices.applicationManagementService.updateApplication(
          app.id,
          app.name,
          app.namespace,
          appRequest.executionGraph,
          Seq.empty
        )
        appNew = appResNew.right.get

        maybeGotNewApp <- managerServices.applicationManagementService.getApplication(appNew.id)
      } yield {
        println(app)
        assert(maybeGotNewApp.isRight, s"Couldn't find updated application in repository ${appNew}")
        assert(appNew === maybeGotNewApp.right.get)
        assert(appNew.kafkaStreaming.isEmpty, appNew)
      }
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()

    dockerClient.pull("hydrosphere/serving-runtime-dummy:latest")

    val f = for {
      d1 <- EitherT(managerServices.modelManagementService.uploadModel(upload1))
      d2 <- EitherT(managerServices.modelManagementService.uploadModel(upload2))
    } yield {
      println(s"UPLOADED: $d1")
      d2
    }

    Await.result(f.value, 30 seconds)
  }
}
