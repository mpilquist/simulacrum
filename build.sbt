import sbtrelease._
import com.typesafe.tools.mima.core._
import sbtcrossproject.{crossProject, CrossType}

def ifAtLeast(scalaBinaryVersion: String, atLeastVersion: String)(options: String*): Seq[String] = {
  case class ScalaBinaryVersion(major: Int, minor: Int) extends Ordered[ScalaBinaryVersion] {
    def compare(that: ScalaBinaryVersion) = Ordering[(Int, Int)].compare((this.major, this.minor), (that.major, that.minor))
  }
  val Pattern = """(\d+)\.(\d+).*""".r
  def getScalaBinaryVersion(v: String) = v match { case Pattern(major, minor) => ScalaBinaryVersion(major.toInt, minor.toInt) }
  if (getScalaBinaryVersion(scalaBinaryVersion) >= getScalaBinaryVersion(atLeastVersion)) options
  else Seq.empty
}

lazy val commonSettings = Seq(
  organization := "com.github.mpilquist",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-language:higherKinds",
    "-language:implicitConversions"
  ) ++ ifAtLeast(scalaBinaryVersion.value, "2.11.0")(
    "-Ywarn-unused-import"
  ),
  scalacOptions in (Compile, doc) ~= { _ filterNot { o => o == "-Ywarn-unused-import" || o == "-Xfatal-warnings" } },
  scalacOptions in (Compile, console) ~= { _ filterNot { o => o == "-Ywarn-unused-import" || o == "-Xfatal-warnings" } },
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value,
  scalaVersion := "2.11.12",
  crossScalaVersions := Seq("2.10.7", "2.11.12", "2.12.4", "2.13.0-M4"),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  ),
  libraryDependencies := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v >= 13 =>
        libraryDependencies.value.filterNot{ d =>
          d.organization == "org.wartremover" && d.name == "wartremover"
        }
      case _ =>
        libraryDependencies.value
    }
  },
  libraryDependencies ++= {
    if (scalaVersion.value == "2.13.0-M4") {
      // TODO https://github.com/scalatest/scalatest/issues/1367
      Nil
    } else {
      Seq("org.scalatest" %%% "scalatest" % "3.0.5-M1" % "test")
    }
  },
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v <= 12 =>
        Seq(
          compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
        )
      case _ =>
        // if scala 2.13.0-M4 or later, macro annotations merged into scala-reflect
        // https://github.com/scala/scala/pull/6606
        Nil
    }
  },
  licenses += ("Three-clause BSD-style", url("https://github.com/mpilquist/simulacrum/blob/master/LICENSE")),
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { x => false },
  pomExtra := (
    <url>http://github.com/mpilquist/simulacrum</url>
    <scm>
      <url>git@github.com:mpilquist/simulacrum.git</url>
      <connection>scm:git:git@github.com:mpilquist/simulacrum.git</connection>
    </scm>
    <developers>
      <developer>
        <id>mpilquist</id>
        <name>Michael Pilquist</name>
        <url>http://github.com/mpilquist</url>
      </developer>
    </developers>
  ),
  pomPostProcess := { (node) =>
    import scala.xml._
    import scala.xml.transform._
    def stripIf(f: Node => Boolean) = new RewriteRule {
      override def transform(n: Node) =
        if (f(n)) NodeSeq.Empty else n
    }
    val stripTestScope = stripIf { n => n.label == "dependency" && (n \ "scope").text == "test" }
    new RuleTransformer(stripTestScope).transform(node)(0)
  },
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  useGpg := true,
  useGpgAgent := true,
  wartremoverErrors in (Test, compile) ++= Seq(
    Wart.ExplicitImplicitTypes,
    Wart.ImplicitConversion)
) ++ Seq(Compile, Test).map{ scope =>
  scalacOptions in (scope, compile) := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v >= 13 =>
        (scalacOptions in (scope, compile)).value.filterNot(_.contains("wartremover"))
      case _ =>
        (scalacOptions in (scope, compile)).value
    }
  }
}

lazy val root = project.in(file("."))
  .settings(commonSettings: _*)
  .settings(noPublishSettings: _*)
  .aggregate(coreJVM, examplesJVM, coreJS, examplesJS)

def previousVersion(currentVersion: String): Option[String] = {
  val Version = """(\d+)\.(\d+)\.(\d+).*""".r
  val Version(x, y, z) = currentVersion
  if (z == "0") None
  else Some(s"$x.$y.${z.toInt - 1}")
}

lazy val core = crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "simulacrum",
    libraryDependencies += "org.typelevel" %% "macro-compat" % "1.1.1",
    scalacOptions in (Test) += "-Yno-imports"
  )
  .settings(scalaMacroDependencies:_*)
  .jvmSettings(
    libraryDependencies += "org.ensime" %% "pcplod" % "1.2.1" % "test",
    // WORKAROUND https://github.com/ensime/pcplod/issues/12
    fork in Test := true,
    javaOptions in Test ++= Seq(
      s"""-Dpcplod.settings=${(scalacOptions in Test).value.filterNot(_.contains(",")).mkString(",")}""",
      s"""-Dpcplod.classpath=${(fullClasspath in Test).value.map(_.data).mkString(",")}"""
    )
  )
  .jsSettings(
    excludeFilter in (Test, unmanagedSources) := "jvm.scala"
  )
  .jvmSettings(
    mimaPreviousArtifacts := previousVersion(version.value).map { pv =>
      organization.value % ("simulacrum" + "_" + scalaBinaryVersion.value) % pv
    }.toSet
  )

lazy val coreJVM = core.jvm
lazy val coreJS = core.js

lazy val examples = crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure)
  .dependsOn(core % "provided")
  .settings(commonSettings: _*)
  .settings(moduleName := "simulacrum-examples")
  .settings(noPublishSettings: _*)

lazy val examplesJVM = examples.jvm
lazy val examplesJS = examples.js

// Base Build Settings
lazy val scalaMacroDependencies: Seq[Setting[_]] = Seq(
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
  ),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      // if scala 2.11+ is used, quasiquotes are merged into scala-reflect
      case Some((2, scalaMajor)) if scalaMajor >= 11 => Seq()
      // in Scala 2.10, quasiquotes are provided by macro paradise
      case Some((2, 10)) =>
        Seq(
          compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
              "org.scalamacros" %% "quasiquotes" % "2.1.0" cross CrossVersion.binary
        )
    }
  }
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

