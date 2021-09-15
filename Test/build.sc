import mill._, scalalib._, scalanativelib._, scalanativelib.api._

object example extends ScalaNativeModule{
  def scalaNativeVersion = "0.4.0"
  def scalaVersion = "2.13.4"
  def releaseMode = ReleaseMode.ReleaseFast
  def nativeLTO = LTO.Thin
  def ivyDeps = Agg(
    ivy"com.lihaoyi::ujson::1.2.3",
    ivy"com.lihaoyi::mainargs::0.2.1",
    ivy"com.lihaoyi::os-lib::0.7.2"
  )
  object test extends Tests{
    def ivyDeps = Agg(ivy"com.lihaoyi::utest::0.7.7")
    def testFrameworks = Seq("utest.runner.Framework")
  }
}
