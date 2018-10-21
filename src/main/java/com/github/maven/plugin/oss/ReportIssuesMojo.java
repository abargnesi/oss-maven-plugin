package com.github.maven.plugin.oss;

import static com.github.maven.plugin.oss.Utility.dependencyToCoordinate;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.artifact.resolve.ArtifactResult;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

@Mojo(
    name = "report-issues",
    requiresDirectInvocation = true)
public class ReportIssuesMojo extends AbstractMojo {

    private static Set<String> DEFAULT_SCOPES = new HashSet<>(asList("compile", "runtime"));

    @Parameter(
        defaultValue = "${session}",
        required = true,
        readonly = true)
    private MavenSession session;

    @Parameter(
        defaultValue = "${project}",
        readonly = true)
    private MavenProject project;

    @Component
    private ArtifactResolver artifactResolver;

    public void execute() {
        // TODO Create mapping of [FQC name] to [Artifact]
        // TODO Compute artifact counters for used imports.
        // TODO Infer issue management system and URL (check issueManagement, infer from SCM, infer from URL).
        // TODO Crawl issues using

        final Log log = getLog();

        final MavenProject project = session.getCurrentProject();

        final Set<File> sourceFiles = project.getCompileSourceRoots()
            .stream()
            .flatMap(Utility::recursivelyScanForFilesInDirectory)
            .collect(toSet());

        final Set<String> imports = sourceFiles
            .stream()
            .flatMap(file -> {
                try {
                    final CompilationUnit cUnit = JavaParser.parse(file);
                    return cUnit.getImports()
                        .stream()
                        .filter(impDecl -> !impDecl.isAsterisk() && !impDecl.isStatic())
                        .map(ImportDeclaration::getName).map(Name::asString);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException("error parsing " + file.getAbsolutePath(), e);
                }
            })
            .collect(toSet());

        imports.forEach(i -> log.info("Source import → " + i));

        project.getDependencies().stream()
            .filter(dep -> DEFAULT_SCOPES.contains(dep.getScope()))
            .forEach(dep -> {
                log.info("Scanning dependency → " + dep);
                final ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
                try {
                    final ArtifactResult result = artifactResolver.resolveArtifact(buildingRequest, dependencyToCoordinate(dep));
                    log.info("  Resolved to → " + result.getArtifact().getFile().getAbsolutePath());

                    final String pomPath = result.getArtifact().getFile().getAbsolutePath().replaceFirst(".jar$", ".pom");

                    final MavenXpp3Reader reader = new MavenXpp3Reader();
                    final Model model = reader.read(new FileReader(pomPath));

                    final IssueManagement issues = model.getIssueManagement();
                    if (issues != null) {
                        log.info(format("Found issue tracker %s at %s", issues.getSystem(), issues.getUrl()));
                    } else {
                        if (model.getScm() != null) {
                            log.info(format("Found SCM at %s", model.getScm().getUrl()));
                        } else {
                            log.info(format("Found URL at %s", model.getUrl()));
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error resolving artifact: " + e.getMessage());
                }
            });
    }
}
