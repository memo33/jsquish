JSquish
=======

Based on JSquish library for DXT image compression by S. Brown, with small
modifications in order to allow for concurrent calls to compression methods.
Moreover, the ClusterFit algorithm was tweaked a little bit, which makes it
about twice as fast.

Usage
-----

Add this to `build.sbt`:

    resolvers += "memo33-bintray" at "http://dl.bintray.com/memo33/maven"

    libraryDependencies += "com.github.memo33" % "jsquish" % "2.0.1"
