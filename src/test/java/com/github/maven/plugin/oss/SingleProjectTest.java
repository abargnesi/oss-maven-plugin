package com.github.maven.plugin.oss;

import static java.nio.file.Files.createTempDirectory;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.test.plugin.ProjectTool;
import org.sonatype.aether.impl.internal.SimpleLocalRepositoryManager;
import org.sonatype.aether.util.DefaultRepositorySystemSession;

import java.io.File;

public class SingleProjectTest extends AbstractMojoTestCase {

    public void testReportSimpleProject() throws Exception {
        File pom = new File(SingleProjectTest.class.getResource("/projects/single-project/pom.xml").getPath());
        assertNotNull(pom);
        assertTrue(pom.exists());

        ReportIssuesMojo mojo = createMojo(pom);
        assertNotNull(mojo);
        mojo.execute();
    }

    private ReportIssuesMojo createMojo(final File testPom) throws Exception {
        final ReportIssuesMojo mojo = (ReportIssuesMojo) lookupMojo("report-issues", testPom);
        assertNotNull(mojo);

        final LegacySupport legacySupport   = lookup(LegacySupport.class);
        final ProjectTool projectTool       = lookup(ProjectTool.class);
        final MavenProject testProject      = projectTool.readProject(testPom, tempLocalRepository);

        legacySupport.setSession(newMavenSession(testProject));

        DefaultRepositorySystemSession repoSession = (DefaultRepositorySystemSession) legacySupport.getRepositorySession();
        repoSession.setLocalRepositoryManager(new SimpleLocalRepositoryManager(tempLocalRepository));

        setVariableValueToObject(mojo,
            "project",
            testProject);
        setVariableValueToObject(mojo,
            "session",
            legacySupport.getSession());
        return mojo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tempLocalRepository = createTempDirectory("local-repo" + getClass().getSimpleName()).toFile();
        FileUtils.deleteDirectory(tempLocalRepository);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        FileUtils.deleteDirectory(tempLocalRepository);
    }

    private File tempLocalRepository;
}
