package com.github.maven.plugin.oss;

import static java.util.Collections.emptyList;

import java.util.Date;

public class NoOpIssueScraper implements IssueScraper {

    private final String issueSystem;

    public NoOpIssueScraper(final String issueSystem) {
        this.issueSystem = issueSystem;
    }

    @Override
    public IssueSnapshot scrape(final String baseURL) {
        return new IssueSnapshot(new Date(), issueSystem, baseURL, emptyList(), emptyList());
    }
}
