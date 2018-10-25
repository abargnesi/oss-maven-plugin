package com.github.maven.plugin.oss;

import java.util.Date;
import java.util.Set;

public class Issue {

    private String title;
    private String url;
    private Set<String> tags;
    private Date created;
    private Date lastActivity;

    public Issue(String title, String url, Set<String> tags, Date created, Date lastActivity) {
        this.title = title;
        this.url = url;
        this.tags = tags;
        this.created = created;
        this.lastActivity = lastActivity;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public Set<String> getTags() {
        return tags;
    }

    public Date getCreated() {
        return created;
    }

    public Date getLastActivity() {
        return lastActivity;
    }
}
