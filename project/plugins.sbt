val scalaJSVersion =
  Option(System.getenv("SCALAJS_VERSION")).getOrElse("0.6.32")
val scalaNativeVersion =
  Option(System.getenv("SCALANATIVE_VERSION")).getOrElse("0.4.0-M2")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")
addSbtPlugin("io.crashbox" % "sbt-gpg" % "0.2.1")
addSbtPlugin("org.scala-js" % "sbt-scalajs"  % scalaJSVersion)
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.6.4")
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.4.5")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.0.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % scalaNativeVersion)
