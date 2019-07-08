import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import bintray.BintrayCredentials
import bintray.BintrayKeys.{ bintrayEnsureCredentials, bintrayOrganization, bintrayPackage }
import bintry.Client
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.debian.DebianPlugin.autoImport.Debian
import com.typesafe.sbt.packager.rpm.RpmPlugin.autoImport.Rpm
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.Universal
import dispatch.{ FunctionHandler, Http }
import sbt.Keys._
import sbt._

object Bintray extends AutoPlugin {

  object autoImport {
    val publishRelease: TaskKey[Unit] = taskKey[Unit]("Publish binary in bintray")
    val publishLatest: TaskKey[Unit] = taskKey[Unit]("Publish latest binary in bintray")
    val publishDebian: TaskKey[Unit] = taskKey[Unit]("publish debian package in Bintray")
    val publishRpm: TaskKey[Unit] = taskKey[Unit]("publish rpm package in Bintray")
    val rpmReleaseFile = taskKey[File]("The rpm release package file")
    val publishRpmRelease = taskKey[Unit]("publish rpm release package in Bintray")
  }

  import autoImport._

  override lazy val projectSettings = Seq(

    publishRelease in ThisBuild := {
      val file = (packageBin in Universal).value
      btPublish(file.getName,
        file,
        bintrayEnsureCredentials.value,
        bintrayOrganization.value,
        "binary",
        bintrayPackage.value,
        (version in ThisBuild).value,
        sLog.value)
    },

    publishLatest in ThisBuild := Def.taskDyn {
      if ((version in ThisBuild).value.endsWith("-SNAPSHOT")) sys.error("Snapshot version can't be released")
      val file = (packageBin in Universal).value
      val latestVersion = if (version.value.contains('-')) "latest-beta" else "latest"
      val latestName = file.getName.replace(version.value, latestVersion)
      if (latestName == file.getName)
        Def.task {
          sLog.value.warn(s"Latest package name can't be built using package name [$latestName], publish aborted")
        }
      else Def.task {
        removeVersion(bintrayEnsureCredentials.value,
          bintrayOrganization.value,
          "binary",
          bintrayPackage.value,
          latestVersion,
          sLog.value)
        btPublish(latestName,
          file,
          bintrayEnsureCredentials.value,
          bintrayOrganization.value,
          "binary",
          bintrayPackage.value,
          latestVersion,
          sLog.value)
      }
    }
      .value,

    publishDebian in ThisBuild := {
      if ((version in ThisBuild).value.endsWith("-SNAPSHOT")) sys.error("Snapshot version can't be released")
      val file = (debianSign in Debian).value
      val bintrayCredentials = bintrayEnsureCredentials.value
      btPublish(file.getName,
        file,
        bintrayCredentials,
        bintrayOrganization.value,
        "debian-beta",
        bintrayPackage.value,
        version.value,
        sLog.value,
        "deb_distribution" → "any",
        "deb_component" → "main",
        "deb_architecture" → "all"
      )
      if (!version.value.contains('-'))
        btPublish(file.getName,
          file,
          bintrayCredentials,
          bintrayOrganization.value,
          "debian-stable",
          bintrayPackage.value,
          version.value,
          sLog.value,
          "deb_distribution" → "any",
          "deb_component" → "main",
          "deb_architecture" → "all"
        )
    },

    publishRpm in ThisBuild := {
      if ((version in ThisBuild).value.endsWith("-SNAPSHOT")) sys.error("Snapshot version can't be released")
      val file = (packageBin in Rpm).value
      val bintrayCredentials = bintrayEnsureCredentials.value
      btPublish(file.getName,
        file,
        bintrayCredentials,
        bintrayOrganization.value,
        "rpm-beta",
        bintrayPackage.value,
        (version in Rpm).value + '-' + (rpmRelease in Rpm).value,
        sLog.value)
      if (!version.value.contains('-'))
        btPublish(file.getName,
          file,
          bintrayCredentials,
          bintrayOrganization.value,
          "rpm-stable",
          bintrayPackage.value,
          (version in Rpm).value + '-' + (rpmRelease in Rpm).value,
          sLog.value)
    },

    publishRpmRelease in ThisBuild := {
      val file = rpmReleaseFile.value
      btPublish(file.getName,
        file,
        bintrayEnsureCredentials.value,
        bintrayOrganization.value,
        "rpm-stable",
        "thehive-project-release",
        "1.1.0",
        sLog.value)
    }
  )

  private def asStatusAndBody = new FunctionHandler({ r ⇒ (r.getStatusCode, r.getResponseBody) })

  def removeVersion(credential: BintrayCredentials,
                    org: Option[String],
                    repoName: String,
                    packageName: String,
                    version: String,
                    log: Logger): Unit = {
    val BintrayCredentials(user, key) = credential
    val client: Client = Client(user, key, new Http())
    val repo: Client#Repo = client.repo(org.getOrElse(user), repoName)
    Await.result(repo.get(packageName).version(version).delete(asStatusAndBody), Duration.Inf) match {
      case (status, body) ⇒ log.info(s"Delete version $packageName $version: $status ($body)")
    }
  }

  private def btPublish(filename: String,
                        file: File,
                        credential: BintrayCredentials,
                        org: Option[String],
                        repoName: String,
                        packageName: String,
                        version: String,
                        log: Logger,
                        additionalParams: (String, String)*): Unit = {
    val BintrayCredentials(user, key) = credential
    val owner: String = org.getOrElse(user)
    val client: Client = Client(user, key, new Http())
    val repo: Client#Repo = client.repo(org.getOrElse(user), repoName)


    val params = additionalParams
      .map { case (k, v) ⇒ s"$k=$v" }
      .mkString(";", ";", "")
    val upload = repo.get(packageName).version(version).upload(filename + params, file)

    log.info(s"Uploading $file ... (${org.getOrElse(user)}/$repoName/$packageName/$version/$filename$params)")
    Await.result(upload(asStatusAndBody), Duration.Inf) match {
      case (201, _) ⇒ log.info(s"$file was uploaded to $owner/$packageName@$version")
      case (_, fail) ⇒ sys.error(s"failed to upload $file to $owner/$packageName@$version: $fail")
    }
  }
}