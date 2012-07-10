Description
-----------

This Maven plugin verifies that the previously deployed snapshot artifact
actually differs from the to-be-deployed artifact. If they are identical, the
plugin will ask the [deploy plugin]
(http://maven.apache.org/plugins/maven-deploy-plugin/) to skip the upload.

Introduction
------------

Maven has a lot of strengths. But it also has weaknesses, one of the more
visible ones being that it downloads three quarters of the internet every
morning.

A large part of the reason is that many artifacts are actually snapshot
artifacts built & deployed by continuous integration systems which do not care
whether the artifacts are different from before or not. They simply deploy the
files.

However, it is a complete waste of time to upload such unchanged artifacts:
there are typically more downloaders than uploaders and Maven has no chance to
avoid these unnecessary downloads: the snapshot version is indeed different.

This plugin tries to help that situation by preventing such unnecessary uploads.

How does it work
----------------

It binds to the _verify_ phase, to make sure that the artifact has been packaged
correctly. In case the current version is not a snapshot version, it gracefully
exits. The same is true in offline mode.

Otherwise it tries to download the snapshot artifact (if there is one).  To
avoid interfering with the _install_ nor the _deploy_ phase, it downloads the
artifact to an alternative local repository in _target/lazydeploy/_.

If a snapshot artifact was successfully downloaded, the plugin now compares it
to the local artifact. If they are identical, the project property
_maven.deploy.skip_ is set to true, otherwise the plugin just exits normally.

When it is the [deploy plugin]
(http://maven.apache.org/plugins/maven-deploy-plugin/)'s turn to run, it
will pick up that property -- if set to true -- and skip uploading the
artifact.

Usage
-----

Just add this to your pom.xml:

<pre>
  &lt;build&gt;
    &lt;plugins&gt;
      &lt;plugin&gt;
        &lt;groupId&gt;org.codehaus.mojo&lt;/groupId&gt;
        &lt;artifactId&gt;maven-lazydeploy-plugin&lt;/artifactId&gt;
        &lt;version&gt;1.0-SNAPSHOT&lt;/version&gt;
        &lt;executions&gt;
          &lt;execution&gt;
            &lt;id&gt;lazy-deploy&lt;/id&gt;
            &lt;phase&gt;verify&lt;/phase&gt;
            &lt;goals&gt;
              &lt;goal&gt;lazy-deploy&lt;/goal&gt;
            &lt;/goals&gt;
          &lt;/execution&gt;
        &lt;/executions&gt;
      &lt;/plugin&gt;
    &lt;/plugins&gt;
  &lt;/build&gt;
</pre>

Shortcomings
------------

It does not lock. That is, if you try to deploy a snapshot version and the
plugin says it is unchanged while your friend also uploads a snapshot version
that is changed, it is a gamble who wins. If -- for network latency reasons or
whatever -- one person's upload happens between the download of the snapshot
version and the skipped upload, Maven will say everything is fine, but the other
person's upload will have changed the deployed snapshot artifact.

Another shortcoming is that if you were used to getting a coffee and chatting
with your colleagues in the morning while Maven is burning the LAN cables, you
might not like what this plugin does.

Future Plans
------------

Add a parameter _deep-inspection_ which will trigger the plugin to look inside
_.jar_ files and ignore the timestamps of the _.jar_ file entries (as a _.jar_
file is really a _.zip_ file, every contained file has a timestamp; this means
that building a _.jar_ file at different times invariably results in different
files, even if the contents are the same). Since we are dealing with Maven, we
should also exclude the timestamps of the _.properties_ files and some entries
in the manifest which also depend on the build environment (and do not provide
functional changes).

Also, handle attached artifacts, for the [NAR plugin]
(https://github.com/scijava/maven-nar-plugin).
