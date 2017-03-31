import java.io.File

import bintray.BintrayCredentials
import bintray.BintrayKeys.{bintrayEnsureCredentials, bintrayOrganization, bintrayPackage, bintrayRepository}
import bintry.Client
import com.typesafe.sbt.packager.debian.DebianPlugin.autoImport.Debian
import com.typesafe.sbt.packager.rpm.RpmPlugin.autoImport.Rpm
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.Universal
import dispatch.{FunctionHandler, Http}
import sbt.Keys._
import sbt._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object PublishToBinTray extends Plugin {
  val publishRelease = taskKey[Unit]("Publish binary in Bintray")
  val publishLatest = taskKey[Unit]("Publish latest binary in Bintray")
  val publishDebian = taskKey[Unit]("publish debian package in Bintray")
  val publishRpm = taskKey[Unit]("publish rpm package in Bintray")

  override def settings = Seq(
    publishRelease := {
      val file = (packageBin in Universal).value
      btPublish(file.getName,
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
      else {
        removeVersion(bintrayEnsureCredentials.value,
          bintrayOrganization.value,
          bintrayRepository.value,
          bintrayPackage.value,
          "latest", sLog.value)
        btPublish(latestName,
          file,
          bintrayEnsureCredentials.value,
          bintrayOrganization.value,
          bintrayRepository.value,
          bintrayPackage.value,
          "latest",
          sLog.value)
      }
    },
    publishDebian := {
      val file = (packageBin in Debian).value
      btPublish(file.getName,
        file,
        bintrayEnsureCredentials.value,
        bintrayOrganization.value,
        bintrayRepository.value,
        bintrayPackage.value,
        version.value,
        sLog.value)
    },
    publishRpm := {
      val file = (packageBin in Rpm).value
      btPublish(file.getName,
        file,
        bintrayEnsureCredentials.value,
        bintrayOrganization.value,
        bintrayRepository.value,
        bintrayPackage.value,
        version.value,
        sLog.value)
    }

  )

  private def asStatusAndBody = new FunctionHandler({ r => (r.getStatusCode, r.getResponseBody) })

  def removeVersion(credential: BintrayCredentials, org: Option[String], repoName: String, packageName: String, version: String, log: Logger) = {
    val BintrayCredentials(user, key) = credential
    val client: Client = Client(user, key, new Http())
    val repo: Client#Repo = client.repo(org.getOrElse(user), repoName)
    Await.result(repo.get(packageName).version(version).delete(asStatusAndBody), Duration.Inf) match {
      case (status, body) => log.info(s"Delete version $packageName $version: $status ($body)")
    }
  }

  private def btPublish(filename: String, file: File, credential: BintrayCredentials, org: Option[String], repoName: String, packageName: String, version: String, log: Logger) = {
    val BintrayCredentials(user, key) = credential
    val owner: String = org.getOrElse(user)
    val client: Client = Client(user, key, new Http())
    val repo: Client#Repo = client.repo(org.getOrElse(user), repoName)

    log.info(s"Uploading $file ...")
    Await.result(repo.get(packageName).version(version).upload(filename, file)(asStatusAndBody), Duration.Inf) match {
      case (201, _) => log.info(s"$file was uploaded to $owner/$packageName@$version")
      case (_, fail) => sys.error(s"failed to upload $file to $owner/$packageName@$version: $fail")
    }
  }
}