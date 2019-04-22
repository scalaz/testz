resolvers += Resolver.sonatypeRepo("releases")

addSbtPlugin("io.get-coursier"    % "sbt-coursier"             % "1.0.3" )
addSbtPlugin("de.heikoseeberger"  % "sbt-header"               % "5.2.0" )
addSbtPlugin("pl.project13.scala" % "sbt-jmh"                  % "0.3.6" )
addSbtPlugin("com.47deg"          % "sbt-microsites"           % "0.7.16")
addSbtPlugin("org.scoverage"      % "sbt-scoverage"            % "1.5.1" )
addSbtPlugin("org.xerial.sbt"     % "sbt-sonatype"             % "2.5"   )
addSbtPlugin("com.jsuereth"       % "sbt-pgp"                  % "1.1.2" )
addSbtPlugin("com.typesafe.sbt"   % "sbt-site"                 % "1.3.2" )
addSbtPlugin("com.eed3si9n"       % "sbt-unidoc"               % "0.4.2" )
addSbtPlugin("org.wartremover"    % "sbt-wartremover"          % "2.4.1" )
addSbtPlugin("org.tpolecat"       % "tut-plugin"               % "0.6.11")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "0.6.27")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.0" )
addSbtPlugin("com.dwijnand"       % "sbt-travisci"             % "1.2.0" )
