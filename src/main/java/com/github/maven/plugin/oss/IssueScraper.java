package com.github.maven.plugin.oss;

import java.io.IOException;
import java.net.URISyntaxException;

public interface IssueScraper {

    IssueSnapshot scrape(final String issueURL) throws IOException, URISyntaxException;
}
