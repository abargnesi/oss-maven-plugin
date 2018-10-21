package com.github.maven.plugin.oss;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.shared.artifact.ArtifactCoordinate;
import org.apache.maven.shared.artifact.DefaultArtifactCoordinate;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
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

    static Stream<File> recursivelyScanForFilesInDirectory(final String baseDir) {
        final DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(baseDir);
        scanner.scan();
        return Stream.of(scanner.getIncludedFiles())
            .map(file -> new File(baseDir, file));
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

    /**
     * @apiNote Private; static accessors only.
     */
    private Utility() {
    }
}
