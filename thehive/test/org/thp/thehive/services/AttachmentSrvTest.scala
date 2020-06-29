package org.thp.thehive.services

import java.io.{File, InputStream}
import java.nio.file.{Path, Files => JFiles}
import java.util.UUID

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import play.api.libs.Files
import play.api.libs.Files.TemporaryFileCreator
import play.api.test.{NoTemporaryFileCreator, PlaySpecification}

import scala.annotation.tailrec

class AttachmentSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").getSystemAuthContext

  @tailrec
  private def streamCompare(is1: InputStream, is2: InputStream): Boolean = {
    val n1 = is1.read()
    val n2 = is2.read()
    if (n1 == -1 || n2 == -1) n1 == n2
    else (n1 == n2) && streamCompare(is1, is2)
  }

  "attachment service" should {
    "create an attachment from a file" in testApp { app =>
      WithFakeScalligraphFile { tempFile =>
        val r =
          app[Database].tryTransaction(implicit graph => app[AttachmentSrv].create(FFile("test.txt", tempFile.path, "text/plain")))

        r must beSuccessfulTry.which { a =>
          a.name shouldEqual "test.txt"
          a.contentType shouldEqual "text/plain"
          a.size shouldEqual JFiles.size(tempFile.path)
          a.hashes must containAllOf(app[AttachmentSrv].hashers.fromPath(tempFile.path))
        }
      }
    }

    "create an attachment from file data" in testApp { app =>
      WithFakeScalligraphFile { tempFile =>
        val r = app[Database].tryTransaction(implicit graph => app[AttachmentSrv].create("test2.txt", "text/plain", JFiles.readAllBytes(tempFile)))

        r must beSuccessfulTry.which { a =>
          a.name shouldEqual "test2.txt"
          a.contentType shouldEqual "text/plain"
          a.size shouldEqual JFiles.size(tempFile.path)
          a.hashes must containAllOf(app[AttachmentSrv].hashers.fromPath(tempFile.path))
        }
      }
    }

    "get an attachment" in testApp { app =>
      val allAttachments = app[Database].roTransaction(implicit graph => app[AttachmentSrv].initSteps.toList)
      allAttachments must not(beEmpty)

      app[Database].roTransaction { implicit graph =>
        app[AttachmentSrv].get(allAttachments.head.attachmentId).exists() must beTrue
      }
    }
  }
}

object WithFakeScalligraphFile {

  def apply[A](body: Files.TemporaryFile => A): A = {
    val tempFile = File.createTempFile("thehive-", "-test")
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
