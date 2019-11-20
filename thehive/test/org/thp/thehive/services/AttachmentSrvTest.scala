package org.thp.thehive.services

import java.io.{File, InputStream}
import java.nio.file.{Path, Files => JFiles}
import java.util.UUID

import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.libs.Files
import play.api.libs.Files.TemporaryFileCreator
import play.api.test.{NoTemporaryFileCreator, PlaySpecification}

import scala.annotation.tailrec
import scala.util.Try

class AttachmentSrvTest extends PlaySpecification {
  val dummyUserSrv = DummyUserSrv(userId = "user1@thehive.local", organisation = "cert")

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val attachmentSrv: AttachmentSrv      = app.instanceOf[AttachmentSrv]
    val db: Database                      = app.instanceOf[Database]
    implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

    @tailrec
    def streamCompare(is1: InputStream, is2: InputStream): Boolean = {
      val n1 = is1.read()
      val n2 = is2.read()
      if (n1 == -1 || n2 == -1) n1 == n2
      else (n1 == n2) && streamCompare(is1, is2)
    }

    s"[$name] attachment service" should {
      "create an attachment from a file" in {
        WithFakeScalligraphFile { tempFile =>
          val r = db.tryTransaction(implicit graph => attachmentSrv.create(FFile("test.txt", tempFile.path, "text/plain")))

          r must beSuccessfulTry.which { a =>
            a.name shouldEqual "test.txt"
            a.contentType shouldEqual "text/plain"
            a.size shouldEqual JFiles.size(tempFile.path)
            a.hashes must containAllOf(attachmentSrv.hashers.fromPath(tempFile.path))
          }
        }
      }

      "create an attachment from file data" in {
        WithFakeScalligraphFile { tempFile =>
          val r = db.tryTransaction(implicit graph => attachmentSrv.create("test2.txt", "text/plain", JFiles.readAllBytes(tempFile)))

          r must beSuccessfulTry.which { a =>
            a.name shouldEqual "test2.txt"
            a.contentType shouldEqual "text/plain"
            a.size shouldEqual JFiles.size(tempFile.path)
            a.hashes must containAllOf(attachmentSrv.hashers.fromPath(tempFile.path))
          }
        }
      }

      "get an attachment" in {
        val allAttachments = db.roTransaction(implicit graph => attachmentSrv.initSteps.toList)
        allAttachments must not(beEmpty)

        db.roTransaction { implicit graph =>
          attachmentSrv.get(allAttachments.head.attachmentId).exists() must beTrue
        }
      }

      "stream an attachment" in {
        WithFakeScalligraphFile { tempFile =>
          val a = db.tryTransaction(implicit graph => attachmentSrv.create("test3.txt", "text/plain", JFiles.readAllBytes(tempFile))).get

          streamCompare(attachmentSrv.stream(a), JFiles.newInputStream(tempFile.path)) must beTrue
        }
      }.pendingUntilFixed("cf FIXME stream id in AttachmentSrv")
    }
  }
}

object WithFakeScalligraphFile {

  def apply[A](body: Files.TemporaryFile => A): A = {
    val tempDir = new File("/tmp/thp")
    val tempFile = File.createTempFile("thehive-", "-test", tempDir)
    JFiles.write(tempFile.toPath, s"hello ${UUID.randomUUID()}".getBytes)
    val fakeTempFile = new Files.TemporaryFile {
      override def path: Path                                 = tempFile.toPath
      override def file: File                                 = tempFile
      override def temporaryFileCreator: TemporaryFileCreator = NoTemporaryFileCreator
    }
    try body(fakeTempFile)
    finally {
      JFiles.deleteIfExists(tempFile.toPath)
      ()
    }
  }
}
