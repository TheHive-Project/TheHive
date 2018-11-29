import Common.{stableVersion, betaVersion}

version in Rpm := {
  version.value match {
    case stableVersion(v1, v2) => v1
    case betaVersion(v1, v2) => v1
    case _ => sys.error("Invalid version: " + version.value)
  }
}
rpmRelease := {
  version.value match {
    case stableVersion(_, v2) => v2
    case betaVersion(v1, v2) => "0.1RC" + v2
    case _ => sys.error("Invalid version: " + version.value)
  }
}
rpmVendor := organizationName.value
rpmUrl := organizationHomepage.value.map(_.toString)
rpmLicense := Some("AGPL")
rpmRequirements += "java-1.8.0-openjdk-headless"

maintainerScripts in Rpm := maintainerScriptsFromDirectory(
  baseDirectory.value / "package" / "rpm",
  Seq(RpmConstants.Pre, RpmConstants.Preun, RpmConstants.Postun)
)

linuxPackageSymlinks in Rpm := Nil
rpmPrefix := Some(defaultLinuxInstallLocation.value)
linuxEtcDefaultTemplate in Rpm := (baseDirectory.value / "package" / "etc_default_thehive").asURL

linuxPackageMappings in Rpm := configWithNoReplace((linuxPackageMappings in Rpm).value)

packageBin in Rpm := {
  import scala.sys.process._
  val rpmFile = (packageBin in Rpm).value
  Process("rpm" ::
    "--define" :: "_gpg_name TheHive Project" ::
    "--define" :: "_signature gpg" ::
    "--define" :: "__gpg_check_password_cmd /bin/true" ::
    "--define" :: "__gpg_sign_cmd %{__gpg} gpg --batch --no-verbose --no-armor --use-agent --no-secmem-warning -u \"%{_gpg_name}\" -sbo %{__signature_filename} %{__plaintext_filename}" ::
    "--addsign" :: rpmFile.toString ::
    Nil).!!
  rpmFile
}
