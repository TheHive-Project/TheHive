import java.io.File

import scala.util.matching.Regex

object Common {
  def remapPath(oldPath: String, newPath: String, prefixes: String*): ((File, String)) => (File, String) = {
    case (file, path) =>
      file -> prefixes
        .find(p => path.startsWith(s"$p/$oldPath"))
        .fold(path)(p => s"$p/$newPath" + path.drop(p.length + 1 + oldPath.length))
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
}
