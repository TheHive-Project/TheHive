import Common._

version in Rpm := getVersion(version.value)
rpmRelease := getRelease(version.value)
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
  s"rpm --addsign $rpmFile".!!
  rpmFile
}
