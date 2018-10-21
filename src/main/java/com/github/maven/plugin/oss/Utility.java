package com.github.maven.plugin.oss;

import org.apache.maven.model.Dependency;
import org.apache.maven.shared.artifact.ArtifactCoordinate;
import org.apache.maven.shared.artifact.DefaultArtifactCoordinate;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.File;
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

    /**
     * @apiNote Private; static accessors only.
     */
    private Utility() {
    }
}
