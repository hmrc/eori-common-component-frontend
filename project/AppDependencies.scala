import sbt.*

object AppDependencies {
  import play.core.PlayVersion

  val mongoDbVersion   = "2.12.0"
  val bootstrapVersion = "10.6.0"

  val compileDependencies: Seq[ModuleID] = Seq(
    "org.typelevel"     %% "cats-core"                             % "2.13.0",
    "uk.gov.hmrc"       %% "play-conditional-form-mapping-play-30" % "3.4.0",
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-30"            % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"                    % mongoDbVersion,
    "uk.gov.hmrc"       %% "play-frontend-hmrc-play-30"            % "12.31.0",
    "uk.gov.hmrc"       %% "internal-auth-client-play-30"          % "4.3.0"
  )

  val testDependencies: Seq[ModuleID] = Seq(
    "org.scalatest"          %% "scalatest"               % "3.2.19"            % "test,it",
    "org.playframework"      %% "play-test"               % PlayVersion.current % "test,it",
    "org.scalatestplus.play" %% "scalatestplus-play"      % "7.0.2"             % "test,it",
    "org.scalacheck"         %% "scalacheck"              % "1.19.0"            % "test,it",
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootstrapVersion    % "test,it",
    "org.scalatestplus"      %% "scalacheck-1-15"         % "3.2.11.0"          % "test,it",
    "org.jsoup"               % "jsoup"                   % "1.22.1"            % "test,it",
    "us.codecraft"            % "xsoup"                   % "0.3.7"             % "test,it",
    "org.mockito"             % "mockito-core"            % "5.21.0"            % "test,it",
    "org.scalatestplus"      %% "mockito-4-6"             % "3.2.15.0"          % "test, it",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30" % mongoDbVersion      % "test, it"
  )

  def apply(): Seq[ModuleID] = compileDependencies ++ testDependencies
}
