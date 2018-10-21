package com.github.maven.plugin.oss;

import static com.github.maven.plugin.oss.Utility.dependencyToCoordinate;
import static com.github.maven.plugin.oss.Utility.retrieveFullyQualifiedClasses;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
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
import java.util.HashSet;
import java.util.Map;
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
        // TODO ✔ Create mapping of [FQC name] to [Artifact]
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

        final Map<String, Artifact> fqcToArtifact = project.getDependencies().stream()
            .filter(dep -> DEFAULT_SCOPES.contains(dep.getScope()))
            .flatMap(dep -> {
                final ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
                try {
                    final ArtifactResult result = artifactResolver.resolveArtifact(buildingRequest, dependencyToCoordinate(dep));
                    final Artifact artifact = result.getArtifact();
                    return retrieveFullyQualifiedClasses(artifact)
                        .stream()
                        .map(fqc -> Pair.of(fqc, artifact));
                } catch (Exception e) {
                    throw new RuntimeException("Error resolving artifact: " + e.getMessage(), e);
                }
            })
            .collect(toMap(Pair::getKey, Pair::getValue, (art1, art2) -> art1));

        System.out.println(fqcToArtifact);
    }
}
