package org.springframework.ide.eclipse.buildtest.springframework;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.junit.Test;
import org.springframework.ide.eclipse.boot.wizard.importing.GeneralProjectStrategy.GeneralProjectImport;
import org.springsource.ide.eclipse.commons.core.ZipFileUtil;
import org.springsource.ide.eclipse.commons.frameworks.test.util.ExternalCommand;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

public class SpringFrameworkBuildTest {
	
	private static String MVN = "/home/kdvolder/Applications/apache-maven-3.3.9/bin/mvn";

	private static final String zipUrl(String owner, String projectName, String branch) {
		return "https://github.com/"+owner+"/"+projectName+"/archive/"+branch+".zip";
	}
	
	@Test
	public void helloWorld() throws Exception {
		String owner = "kdvolder";
		String branch = "master";
		String projectName = "hello-world";
		
		String testProjectZip = zipUrl(owner, projectName, branch);
		
		File workDir = createTempDir();
		File zipFile = new File(workDir, "project.zip");
		download(testProjectZip, zipFile);
		StsTestUtil.setAutoBuilding(false);
		
		ZipFileUtil.unzip(zipFile, workDir, new NullProgressMonitor());
		
		File projectDir = new File(workDir, projectName+"-"+branch);
		ExternalCommand mvnEclipse = new ExternalCommand(MVN, "eclipse:eclipse");
//		ExternalCommand mvnEclipse = new ExternalCommand("pwd");
		mvnEclipse.exec(projectDir);

		IProject project = importExistingProject(projectName, projectDir, new NullProgressMonitor());
		StsTestUtil.assertNoErrors(project);
	}

	private void download(String _url, File saveTo) throws Exception {
		URL url = new URL(_url);
		ReadableByteChannel rbc = Channels.newChannel(url.openStream());
		FileOutputStream fos = new FileOutputStream(saveTo);
		fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
	}

	private File createTempDir() throws IOException {
		File dir = new File("workdir");
		System.out.println(dir.getAbsolutePath());
		FileUtils.deleteQuietly(dir);
		dir.mkdirs();
		return dir;
//		return StsTestUtil.createTempDirectory();
	}
	
	/**
	 * Create a general eclipse project (no builders natures etc) with a given name and project contents
	 * from a given location. The contents of the project will be linked, not copied.
	 * @return 
	 */
	public static IProject importExistingProject(String projectName, File projectDir, IProgressMonitor mon) throws Exception {
		mon.beginTask("Import project "+projectName, 3);
		try {
			//1
			IWorkspace ws = ResourcesPlugin.getWorkspace();
			IPath projectDescriptionFile = Path.fromOSString(projectDir.toString()).append(".project");
			IProjectDescription projectDescription = ws.loadProjectDescription(projectDescriptionFile);
			Path projectLocation = new Path(projectDir.getAbsolutePath());
			if (!GeneralProjectImport.isDefaultProjectLocation(projectName, projectDir)) {
				projectDescription.setLocation(projectLocation);
			}
			//To improve error message... check validity of project location vs name
			//note: in import wizard use, this error is impossible since wizard validates this constraint.
			//Be careful that this constraint only needs to hold in a very specific case where the
			//location is nested exactly one level below the workspace location on disk.
			IPath wsLocation = ws.getRoot().getLocation();
			if (wsLocation.isPrefixOf(projectLocation) && wsLocation.segmentCount()+1==projectLocation.segmentCount()) {
				String expectedName = projectDir.getName();
				if (!expectedName.equals(projectName)) {
					throw new IllegalArgumentException("Project-name ("+projectName+") should match last segment of location ("+projectDir+")");
				}
			}
			mon.worked(1);

			//2
			IProject project = ws.getRoot().getProject(projectName);
			project.create(projectDescription, new SubProgressMonitor(mon, 1));

			//3
			project.open(new SubProgressMonitor(mon, 1));
			return project;
		} finally {
			mon.done();
		}
	}


}
