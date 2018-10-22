package com.github.maven.plugin.oss;

import static java.util.Collections.reverseOrder;
import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toList;

import org.apache.commons.collections.CollectionUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Mojo(
    name = "report-issues",
    aggregator = true,
    requiresDirectInvocation = true)
public class ReportIssuesMojo extends AbstractMojo {

    @Parameter(
        defaultValue = "${session}",
        required = true,
        readonly = true)
    private MavenSession session;

    @Parameter(
        defaultValue = "${project}",
        readonly = true)
    private MavenProject project;

    @Parameter(
        defaultValue = "${reactorProjects}",
        readonly = true,
        required = true)
    private List<MavenProject> reactorProjects;

    @Component
    private ArtifactResolver artifactResolver;

    public void execute() {
        // TODO ✔ Create mapping of [FQC name] to [Artifact]
        // TODO ✔ Compute artifact counters for used imports.
        // TODO ✔ Determine transitive dependencies, per project, so more imports match.
        // TODO Infer issue management system and URL (check issueManagement, infer from SCM, infer from URL).
        // TODO Crawl issues using http-client and jsoup.

        if (CollectionUtils.isNotEmpty(reactorProjects)) {
            reactorReport(reactorProjects, project, session, artifactResolver, getLog());
        } else {
            projectReport(session.getCurrentProject(), session, artifactResolver, getLog());
        }
    }

    private static void reactorReport(
        final List<MavenProject> moduleProjects, final MavenProject topProject,
        final MavenSession session, final ArtifactResolver artifactResolver,
        final Log log) {

        log.info("Analyzing multi-module build for: " + topProject.getName());
        final List<Entry<Artifact, Long>> orderedDependencyUsage =
            Utility.computeDependencyUsage(topProject, moduleProjects, session, artifactResolver)
                .entrySet()
                .stream()
                .sorted(reverseOrder(comparingByValue()))
                .collect(toList());

        orderedDependencyUsage.forEach(du -> {
            final Artifact art = du.getKey();
            log.info("Dependency → " + art.getGroupId() + ":" + art.getArtifactId() + ":" + art.getVersion());
            log.info("  Import count: " + du.getValue());
        });
    }

    private static void projectReport(
        final MavenProject project, final MavenSession session,
        final ArtifactResolver artifactResolver, final Log log) {

        log.info("Analyzing project: " + project.getName());
        final Map<Artifact, Long> dependencyUsage = Utility.computeDependencyUsage(project, session, artifactResolver);

        final List<Entry<Artifact, Long>> orderedDependencyUsage = dependencyUsage.entrySet()
            .stream()
            .sorted(reverseOrder(comparingByValue()))
            .collect(toList());

        orderedDependencyUsage.forEach(du -> {
            final Artifact art = du.getKey();
            log.info("Dependency → " + art.getGroupId() + ":" + art.getArtifactId() + ":" + art.getVersion());
            log.info("  Import count: " + du.getValue());
        });
    }
}
