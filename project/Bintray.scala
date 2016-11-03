import java.io.File

import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

import sbt.Logger

import dispatch.{ Http, FunctionHandler }

import bintry.Client
import bintray.BintrayCredentials

object BinTray {

  private def asStatusAndBody = new FunctionHandler({ r => (r.getStatusCode, r.getResponseBody) })

  def publish(file: File, credential: BintrayCredentials, org: Option[String], repoName: String, packageName: String, version: String, log: Logger) = {
    val BintrayCredentials(user, key) = credential
    val owner: String = org.getOrElse(user)
    val client: Client = Client(user, key, new Http())
    val repo: Client#Repo = client.repo(org.getOrElse(user), repoName)

    log.info(s"Uploading $file ...")
    Await.result(repo.get(packageName).version(version).upload(file.getName, file)(asStatusAndBody), Duration.Inf) match {
      case (201, _)  => log.info(s"$file was uploaded to $owner/$packageName@$version")
      case (_, fail) => sys.error(s"failed to upload $file to $owner/$packageName@$version: $fail")
    }
  }
}