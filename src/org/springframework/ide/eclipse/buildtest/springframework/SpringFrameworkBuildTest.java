package org.springframework.ide.eclipse.buildtest.springframework;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.ide.eclipse.boot.wizard.importing.GeneralProjectStrategy.GeneralProjectImport;
import org.springsource.ide.eclipse.commons.core.ZipFileUtil;
import org.springsource.ide.eclipse.commons.core.ZipFileUtil.PermissionSetter;
import org.springsource.ide.eclipse.commons.frameworks.test.util.ExternalCommand;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

public class SpringFrameworkBuildTest {
	
	private static final URL zipUrl(String owner, String projectName, String branch) throws MalformedURLException {
		return new URL("https://github.com/"+owner+"/"+projectName+"/archive/"+branch+".zip");
	}
	
	@Test
	public void buildItWithEclipse() throws Exception {
//		String owner = "kdvolder";
//		String branch = "master";
//		String projectName = "simple-gradle-multi";
//		ExternalCommand prepare = new ExternalCommand("./gradlew", "cleanEclipse", "eclipse");

		String owner = "spring-projects";
		String branch = "master";
		String projectName = "spring-framework";
		ExternalCommand prepare = new ExternalCommand("./gradlew", 
				"--no-daemon",  "cleanEclipse", ":spring-oxm:compileTestJava", "eclipse", "-x", ":eclipse");

		File workDir = createTempDir();
		StsTestUtil.setAutoBuilding(false);
		
		ZipFileUtil.unzip(
				zipUrl(owner, projectName, branch), 
				workDir, 
				null, 
				PermissionSetter.executableExtensions("gradlew", ".sh"),
				new NullProgressMonitor());
		
		File rootProjectDir = new File(workDir, projectName+"-"+branch);
		
		prepare.exec(rootProjectDir);

		List<File> importableProjects = findProjects(rootProjectDir);
		System.out.println("Found "+importableProjects.size()+" projects to import");
		List<IProject> projects = new ArrayList<>();
		for (File projectDir : importableProjects) {
			System.out.println("Importing '"+projectDir.getName()+"' ...");
			projects.add(importExistingProject(projectDir));
			System.out.println("Importing '"+projectDir.getName()+"' DONE");
		}

		ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, new SysOutProgressMonitor());
		System.out.println("WORKSPACE BUILD complete");
		for (IProject p : projects) {
			assertNoErrors(p);
			System.out.println("Project '"+p.getName()+"' has no errors!");
		}
	}

	public static void assertNoErrors(IProject project) throws CoreException {
		IMarker[] problems = project.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
		StringBuilder errors = new StringBuilder();
		int errorCount = 0;
		for (IMarker problem : problems) {
			if (problem.getAttribute(IMarker.SEVERITY, 0) >= IMarker.SEVERITY_ERROR) {
				errors.append(StsTestUtil.markerMessage(problem)+"\n");
				errorCount++;
				if (errorCount>=10) { //don't include hundreds of errors. 10 is reasonable
					break;
				}
			}
		}
		if (errorCount>0) {
			Assert.fail("Expecting no problems but found: " + errors.toString());
		}
	}
	
	
	private List<File> findProjects(File dir) {
		return findProject(dir, new ArrayList<>());
	}

	private List<File> findProject(File dir, ArrayList<File> found) {
		if (dir.isDirectory()) {
			File descriptor = new File(dir, ".project");
			if (descriptor.exists()) {
				found.add(dir);
			}
			for (File file : dir.listFiles()) {
				findProject(file, found);
			}
		}
		return found;
	}

	private File createTempDir() throws IOException {
		File dir = new File("workdir");
		System.out.println(dir.getAbsolutePath());
		FileUtils.deleteQuietly(dir);
		dir.mkdirs();
		return dir;
	}
	
	/**
	 * Import project assumed to have eclipse metadata already.
	 * The contents of the project will be linked, not copied.
	 */
	IProject importExistingProject(File projectDir) throws Exception {
		//1
		IWorkspace ws = ResourcesPlugin.getWorkspace();
		IPath projectDescriptionFile = Path.fromOSString(projectDir.toString()).append(".project");
		IProjectDescription projectDescription = ws.loadProjectDescription(projectDescriptionFile);
		String projectName = projectDescription.getName();
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

		//2
		IProject project = ws.getRoot().getProject(projectName);
		project.create(projectDescription, new NullProgressMonitor());

		//3
		project.open(new NullProgressMonitor());
		return project;
	}


}
