## oss-maven-plugin

A plugin that reports on issues for the open source dependencies
that you use in your maven project.

Dependency usage is determined based on import statements
declared in your *.java* files. The report is ordered by most-imported
dependencies.

#### build

Clone repository. Run `mvn clean install`.

#### use in project

Add the following to your `<build><plugins></plugins></build>` section:

```xml
<plugin>
    <groupId>com.github</groupId>
    <artifactId>oss-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</plugin>
```

Then run `mvn oss:report-issues`.
