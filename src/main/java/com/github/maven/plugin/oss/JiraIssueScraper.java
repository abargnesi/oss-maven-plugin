package com.github.maven.plugin.oss;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;

import com.machinepublishers.jbrowserdriver.JBrowserDriver;
import com.machinepublishers.jbrowserdriver.Settings;
import com.machinepublishers.jbrowserdriver.Timezone;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;

public class JiraIssueScraper implements IssueScraper {

    private CloseableHttpClient client;

    public JiraIssueScraper() {
        client = HttpClientBuilder
            .create()
            .setRedirectStrategy(new DefaultRedirectStrategy())
            .build();
    }

    @Override
    public IssueSnapshot scrape(final String baseURL) throws URISyntaxException {
        final URI uri = new URI(baseURL + "?filter=allopenissues");
        final String baseURI = uri.getScheme() + "://" + uri.getHost();

        JBrowserDriver driver = new JBrowserDriver(Settings.builder().timezone(Timezone.AMERICA_NEWYORK).build());

        try {
            driver.get(uri.toString());

            final List<WebElement> issueListElements = driver.findElementsByCssSelector("ol.issue-list > li[data-key]");

            final List<Issue> openIssues = issueListElements
                .stream()
                .map(el -> {
                    final WebElement linkEl = el.findElement(By.cssSelector("a.splitview-issue-link"));
                    final WebElement titleEl = el.findElement(By.cssSelector("span.issue-link-summary"));
                    return new Issue(titleEl.getText(), baseURI + linkEl.getAttribute("href"), emptySet(), null, null);
                })
                .collect(toList());

            return new IssueSnapshot(new Date(), "jira", uri.toString(), openIssues, emptyList());
        } finally {
            driver.quit();
        }
    }
}
