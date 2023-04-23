name := "jsquish"

organization := "io.github.memo33"

version := "2.1.0-SNAPSHOT"

licenses += ("BSD 3-Clause", url("https://opensource.org/licenses/BSD-3-Clause"))

scalaVersion := "2.11.8"

javacOptions in (Compile, compile) ++= Seq(
  "-source", "1.6",
  "-target", "1.6",
  "-encoding", "UTF-8")

crossPaths := false

autoScalaLibrary := false


//libraryDependencies += "org.scalatest" %% "scalatest" % "2.1.5" % "test"
