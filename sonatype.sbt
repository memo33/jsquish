// For publishing to Maven Central, remove `-SNAPSHOT` from version, then
//
//   > +publishSigned
//
// to create staging bundle at target/sonatype-staging/(version). Then
//
//   > sonatypeBundleRelease
//
// to upload bundle to Sonatype.
//
// See https://github.com/xerial/sbt-sonatype for details.

publishMavenStyle := true

import xerial.sbt.Sonatype._
sonatypeProjectHosting := Some(GitHubHosting("memo33", "jsquish", "memo33@users.noreply.github.com"))
