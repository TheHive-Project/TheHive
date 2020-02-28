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
  val betaVersion: Regex   = "(\\d+\\.\\d+\\.\\d+)-[Rr][Cc](\\d+)".r
  object snapshotVersion {
    def unapplySeq(version: String): Option[List[String]] =
      if (version.endsWith("-SNAPSHOT")) {
        val v = version.dropRight(9)
        stableVersion.unapplySeq(v) orElse betaVersion.unapplySeq(v)
      } else None
  }
}
