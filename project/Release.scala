import sbt._
import sbt.Keys.baseDirectory
import sbt.{ Project, Extracted, State, IO, File, StateOps }
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations.readVersion
import sbtrelease.{ Vcs, Versions }
import play.api.libs.json._

object Release {
  val releaseVersionUIFile = settingKey[File]("The json package file to write the version to")
  val changelogFile = settingKey[File]("Changelog file")

  lazy val settings = Seq(
    releaseVersionUIFile := baseDirectory.value / "ui" / "package.json",
    changelogFile := baseDirectory.value / "CHANGELOG.md")

  val releaseNumberExtractor = "release/(.*)".r

  def getReleaseNumber(branchName: String): String =
    branchName match {
      case releaseNumberExtractor(version) ⇒ version
      case _                               ⇒ sys.error(s"The current branch ($branchName) is not a release branch (must starts with release/)")
    }

  def vcs(st: State): Vcs = {
    Project.extract(st).get(releaseVcs).getOrElse(sys.error("Aborting release. Working directory is not a repository of a recognized VCS."))
  }

  def checkUncommittedChanges: ReleaseStep = { st: State =>
    vcs(st)
      .cmd("fetch") ! st.log
    val ret = vcs(st)
      .cmd("diff-index", "--quiet", "HEAD", "--") ! st.log
    if (ret > 0) sys.error("Aborting release. Your working directory is not clean.")
    st
  }

  lazy val releaseMerge: ReleaseStep = { st: State ⇒
    val version = st.get(ReleaseKeys.versions).getOrElse(sys.error("Version not set ?!"))._1
    val ret = vcs(st).cmd("flow", "release", "finish", version, "-m", s"Release $version") ! st.log
    if (ret > 0) sys.error("Release finish failed")
    st
  }

  lazy val checkoutMaster: ReleaseStep = { st: State =>
    vcs(st).cmd("checkout", "master") ! st.log
    st
  }

  lazy val checkoutDevelop: ReleaseStep = { st: State =>
    vcs(st).cmd("checkout", "develop") ! st.log
    st
  }

  def currentBranch(st: State): String = {
    vcs(st)
      .cmd("rev-parse", "--abbrev-ref", "HEAD").!!.trim
  }

  lazy val setReleaseUIVersion: ReleaseStep = setUIVersion(_._1)
  lazy val setNextUIVersion: ReleaseStep = setUIVersion(_._2)

  def setUIVersion(selectVersion: Versions => String): ReleaseStep = { st: State ⇒
    val vs = st.get(ReleaseKeys.versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
    val version = selectVersion(vs)
    //val version = st.get(ReleaseKeys.versions).getOrElse(sys.error("Version not set ?!"))._1
    val packageFile = new File("ui/package.json")
    val pkgJson = Json.parse(IO.read(packageFile))

    pkgJson.transform(
      (__ \ 'version).json.update(
        __.read[JsString].map(_ => JsString(version)))) match {
        case JsSuccess(newPkgJson, _) => IO.write(packageFile, Json.prettyPrint(newPkgJson))
        case JsError(error)           => sys.error(s"Invalid package file format: $error")
      }
    st
  }

  lazy val getVersionFromBranch: ReleaseStep = { st: State ⇒
    val extracted = Project.extract(st)
    val useDefs = st.get(ReleaseKeys.useDefaults).getOrElse(false)

    val currentV = getReleaseNumber(currentBranch(st))
    val releaseFunc = extracted.get(releaseVersion)
    val suggestedReleaseV = releaseFunc(currentV)

    st.log.info(s"Release version : $currentV")
    val nextFunc = extracted.get(releaseNextVersion)
    val suggestedNextV = nextFunc(currentV)
    //flatten the Option[Option[String]] as the get returns an Option, and the value inside is an Option
    val nextV = readVersion(suggestedNextV, "Next version [%s] : ", useDefs, st.get(ReleaseKeys.commandLineNextVersion).flatten)
    st.put(ReleaseKeys.versions, (currentV, nextV))
  }

  lazy val generateChangelog: ReleaseStep = { st: State =>
    val changeLogFile = Project.extract(st).get(changelogFile)
    st.log.info("Generating changelog in ")
    val properties = new java.util.Properties
    val credentialsFile = new File("~/.github/credentials")
    IO.load(properties, credentialsFile)
    val token = Option(properties.getProperty("token")).fold("")(t => s"-t $t")
    s"github_changelog_generator $token" ! st.log
    st
  }

  lazy val commitChanges: ReleaseStep = { st: State =>
    val base = vcs(st).baseDir
    Seq(releaseVersionFile, releaseVersionUIFile, changelogFile)
      .foreach { f =>
        val file = Project.extract(st).get(f)
        val relativePath = IO.relativize(base, file).getOrElse(sys.error(s"Version file [$file] is outside of this VCS repository with base directory [$base]!"))
        vcs(st).add(relativePath) !! st.log
      }
    val status = (vcs(st).status.!!).trim

    val newState = if (status.nonEmpty) {
      val (state, msg) = Project.extract(st).runTask(releaseCommitMessage, st)
      vcs(state).commit(msg) ! st.log
      state
    } else {
      // nothing to commit. this happens if the version.sbt file hasn't changed.
      st
    }
    newState
  }

}