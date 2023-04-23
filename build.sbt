name := "jsquish"

organization := "io.github.memo33"

version := "2.1.0"

ThisBuild / versionScheme := Some("early-semver")

description := "DXT image compression algorithms"

licenses += ("BSD 3-Clause", url("https://opensource.org/licenses/BSD-3-Clause"))

scalaVersion := "2.13.10"

javacOptions ++= Seq(
  "--release", "8",
  "-Xdoclint:-missing",  // ignore verbose warnings about missing javadoc comments
  "-encoding", "UTF-8")

crossPaths := false

autoScalaLibrary := false

publishTo := sonatypePublishToBundle.value

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

// libraryDependencies += "org.scala-lang" % "scala-library" % scalaVersion.value % "test"

// libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.15" % "test"
