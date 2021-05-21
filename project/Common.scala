import java.io.File
import scala.util.matching.Regex
import scala.sys.process._

object Common {
  def remapPath(oldPath: String, newPath: String, prefixes: String*): ((File, String)) => (File, String) = {
    case (file, path) =>
      file -> prefixes
        .find(p => path.startsWith(s"$p/$oldPath"))
        .fold(path)(p => s"$p/$newPath" + path.drop(p.length + 1 + oldPath.length))
  }
  def rebasePath(oldPath: String, newPath: String): ((File, String)) => (File, String) = {
    case (file, path) if path.startsWith(oldPath) =>
      file -> s"$newPath${path.drop(oldPath.length)}"
    case other => other
  }

  val stableVersion: Regex = "(\\d+\\.\\d+\\.\\d+)-(\\d+)".r
  val betaVersion: Regex   = "(\\d+\\.\\d+\\.\\d+)-[Rr][Cc](\\d+)-(\\d+)".r
  object snapshotVersion {
    def unapply(version: String): Option[String] =
      if (version.endsWith("-SNAPSHOT")) Some(version.dropRight(9))
      else None
  }
  def versionUsage(version: String): Nothing =
    sys.error(
      s"Invalid version: $version\n" +
        "The accepted formats for version are:\n" +
        " - 1.2.3-4\n" +
        " - 1.2.3-RC4-5\n" +
        " - 1.2.3-4-SNAPSHOT\n" +
        " - 1.2.3-RC4-5-SNAPSHOT"
    )

  def debianVersion(version: String): String =
    version match {
      case stableVersion(_, _)                      => version
      case betaVersion(v1, v2, v3)                  => v1 + "-0." + v3 + "RC" + v2
      case snapshotVersion(stableVersion(v1, v2))   => v1 + "-" + v2 + "-SNAPSHOT"
      case snapshotVersion(betaVersion(v1, v2, v3)) => v1 + "-0." + v3 + "RC" + v2 + "-SNAPSHOT"
      case _                                        => versionUsage(version)
    }

  def rpmVersion(version: String): String =
    version match {
      case stableVersion(v1, _)                   => v1
      case betaVersion(v1, _, _)                  => v1
      case snapshotVersion(stableVersion(v1, _))  => v1
      case snapshotVersion(betaVersion(v1, _, _)) => v1
      case _                                      => versionUsage(version)
    }

  def rpmReleaseVersion(version: String): String =
    version match {
      case stableVersion(_, v2)                    => v2
      case betaVersion(_, v2, v3)                  => "0." + v3 + "RC" + v2
      case snapshotVersion(stableVersion(_, v2))   => v2 + "-SNAPSHOT"
      case snapshotVersion(betaVersion(_, v2, v3)) => "0." + v3 + "RC" + v2 + "-SNAPSHOT"
      case _                                       => versionUsage(version)
    }

  def rpmSignFile(rpmFile: File): File = {
    Process(
      "rpm" ::
        "--define" :: "_gpg_name TheHive Project" ::
        "--define" :: "_signature gpg" ::
        "--define" :: "__gpg_check_password_cmd /bin/true" ::
        "--define" :: "__gpg_sign_cmd %{__gpg} gpg --batch --no-verbose --no-armor --use-agent --no-secmem-warning -u \"%{_gpg_name}\" -sbo %{__signature_filename} %{__plaintext_filename}" ::
        "--addsign" :: rpmFile.toString ::
        Nil
    ).!!
    rpmFile
  }
}
