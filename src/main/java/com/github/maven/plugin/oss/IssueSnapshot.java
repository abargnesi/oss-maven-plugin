package com.github.maven.plugin.oss;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Date;
import java.util.List;

public class IssueSnapshot {

    private Date                        snapshotDate;
    private String                      issueSystem;
    private String                      url;
    private List<Issue>                 openIssues;
    private List<Pair<String, Integer>> stateCounts;

    public IssueSnapshot(Date snapshotDate, String issueSystem, String url, List<Issue> openIssues, List<Pair<String, Integer>> stateCounts) {
        this.snapshotDate = snapshotDate;
        this.issueSystem  = issueSystem;
        this.url          = url;
        this.openIssues   = openIssues;
        this.stateCounts  = stateCounts;
    }

    public Date getSnapshotDate() {
        return snapshotDate;
    }

    public String getIssueSystem() {
        return issueSystem;
    }

    public String getUrl() {
        return url;
    }

    public List<Pair<String, Integer>> getStateCounts() {
        return stateCounts;
    }

    public List<Issue> getOpenIssues() {
        return openIssues;
    }
}
