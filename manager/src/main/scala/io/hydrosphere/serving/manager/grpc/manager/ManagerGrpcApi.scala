package io.hydrosphere.serving.manager.grpc.manager

import io.hydrosphere.serving.manager.ManagerServices
import io.hydrosphere.serving.manager.model.api.ContractOps
import io.hydrosphere.serving.tensorflow.api.predict.{PredictRequest, PredictResponse}
import io.hydrosphere.serving.tensorflow.api.prediction_service.PredictionServiceGrpc.PredictionService
import io.hydrosphere.serving.tensorflow.tensor.TensorProto
import io.hydrosphere.serving.tensorflow.types.DataType.DT_STRING
import org.apache.logging.log4j.scala.Logging
import spray.json.JsArray

import scala.concurrent.{ExecutionContext, Future}

class ManagerGrpcApi(managerServices: ManagerServices)
  (implicit ec: ExecutionContext) extends PredictionService with Logging {

  override def predict(request: PredictRequest) = {
    logger.info(request)
    Future.failed(new RuntimeException())
    /*TODO request.modelSpec match {
      case Some(x) =>
        val packedData = JsArray(ContractOps.TensorProtoOps.jsonify(request.inputs))
        val serveRequest = ServeRequest(
          serviceKey = ModelByName(x.name, x.version),
          servePath = "/serve",
          headers = Seq.empty,
          inputData = packedData.compactPrint.getBytes
        )


        managerServices.servingManagementService.serve(serveRequest).flatMap {
          case ExecutionSuccess(json, _) => Future.successful(
            PredictResponse(
              Map(
                "result" -> TensorProto(
                  dtype = DT_STRING,
                  stringVal = Seq(ByteString.copyFrom(json))
                )
              )
            )
          )
          case ExecutionFailure(error, _) => Future.failed(new RuntimeException(error))
        }
      case None => Future.failed(new IllegalArgumentException("ModelSpec is not defined"))
    }*/
  }
}
