package io.hydrosphere.serving.manager.service.modelsource.s3

import java.io.{File, FileNotFoundException}
import java.net.URI
import java.nio.file._

import scala.collection.JavaConversions._
import io.hydrosphere.serving.manager.service.modelsource.ModelSource
import io.hydrosphere.serving.manager.service.modelsource.local._
import io.hydrosphere.serving.manager.util.FileUtils.RecursiveRemover
import org.apache.logging.log4j.scala.Logging

class S3ModelSource(val sourceDef: S3SourceDef) extends ModelSource with Logging {
  private[this] val localFolder = Paths.get("tmp", sourceDef.name)
  private[this]val localSource = new LocalModelSource(
    LocalSourceDef(s"s3-proxy-${sourceDef.name}")
  )

  private val client = sourceDef.s3Client

  override def getSubDirs(path: String): List[String] = {
    logger.debug(s"getSubDirs: $path")
    client
      .listObjects(sourceDef.bucket)
      .getObjectSummaries
      .map(_.getKey)
      .filter(_.startsWith(path))
      .map(_.split(path).last.split("/"))
      .filterNot(_.length == 1)
      .map(_ (1))
      .distinct.toList
  }

  override def getAllFiles(folder: String): List[String] = {
    logger.debug(s"getAllFiles: $folder")
    val modelKeys = client
      .listObjects(sourceDef.bucket, folder)
      .getObjectSummaries
      .map(_.getKey)
      .filterNot(_.endsWith("/"))
    logger.debug(s"modelKeys=$modelKeys")
    modelKeys
      .map(URI.create)
      .map(_.toString)
      .map(getReadableFile)

    val r = localSource.getAllFiles(localFolder.resolve(folder).toString)
    logger.debug(s"getAllFiles=$r")
    r
  }

  override def getReadableFile(path: String): File = {
    logger.debug(s"getReadableFile: $path")
    val fullObjectPath = URI.create(s"$path")
    val file = localFolder.resolve(path)
    if (Files.exists(file)) downloadObject(fullObjectPath.toString)
    file.toFile
  }

  override def getAbsolutePath(path: String): Path = {
    logger.debug(s"getAbsolutePath: $path")
    localFolder.resolve(path)
  }

  override def isExist(path: String): Boolean = {
    if (client.doesObjectExist(sourceDef.bucket, path)) {
      true
    } else {
      client.listObjects(sourceDef.bucket, path).getObjectSummaries.nonEmpty
    }
  }

  override def writeFile(path: String, localFile: File): Unit = {
    client.putObject(sourceDef.bucket, path, localFile)
  }

  private def deleteProxyObject(objectPath: String): Boolean = {
    val path = localFolder.resolve(objectPath)
    if (Files.exists(path) && Files.isDirectory(path)) {
      Files.walkFileTree(path, new RecursiveRemover())
    }
    Files.deleteIfExists(path)
  }

  private def downloadObject(objectPath: String): File = {
    logger.debug(s"downloadObject: $objectPath")
    if (!client.doesObjectExist(sourceDef.bucket, objectPath))
      throw new FileNotFoundException(objectPath)
    val fileStream = client.getObject(sourceDef.bucket, objectPath).getObjectContent

    val folderStructure = localFolder.resolve(objectPath.split("/").dropRight(1).mkString("/"))

    if (Files.isRegularFile(folderStructure)) { // FIX sometimes s3 puts the entire folder
      Files.delete(folderStructure)
    }

    Files.createDirectories(folderStructure)

    val file = Files.createFile(localFolder.resolve(objectPath))
    Files.copy(fileStream, file, StandardCopyOption.REPLACE_EXISTING)
    file.toFile
  }
}
