enablePlugins(JavaAppPackaging, SbtOsgi)

name := "robotServices"
organization := "chalmers"
version := "1.0"
scalaVersion := "2.11.8"

lazy val commonSettings = Seq(
  version := "1.0",

  scalaVersion := "2.11.8",

  scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8"),

  libraryDependencies ++= {
    Seq(
      "com.typesafe"       % "config"                               % "1.3.0",
      "com.typesafe.akka" %% "akka-actor"                           % "2.4.6",
      "com.codemettle.reactivemq" %% "reactivemq"                   % "0.5.4",
      "org.apache.activemq" % "activemq-client"                     % "5.13.1",
      "org.json4s" %% "json4s-native"                               % "3.3.0",
      "org.json4s" %% "json4s-jackson"                              % "3.3.0",
      "org.json4s" %% "json4s-ext"                                  % "3.3.0",
      "com.github.nscala-time" %% "nscala-time"                     % "1.8.0",
      "wabisabi" %% "wabisabi"                                      % "2.1.4"
    )
  },
    resolvers += "gphat" at "https://raw.github.com/gphat/mvn-repo/master/releases/"
)
lazy val root = project.in( file(".") ).
  aggregate(robotPathService, robotIsWaitingService, robotRoutineChangeService, robotCycleStoreService,
    robotTipDressWearService, waitChange, launcher)

lazy val core = project.settings(commonSettings: _*)

lazy val robotPathService = project
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val robotIsWaitingService = project
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val waitChange = project
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val robotRoutineChangeService = project
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val robotCycleStoreService = project
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val robotTipDressWearService = project
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val workCellCycleChangeService = project
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val launcher = project.
  dependsOn(robotPathService, robotIsWaitingService, robotRoutineChangeService, robotCycleStoreService,
    robotTipDressWearService, waitChange, workCellCycleChangeService).settings(commonSettings: _*)


osgiSettings

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomExtra :=
  <url>http://</url>
    <licenses>
      <license>
        <name>Apache-2.0</name>
        <url>http://opensource.org/licenses/Apache-2.0</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>https://github.com/HenWahl</url>
      <connection>scm:git:git@github.com:HenWahl/REPO.git</connection>
    </scm>
    <developers>
      <developer>
        <name>Daniel Nord</name>
      </developer>
      <developer>
        <name>Henrik Wahlqvist</name>
      </developer>
    </developers>