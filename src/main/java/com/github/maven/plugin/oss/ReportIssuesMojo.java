package com.github.maven.plugin.oss;

import static com.github.maven.plugin.oss.Utility.resolveIssueSite;
import static com.github.maven.plugin.oss.Utility.resolvePOMLineage;
import static java.util.Collections.reverseOrder;
import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toList;

import net.steppschuh.markdowngenerator.link.Link;
import net.steppschuh.markdowngenerator.list.UnorderedList;
import net.steppschuh.markdowngenerator.table.Table;
import net.steppschuh.markdowngenerator.text.Text;
import net.steppschuh.markdowngenerator.text.emphasis.BoldText;
import net.steppschuh.markdowngenerator.text.heading.Heading;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
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
        // TODO ✔ Compute POM inheritance chain.
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
        final List<Entry<Pair<Artifact, MavenProject>, Long>> orderedDependencyUsage =
            Utility.computeDependencyUsage(topProject, moduleProjects, session, artifactResolver)
                .entrySet()
                .stream()
                .sorted(reverseOrder(comparingByValue()))
                .collect(toList());

        final StringBuilder md = new StringBuilder();
        md.append(new Heading(topProject.getName() + " :: Open Source Issues", 1)).append("\n");

        orderedDependencyUsage.forEach(du -> {
            final Pair<Artifact, MavenProject> art = du.getKey();
            final Artifact artifact = art.getLeft();

            try {
                final List<Model> pomLineage = resolvePOMLineage(artifact, artifactResolver, art.getRight(), session);
                final String name;
                if (!pomLineage.isEmpty()) {
                    name = pomLineage.get(0).getName();
                } else {
                    name = artifact.getArtifactId();
                }

                final Pair<String, String> issueSite = resolveIssueSite(pomLineage);

                md.append(new Heading(name, 2)).append("\n");
                md.append(new Text(artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion())).append("\n");
                md.append(new BoldText(du.getValue())).append(new Text(" imports declared.")).append("\n\n");

                if (issueSite == null) {
                    md.append(new Text("Issues: unknown")).append("\n");
                } else {
                    final IssueSnapshot snapshot = issueScraper(issueSite.getLeft()).scrape(issueSite.getRight());
                    md.append(new Link("Issues", snapshot.getUrl())).append("\n\n");
                    if (!snapshot.getStateCounts().isEmpty()) {
                        md.append(new Text("State Counts")).append("\n");
                        md.append(new UnorderedList<>(snapshot.getStateCounts())).append("\n\n");
                    }

                    if (!snapshot.getOpenIssues().isEmpty()) {
                        Table.Builder tableBuilder = new Table.Builder()
                            .withAlignments(Table.ALIGN_LEFT, Table.ALIGN_LEFT)
                            .addRow("Title", "Tags");

                        snapshot.getOpenIssues().forEach(i ->
                            tableBuilder.addRow(new Link(i.getTitle(), i.getUrl()), String.join(", ", i.getTags())));
                        md.append(tableBuilder.build()).append("\n\n");
                    }
                }
            } catch (Exception e) {
                log.warn("Error file resolving issues, skipping artifact: " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion());
            }
        });

        System.out.println(md.toString());
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

    private static IssueScraper issueScraper(final String issueSystem) {
        if (issueSystem == null) {
            return null;
        }

        switch(issueSystem) {
            case "github":
                return new GitHubIssueScraper();
            case "jira":
                return new JiraIssueScraper();
            default:
                return new NoOpIssueScraper(issueSystem);
        }
    }
}
