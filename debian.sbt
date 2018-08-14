import Common._

linuxPackageMappings in Debian += packageMapping(file("LICENSE") -> "/usr/share/doc/thehive/copyright").withPerms("644")
version in Debian := getVersion(version.value) + '-' + getRelease(version.value)
debianPackageRecommends := Seq("elasticsearch")
debianPackageDependencies += "openjdk-8-jre-headless"
maintainerScripts in Debian := maintainerScriptsFromDirectory(
  baseDirectory.value / "package" / "debian",
  Seq(DebianConstants.Postinst, DebianConstants.Prerm, DebianConstants.Postrm)
)
linuxEtcDefaultTemplate in Debian := (baseDirectory.value / "package" / "etc_default_thehive").asURL
linuxMakeStartScript in Debian := None