JSquish
=======

Based on JSquish library for DXT image compression by S. Brown, with small
modifications in order to allow for concurrent calls to compression methods.
Moreover, the ClusterFit algorithm was tweaked a little bit, which makes it
about twice as fast.

Usage
-----

Add this to SBT:

    resolvers += "memo33-gdrive-repo" at "https://googledrive.com/host/0B9r6o2oTyY34ZVc4SFBWMV9yb0E/repo/releases/"

    libraryDependencies += "com.github.memo33" % "jsquish" % "2.0.0"

Or download directly at "https://googledrive.com/host/0B9r6o2oTyY34ZVc4SFBWMV9yb0E/repo/releases/com/github/memo33/jsquish/".

