import java.io.File

import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

import sbt._
import sbt.Keys._

import dispatch.{ Http, FunctionHandler }

import bintry.Client
import bintray.BintrayCredentials
import bintray.BintrayKeys.{ bintrayEnsureCredentials, bintrayOrganization, bintrayRepository, bintrayPackage }
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.Universal

object PublishToBinTray {
  val publishRelease = taskKey[Unit]("Publish binary in bintray")
  val publishLatest = taskKey[Unit]("Publish latest binary in bintray")

  lazy val settings = Seq(
    publishRelease := {
      val file = (packageBin in Universal).value
      publish(file.getName,
        file,
        bintrayEnsureCredentials.value,
        bintrayOrganization.value,
        bintrayRepository.value,
        bintrayPackage.value,
        version.value,
        sLog.value)
    },
    publishLatest := {
      val file = (packageBin in Universal).value
      val latestName = file.getName.replace(version.value, "latest")
      if (latestName == file.getName)
        sLog.value.warn(s"Latest package name can't be built using package name [$latestName], publish aborted")
      else
        publish(latestName,
          file,
          bintrayEnsureCredentials.value,
          bintrayOrganization.value,
          bintrayRepository.value,
          bintrayPackage.value,
          version.value,
          sLog.value)
    })

  private def asStatusAndBody = new FunctionHandler({ r => (r.getStatusCode, r.getResponseBody) })

  def publish(filename: String, file: File, credential: BintrayCredentials, org: Option[String], repoName: String, packageName: String, version: String, log: Logger) = {
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