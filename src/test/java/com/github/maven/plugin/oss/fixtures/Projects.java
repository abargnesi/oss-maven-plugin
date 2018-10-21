package com.github.maven.plugin.oss.fixtures;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;

import java.util.Collections;
import java.util.List;

public final class Projects {

    public static abstract class Base extends MavenProjectStub {

        public Base(final Model model) {
            super(model);
        }

        public List<ArtifactRepository> getRemoteArtifactRepositories() {
            return Collections.singletonList(
                new DefaultArtifactRepository(
                    "central",
                    "http://repo.maven.apache.org/maven2",
                    new DefaultRepositoryLayout()));
        }
    }

    public static final class Single extends Base {

        public Single() {
            super(testModel("oss-maven-plugin-test-single"));
        }
    }

    public static final class Multiple extends Base {

        public Multiple() {
            super(testModel("oss-maven-plugin-test-multiple"));
        }
    }

    private static final Model testModel(final String artifactId) {
        final Model model = new Model();
        model.setGroupId("com.github");
        model.setArtifactId(artifactId);
        model.setVersion("1.0.0");
        return model;
    }
}
