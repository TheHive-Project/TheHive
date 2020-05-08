import Common.{betaVersion, snapshotVersion, stableVersion, versionUsage}

version in Rpm := {
  version.value match {
    case stableVersion(v1, _)                   => v1
    case betaVersion(v1, _, _)                  => v1
    case snapshotVersion(stableVersion(v1, _))  => v1
    case snapshotVersion(betaVersion(v1, _, _)) => v1
    case _                                      => versionUsage(version.value)
  }
}
rpmRelease := {
  version.value match {
    case stableVersion(_, v2)                    => v2
    case betaVersion(_, v2, v3)                  => "0." + v3 + "RC" + v2
    case snapshotVersion(stableVersion(_, v2))   => v2 + "-SNAPSHOT"
    case snapshotVersion(betaVersion(_, v2, v3)) => "0." + v3 + "RC" + v2 + "-SNAPSHOT"
    case _                                       => versionUsage(version.value)
  }
}
rpmVendor := organizationName.value
rpmUrl := organizationHomepage.value.map(_.toString)
rpmLicense := Some("AGPL")
rpmRequirements += "java-1.8.0-openjdk-headless"

maintainerScripts in Rpm := maintainerScriptsFromDirectory(
  baseDirectory.value / "package" / "rpm",
  Seq(RpmConstants.Pre, RpmConstants.Post, RpmConstants.Preun, RpmConstants.Postun)
)

linuxPackageSymlinks in Rpm := Nil
rpmPrefix := Some(defaultLinuxInstallLocation.value)

linuxPackageMappings in Rpm := configWithNoReplace((linuxPackageMappings in Rpm).value)

packageBin in Rpm := {
  import scala.sys.process._
  val rpmFile = (packageBin in Rpm).value
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
