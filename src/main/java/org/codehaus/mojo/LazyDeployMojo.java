package org.codehaus.mojo;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.util.List;

/**
 * Goal which inspects the artifact (and attached artifacts) to determine whether a new snapshot needs to be deployed or not.
 *
 * @goal lazy-deploy
 * @phase verify
 * @inheritedByDefault true
 * @requiresProject true
 * 
 * @author Johannes Schindelin
 */
public class LazyDeployMojo
    extends AbstractMojo
{
	/**
	 * @parameter default-value="${project}"
	 * @required
	 */
	private MavenProject project;

	/**
	 * @parameter default-value="${project.artifact}"
	 * @required
	 * @readonly
	 */
	private Artifact artifact;

	/**
	 * @component
	 * @readonly
	 */
	private ArtifactResolver resolver;

	/**
	 * @parameter default-value="${project.attachedArtifacts}
	 * @required
	 * @readonly
	 */
	private List<Artifact> attachedArtifacts;

    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     */
    private List<ArtifactRepository> remoteRepositories;

	/**
	 * Flag whether Maven is currently in online/offline mode.
	 *
	 * @parameter default-value="${settings.offline}"
	 * @readonly
	 */
	private boolean offline;

	/**
	 * Location of the file.
	 * @parameter expression="${project.build.directory}"
	 * @required
	 */
	private File outputDirectory;

	/**
	 * The "secondary" local repository.
	 *
	 * The whole purpose of this repository is to obtain the snapshot
	 * artifact from the remote repository so that we can compare it
	 * to the current artifact.
	 */
	private ArtifactRepository alternativeRepository;

	public void execute()
		throws MojoExecutionException
	{
		if ( offline || !artifact.isSnapshot() )
		{
			return;
		}

		/*
		 * We are already in the verify phase, so we're the only ones who still want to
		 * fetch snapshot versions. Therefore we can safely override the update policy now.
		 */
		for ( ArtifactRepository repository : remoteRepositories )
		{
			ArtifactRepositoryPolicy policy = repository.getSnapshots();
			policy.setUpdatePolicy( ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS );
		}

		initAlternativeRepository();

		if ( unchanged( artifact ) )
		{
			getLog().info( "Skip deployment of unchanged artifact " + artifact );
			project.getProperties().put("maven.deploy.skip", "true");
		}
	}

	private void initAlternativeRepository()
	{
		final File lazydeploy = new File( outputDirectory, "lazydeploy" );
		if ( !lazydeploy.isDirectory() )
		{
			lazydeploy.mkdirs();
		}

		try {
			alternativeRepository = new DefaultArtifactRepository( "lazydeploy",
					lazydeploy.toURI().toURL().toString(), new DefaultRepositoryLayout() )
			{
				@Override
				public String getBasedir()
				{
					return lazydeploy.getAbsolutePath();
				}
			};
		} catch (Throwable t) {
			// no luck...
		}

	}

	private boolean unchanged( Artifact artifact )
	{
		if ( alternativeRepository == null )
		{
			return false;
		}

		// try to copy the artifact from a remote repository to our local alternative repository
		Artifact copy = ArtifactUtils.copyArtifact( artifact );
		try {
			resolver.resolve( copy, remoteRepositories, alternativeRepository );
		} catch ( ArtifactNotFoundException e ) {
			// not found; force "changed"
			return false;
		} catch ( ArtifactResolutionException e ) {
			// not found; force "changed"
			return false;
		}

		// did we obtain a copy of a remote artifact?
		File file = copy.getFile();
		if ( file == null || ! file.exists() )
		{
			return false;
		}

		return compare( file, artifact.getFile() ) == 0;
	}

	private static int compare( File file1, File file2 )
	{
		long sizeDifference = file1.length() - file2.length();

		if ( sizeDifference != 0 )
		{
			return sizeDifference < 0 ? -1 : +1;
		}

		try {
			return compare( new FileInputStream( file1 ), new FileInputStream( file2 ) );
		} catch ( FileNotFoundException e ) {
			return -1;
		}
	}

	private static int compare( InputStream in1, InputStream in2 )
	{
		byte[] buffer1 = new byte[32768], buffer2 = new byte[32768];
		int offset1, offset2, length1, length2, difference;
		offset1 = offset2 = length1 = length2 = difference = 0;
		try {
			for ( ;; )
			{
				if ( offset1 == length1 )
				{
					length1 = in1.read( buffer1 );
					if ( length1 < 0 )
					{
						break;
					}
					offset1 = 0;
				}
				if ( offset2 == length2 )
				{
					length2 = in2.read( buffer2 );
					if ( length2 < 0 )
					{
						break;
					}
					offset2 = 0;
				}
				difference = (buffer1[offset1++] & 0xff) - (buffer2[offset2++] & 0xff);
				if ( difference != 0 )
				{
					break;
				}
			}
			in1.close();
			in2.close();
			return difference;
		} catch ( IOException e ) {
			return -1;
		}
	}
}
/* vim: set sw=4 ts=4: */
