package com.github.maven.plugin.oss;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.ArtifactCoordinate;
import org.apache.maven.shared.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.artifact.resolve.ArtifactResult;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
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

    static Map<Pair<Artifact, MavenProject>, Long> computeDependencyUsage(
        final MavenProject topProject, final List<MavenProject> projects,
        final MavenSession session, final ArtifactResolver resolver) {

        final List<MavenProject> jarProjects = projects
            .stream()
            .filter(project -> "jar".equalsIgnoreCase(project.getPackaging()))
            .collect(toList());

        final List<String> fqcImported =
            jarProjects
            .stream()
            .filter(project -> !topProject.equals(project))
            .flatMap(project -> Utility.retrieveImportedClasses(project).stream())
            .collect(toList());

        final Map<String, Pair<Artifact, MavenProject>> fqcDependency =
            jarProjects
            .stream()
            .flatMap(project -> project.getDependencies().stream().map(dep -> Pair.of(project, dep)))
            .filter(dep -> !"system".equalsIgnoreCase(dep.getRight().getScope()))
            .flatMap(dep -> {
                final ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                buildingRequest.setRemoteRepositories(dep.getLeft().getRemoteArtifactRepositories());
                try {
                    final ArtifactResult result = resolver.resolveArtifact(buildingRequest, dependencyToCoordinate(dep.getRight()));
                    final Artifact artifact = result.getArtifact();
                    return retrieveFullyQualifiedClasses(artifact)
                        .stream()
                        .map(fqc -> Triple.of(fqc, artifact, dep.getKey()));
                } catch (Exception e) {
                    throw new RuntimeException("Error resolving artifact: " + e.getMessage(), e);
                }
            })
            .collect(toMap(Triple::getLeft, t -> Pair.of(t.getMiddle(), t.getRight()), (art1, art2) -> art1));

        final Map<Pair<Artifact, MavenProject>, Long> counts = new HashMap<>(fqcDependency.values().size());
        fqcImported.forEach(fqcImport -> {
            final Pair<Artifact, MavenProject> match = fqcDependency.get(fqcImport);
            if (match != null) {
                counts.merge(match, 1L, Long::sum);
            }
        });

        return counts;
    }

    static List<Model> resolvePOMLineage(
        final Artifact artifact,  final ArtifactResolver artifactResolver,
        final MavenProject project, final MavenSession session)
        throws IOException, XmlPullParserException, ArtifactResolverException {

        final File basePOM = new File(artifact.getFile().getAbsolutePath().replaceFirst(".jar$", ".pom"));

        final MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(new FileReader(basePOM));

        Parent pomParent = model.getParent();


        final List<Model> pomLineage = new ArrayList<>();
        pomLineage.add(model);
        while (pomParent != null) {
            // resolve parent POM
            final ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());

            DefaultArtifactCoordinate parentCoordinate = new DefaultArtifactCoordinate();
            parentCoordinate.setGroupId(pomParent.getGroupId());
            parentCoordinate.setArtifactId(pomParent.getArtifactId());
            parentCoordinate.setVersion(pomParent.getVersion());
            parentCoordinate.setExtension("pom");

            ArtifactResult parentPOMResult = artifactResolver.resolveArtifact(buildingRequest, parentCoordinate);

            // read POM model
            File parentPOMFile = parentPOMResult.getArtifact().getFile();
            Model pomModel = reader.read(new FileReader(parentPOMFile));

            pomLineage.add(pomModel);
            pomParent = pomModel.getParent();
        }

        return pomLineage;
    }

    static String normalizeIssueSystem(final String text) {
        if (StringUtils.isEmpty(text)) {
            return null;
        }

        final String nameLower = text.toLowerCase();
        if (nameLower.contains("github")) {
            return "github";
        } else if (nameLower.contains("jira")) {
            return "jira";
        } else if (nameLower.contains("sourceforge")) {
            return "sourceforge";
        } else if (nameLower.contains("google") && nameLower.contains("code")) {
            return "google code";
        } else if (nameLower.contains("youtrack")) {
            return "youtrack";
        } else if (nameLower.contains("bugzilla")) {
            return "bugzilla";
        } else {
            throw new UnsupportedOperationException("do not know " + text);
        }
    }

    static Pair<String, String> resolveIssueSite(final List<Model> pomLineage) {
        // prefer issue management
        for (final Model pom : pomLineage) {
            final IssueManagement issues = pom.getIssueManagement();
            if (issues != null) {
                String issuesURL = issues.getUrl();
                issuesURL = issuesURL.replace("${project.artifactId}", pom.getArtifactId());

                String normalizedIssuesSystem = normalizeIssueSystem(issues.getSystem());
                if (normalizedIssuesSystem == null) {
                    normalizedIssuesSystem = normalizeIssueSystem(issuesURL);
                }

                return Pair.of(normalizedIssuesSystem, issuesURL);
            }
        }

        // infer from github scm
        for (final Model pom : pomLineage) {
            final Scm scm = pom.getScm();
            if (scm != null && scm.getUrl() != null) {
                Matcher ghMatch = compile("github\\.com/(?<org>[^/]+)/(?<repo>[a-zA-Z0-9_\\-.]+)").matcher(scm.getUrl());
                if (ghMatch.find()) {
                    return Pair.of("github",
                        format("https://github.com/%s/%s/issues", ghMatch.group("org"), ghMatch.group("repo")));
                }

                ghMatch = compile("git@github\\.com:(?<org>[^/]+)/(?<repo>[a-zA-Z0-9_\\-.]+)").matcher(scm.getUrl());
                if (ghMatch.find()) {
                    return Pair.of("github",
                        format("https://github.com/%s/%s/issues", ghMatch.group("org"), ghMatch.group("repo")));
                }
            }
        }

        // infer from github url
        for (final Model pom : pomLineage) {
            if (pom.getUrl() != null) {
                final Matcher ghMatch = compile(".*github\\.com/(?<org>[^/]+)/(?<repo>[a-zA-Z0-9_\\-.]+).*").matcher(pom.getUrl());
                if (ghMatch.matches()) {
                    return Pair.of("github",
                        format("https://github.com/%s/%s/issues", ghMatch.group("org"), ghMatch.group("repo")));
                }
            }
        }
        return null;
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
