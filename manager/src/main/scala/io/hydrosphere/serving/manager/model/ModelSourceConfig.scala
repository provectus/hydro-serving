package io.hydrosphere.serving.manager.model

case class ModelSourceConfigAux(
  id: Long,
  name: String,
  params: SourceParams
){
  def toTyped[T <: SourceParams]: ModelSourceConfig[T] = ModelSourceConfig(id, name, params.asInstanceOf[T])
}

case class ModelSourceConfig[T <: SourceParams](
  id: Long,
  name: String,
  params: T
) {
  def toAux: ModelSourceConfigAux = {
    ModelSourceConfigAux(id, name, params)
  }
}

trait SourceParams

case class LocalSourceParams() extends SourceParams

case class AWSAuthKeys(keyId: String, secretKey: String) {
  def hide: AWSAuthKeys = AWSAuthKeys("***************", "***************")
}

case class S3SourceParams (
  awsAuth: Option[AWSAuthKeys],
  bucketName: String,
  path: String,
  region: String
) extends SourceParams