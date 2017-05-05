import play.api.libs.json._
import sbt.Keys.{baseDirectory, sLog, version}
import sbt.{File, IO, _}

object Release {
  val releaseVersionUIFile = settingKey[File]("The json package file to write the version to")
  val changelogFile = settingKey[File]("Changelog file")
  val updateUIVersion = taskKey[Unit]("Put sbt package version (from version.sbt) in NPM package (package.json)")
  val generateChangelog = taskKey[Unit]("Generate changelog file")

  lazy val settings = Seq(
    releaseVersionUIFile := baseDirectory.value / "ui" / "package.json",
    changelogFile := baseDirectory.value / "CHANGELOG.md",
    updateUIVersion := {
      val packageFile = releaseVersionUIFile.value
      val pkgJson = Json.parse(IO.read(packageFile))

      pkgJson.transform(
        (__ \ 'version).json.update(
          __.read[JsString].map(_ => JsString((version in ThisBuild).value)))) match {
        case JsSuccess(newPkgJson, _) => IO.write(packageFile, Json.prettyPrint(newPkgJson))
        case JsError(error) => sys.error(s"Invalid package file format: $error")
      }
    },
    generateChangelog := {
      sLog.value.info("Generating changelog in ")
      val properties = new java.util.Properties
      val credentialsFile = new File("~/.github/credentials")
      IO.load(properties, credentialsFile)
      val token = Option(properties.getProperty("token")).fold("")(t => s"-t $t")
      s"github_changelog_generator $token" ! sLog.value
      ()
    }
  )
}