package com.github.maven.plugin.oss;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.ArtifactCoordinate;
import org.apache.maven.shared.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.artifact.resolve.ArtifactResult;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;

final class Utility {

    static ArtifactCoordinate dependencyToCoordinate(final Dependency dependency) {
        final DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
        coordinate.setGroupId(dependency.getGroupId());
        coordinate.setArtifactId(dependency.getArtifactId());
        coordinate.setVersion(dependency.getVersion());
        return coordinate;
    }

    static List<String> retrieveFullyQualifiedClasses(final Artifact artifact) {
        final File artifactFile = artifact.getFile();
        if (!artifactFile.getAbsolutePath().endsWith(".jar")) {
            return emptyList();
        }

        try {
            return new JarFile(artifactFile)
                .stream()
                .filter(e -> e.getName().endsWith(".class"))
                .map(e -> e.getName().replaceAll("[/$]", ".").replaceAll("\\.class$", ""))
                .collect(toList());
        } catch (IOException e) {
            throw new RuntimeException("Could not read JAR file: " + artifactFile.getAbsolutePath());
        }
    }

    static Map<Artifact, Long> computeDependencyUsage(
        final MavenProject project, final MavenSession session, final ArtifactResolver resolver) {

        final Map<String, Artifact> fqcDependency = project.getDependencies().stream()
            .filter(dep -> !"system".equalsIgnoreCase(dep.getScope()))
            .flatMap(dep -> {
                final ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
                try {
                    final ArtifactResult result = resolver.resolveArtifact(buildingRequest, dependencyToCoordinate(dep));
                    final Artifact artifact = result.getArtifact();
                    return retrieveFullyQualifiedClasses(artifact)
                        .stream()
                        .map(fqc -> Pair.of(fqc, artifact));
                } catch (Exception e) {
                    throw new RuntimeException("Error resolving artifact: " + e.getMessage(), e);
                }
            })
            .collect(toMap(Pair::getKey, Pair::getValue, (art1, art2) -> art1));

        final List<String> fqcImported = retrieveImportedClasses(project);

        final Map<Artifact, Long> counts = new HashMap<>(fqcDependency.values().size());
        fqcImported.forEach(fqcImport -> {
            final Artifact match = fqcDependency.get(fqcImport);
            if (match != null) {
                counts.merge(match, 1L, Long::sum);
            }
        });

        return counts;
    }

    static File resolvePomFile(final Artifact artifact) {
        return new File(artifact.getFile().getAbsolutePath().replaceFirst(".jar$", ".pom"));
    }

    static Pair<String, String> resolveIssueSite(final File pomFile) throws Exception {
        final MavenXpp3Reader reader = new MavenXpp3Reader();
        final Model model = reader.read(new FileReader(pomFile));

        final IssueManagement issues = model.getIssueManagement();
        if (issues != null) {
            return Pair.of(issues.getSystem(), issues.getUrl());
        } else {
            if (model.getScm() != null) {
                // TODO Check scm url for GitHub or BitBucket; convert URL appropriately.
                return Pair.of("TODO", model.getScm().getUrl());
            } else {
                // TODO Check main url for GitHub or BitBucket; convert URL appropriately.
                return Pair.of("TODO", model.getUrl());
            }
        }
    }

    private static Stream<File> recursivelyScanForFilesInDirectory(final String baseDir) {
        final DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(baseDir);
        scanner.scan();
        return Stream.of(scanner.getIncludedFiles())
            .map(file -> new File(baseDir, file));
    }

    private static List<String> retrieveImportedClasses(final MavenProject project) {
        final Set<File> sourceFiles = project.getCompileSourceRoots()
            .stream()
            .flatMap(Utility::recursivelyScanForFilesInDirectory)
            .filter(f -> f.getAbsolutePath().endsWith(".java"))
            .collect(toSet());

        return sourceFiles
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
            .collect(toList());
    }

    /**
     * @apiNote Private; static accessors only.
     */
    private Utility() {
    }
}
