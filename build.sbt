import com.typesafe.sbt.packager.MappingsHelper._
import play.core.PlayVersion
import play.sbt.routes.RoutesKeys
import play.sbt.routes.RoutesKeys._
import sbt.Keys._
import sbt._
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, targetJvm}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

import scala.language.postfixOps

mappings in Universal ++= directory(baseDirectory.value / "public")
// see https://stackoverflow.com/a/37180566

name := "eori-common-component-frontend"

targetJvm := "jvm-1.8"

scalaVersion := "2.12.12"

majorVersion := 0

PlayKeys.devSettings := Seq("play.server.http.port" -> "6750")

resolvers += Resolver.bintrayRepo("hmrc", "releases")

lazy val allResolvers = resolvers ++= Seq(Resolver.jcenterRepo)

lazy val IntegrationTest = config("it") extend Test

val testConfig = Seq(IntegrationTest, Test)

lazy val microservice = (project in file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .configs(testConfig: _*)
  .settings(
    commonSettings,
    unitTestSettings,
    integrationTestSettings,
    playSettings,
    allResolvers,
    scoverageSettings,
    twirlSettings,
    TwirlKeys.templateImports += "uk.gov.hmrc.eoricommoncomponent.frontend.models._"
  )

def filterTestsOnPackageName(rootPackage: String): String => Boolean = {
  testName => testName startsWith rootPackage
}

lazy val unitTestSettings =
  inConfig(Test)(Defaults.testTasks) ++
    Seq(
      testOptions in Test := Seq(Tests.Filter(filterTestsOnPackageName("unit"))),
      testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
      fork in Test := true,
      unmanagedSourceDirectories in Test := Seq((baseDirectory in Test).value / "test"),
      addTestReportOption(Test, "test-reports")
    )

lazy val integrationTestSettings =
  inConfig(IntegrationTest)(Defaults.testTasks) ++
    Seq(
      testOptions in IntegrationTest := Seq(Tests.Filters(Seq(filterTestsOnPackageName("integration")))),
      testOptions in IntegrationTest += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
      fork in IntegrationTest := false,
      parallelExecution in IntegrationTest := false,
      addTestReportOption(IntegrationTest, "int-test-reports")
    )

lazy val commonSettings: Seq[Setting[_]] = publishingSettings ++ defaultSettings()

lazy val playSettings: Seq[Setting[_]] = Seq(
  routesImport ++= Seq("uk.gov.hmrc.eoricommoncomponent.frontend.domain._"),
  RoutesKeys.routesImport += "uk.gov.hmrc.eoricommoncomponent.frontend.models._"
)

lazy val twirlSettings: Seq[Setting[_]] = Seq(
  TwirlKeys.templateImports ++= Seq("uk.gov.hmrc.eoricommoncomponent.frontend.views.html._", "uk.gov.hmrc.eoricommoncomponent.frontend.domain._")
)

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys

  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := List("<empty>"
      , "Reverse.*"
      , "uk\\.gov\\.hmrc\\.customs\\.rosmfrontend\\.models\\.data\\..*"
      , "uk\\.gov\\.hmrc\\.customs\\.rosmfrontend\\.view.*"
      , "uk\\.gov\\.hmrc\\.customs\\.rosmfrontend\\.models.*"
      , "uk\\.gov\\.hmrc\\.customs\\.rosmfrontend\\.config.*"
      , ".*(AuthService|BuildInfo|Routes|TestOnly).*").mkString(";"),
    ScoverageKeys.coverageMinimum := 87.5,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

scalastyleConfig := baseDirectory.value / "project" / "scalastyle-config.xml"

val compileDependencies = Seq(
  "uk.gov.hmrc" %% "bootstrap-play-26" % "1.14.0",
  "uk.gov.hmrc" %% "auth-client" % "3.0.0-play-26",
  "uk.gov.hmrc" %% "http-caching-client" % "9.1.0-play-26",
  "uk.gov.hmrc" %% "play-conditional-form-mapping" % "1.3.0-play-26",
  "uk.gov.hmrc" %% "domain" % "5.9.0-play-26",
  "uk.gov.hmrc" %% "mongo-caching" % "6.15.0-play-26",
  "uk.gov.hmrc" %% "emailaddress" % "3.5.0",
  "uk.gov.hmrc" %% "logback-json-logger" % "4.8.0",
  "com.typesafe.play" %% "play-json-joda" % "2.6.10",
  "uk.gov.hmrc" %% "play-language" % "4.3.0-play-26",
  "uk.gov.hmrc" %% "play-ui" % "8.11.0-play-26"
)


val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % "3.0.5" % "test,it",
  "com.typesafe.play" %% "play-test" % PlayVersion.current % "test,it",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % "test,it",
  "com.github.tomakehurst" % "wiremock-standalone" % "2.23.2" % "test, it"
    exclude("org.apache.httpcomponents", "httpclient") exclude("org.apache.httpcomponents", "httpcore"),
  "org.scalacheck" %% "scalacheck" % "1.14.0" % "test,it",
  "org.jsoup" % "jsoup" % "1.11.3" % "test,it",
  "us.codecraft" % "xsoup" % "0.3.1" % "test,it",
  "org.mockito" % "mockito-core" % "3.0.0" % "test,it",
  "uk.gov.hmrc" %% "reactivemongo-test" % "4.21.0-play-26" % "test, it"
)

libraryDependencies ++= compileDependencies ++ testDependencies
