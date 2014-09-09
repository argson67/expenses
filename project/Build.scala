import sbt._
import Keys._

import com.earldouglas.xsbtwebplugin.WebPlugin.webSettings

//import Tests._

object BuildSettings {
  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization  := "bsoviet",
    version       := "0.1",
    scalaVersion  := "2.10.3",

    scalacOptions ++= Seq("-language:postfixOps", "-unchecked", "-deprecation", "-feature", "-encoding", "utf8"),

    libraryDependencies ++= List(
      // Database
      "com.typesafe.slick" % "slick_2.10" % "2.0.0", // Database
      "org.slf4j" % "slf4j-simple" % "1.6.4", // Logger
      "joda-time" % "joda-time" % "2.3", // Datetime
      "org.joda" % "joda-convert" % "1.5",
      "com.github.tototoshi" % "slick-joda-mapper_2.10" % "1.0.1",
      "org.postgresql" % "postgresql" % "9.3-1100-jdbc4", // Postgres JDBC driver

      "org.json4s" %%  "json4s-native" % "3.2.4",
      "org.scalaz" %%  "scalaz-core"   % "7.1.0-M7"
    ),
    resolvers += Resolver.sonatypeRepo("releases"),
    //resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
    addCompilerPlugin("org.scalamacros" % "paradise_2.10.3" % "2.0.0")
  )
}

object MoneyBuild extends Build {
  import BuildSettings._

  lazy val money: Project = Project(
    "money",
    file("."),
    settings =  buildSettings ++ Seq(
      run <<= run in Compile in core
    )
  ) aggregate(core, macros)

  lazy val macros: Project = Project(
    "macros",
    file("macros"),
    settings = buildSettings ++ Seq(
      libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
      libraryDependencies += "org.scalamacros" % "quasiquotes_2.10" % "2.0.0"
    )
  )

  lazy val core: Project = {
    val akkaV = "2.3.0"
    val sprayV = "1.3.1"

    Project(
      "core",
      file("core"),
      settings = buildSettings ++ webSettings ++ Seq(
        libraryDependencies ++= List(
          // Spray
          "io.spray"            %   "spray-servlet" % sprayV,
          "io.spray"            %   "spray-routing" % sprayV,
          "org.eclipse.jetty"   %   "jetty-webapp"  % "9.1.0.v20131115" % "container",
          "org.eclipse.jetty"   %   "jetty-plus"    % "9.1.0.v20131115" % "container",
          "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container"  artifacts Artifact("javax.servlet", "jar", "jar"),
          "io.spray"            %   "spray-testkit" % sprayV % "test",
          "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
          "com.typesafe.akka"   %%  "akka-testkit"  % akkaV % "test",
          "org.specs2"          %%  "specs2"        % "2.2.3" % "test",
          "com.github.t3hnar"   %% "scala-bcrypt"   % "2.4"
        ))
    ) dependsOn(macros)
  }
}
