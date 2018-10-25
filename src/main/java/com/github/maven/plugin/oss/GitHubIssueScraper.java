package com.github.maven.plugin.oss;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

public class GitHubIssueScraper implements IssueScraper {

    private CloseableHttpClient client;

    public GitHubIssueScraper() {
        client = HttpClientBuilder
            .create()
            .disableRedirectHandling()
            .build();
    }

    @Override
    public IssueSnapshot scrape(final String baseURL) throws IOException, URISyntaxException {
        final URI uri = new URI(baseURL + "?q=is%3Aissue+is%3Aopen+sort%3Aupdated-desc");

        final CloseableHttpResponse response = client.execute(new HttpGet(uri));
        if (response.getStatusLine().getStatusCode() == 200) {
            final String baseURI = uri.getScheme() + "://" + uri.getHost();

            final Document htmlDoc = Jsoup.parse(IOUtils.toString(response.getEntity().getContent(), UTF_8));

            final String textDivStates = htmlDoc.select("div.states").first().text();
            final Matcher stateCountMatcher = compile("(?<count>[0-9]+) (?<type>(Open|Closed))").matcher(textDivStates);
            final List<Pair<String, Integer>> stateCounts = new ArrayList<>();
            while (stateCountMatcher.find()) {
                stateCounts.add(Pair.of(stateCountMatcher.group("type"), Integer.valueOf(stateCountMatcher.group("count"))));
            }

            final Elements issueElements = htmlDoc.select("li.js-issue-row");
            final List<Issue> openIssues = issueElements
                .stream()
                .map(el -> {
                    final Element titleElement = el.selectFirst("a.js-navigation-open");
                    final String url = baseURI + titleElement.attr("href");
                    final Set<String> tags =
                        el.select("a.IssueLabel")
                            .stream()
                            .map(Element::text)
                            .collect(toSet());

                    return new Issue(titleElement.text(), url, tags, null, null);
                })
                .collect(toList());

            return new IssueSnapshot(new Date(), "github", uri.toString(), openIssues, stateCounts);
        }

        return new IssueSnapshot(new Date(), "github", uri.toString(), emptyList(), emptyList());
    }
}
