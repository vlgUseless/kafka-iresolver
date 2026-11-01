package ru.polytech.iresolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.resolver.common.api.component.connector.IssueClient;
import com.koval.resolver.common.api.model.issue.Issue;
import com.koval.resolver.common.api.model.issue.IssueField;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Доступ к сохранённому снимку без сетевых запросов и изменений в Jira. */
public final class SnapshotClient implements IssueClient {
    private final Map<String, Issue> issuesByKey;
    private final List<Issue> openIssues;
    private final int resolvedCount;

    private SnapshotClient(Map<String, Issue> issuesByKey, List<Issue> openIssues, int resolvedCount) {
        this.issuesByKey = Collections.unmodifiableMap(issuesByKey);
        this.openIssues = Collections.unmodifiableList(openIssues);
        this.resolvedCount = resolvedCount;
    }

    public static SnapshotClient read(Path path) throws IOException {
        JsonNode snapshot = new ObjectMapper().readTree(path.toFile());
        if (snapshot == null || !snapshot.isObject()) {
            throw new IOException("Снимок должен содержать JSON-объект: " + path);
        }
        JsonNode resolved = requireArray(snapshot, "resolved");
        JsonNode opened = requireArray(snapshot, "opened");
        Map<String, Issue> issuesByKey = new LinkedHashMap<>();
        List<Issue> openIssues = new ArrayList<>();
        JiraIssueMapper mapper = new JiraIssueMapper();

        for (JsonNode item : resolved) {
            addUnique(issuesByKey, mapper.fromJson(item));
        }
        for (JsonNode item : opened) {
            Issue issue = mapper.fromJson(item);
            addUnique(issuesByKey, issue);
            openIssues.add(issue);
        }
        return new SnapshotClient(issuesByKey, openIssues, resolved.size());
    }

    private static JsonNode requireArray(JsonNode root, String name) throws IOException {
        JsonNode value = root.path(name);
        if (!value.isArray()) {
            throw new IOException("В снимке отсутствует массив " + name);
        }
        return value;
    }

    private static void addUnique(Map<String, Issue> issues, Issue issue) throws IOException {
        if (issues.putIfAbsent(issue.getKey(), issue) != null) {
            throw new IOException("Задача повторяется в снимке: " + issue.getKey());
        }
    }

    public List<Issue> getOpenIssues() {
        return openIssues;
    }

    public int getResolvedCount() {
        return resolvedCount;
    }

    @Override
    public Issue getIssueByKey(String key) {
        Issue issue = issuesByKey.get(key);
        if (issue == null) {
            throw new IllegalArgumentException("В снимке нет задачи " + key);
        }
        return issue;
    }

    @Override
    public int getTotalIssues(String query) {
        throw new UnsupportedOperationException("Снимок не выполняет JQL; используйте getOpenIssues");
    }

    @Override
    public List<Issue> search(String query, int maxResults, int startAt, List<String> fields) {
        throw new UnsupportedOperationException("Снимок не выполняет JQL; используйте getOpenIssues");
    }

    @Override
    public List<IssueField> getIssueFields() {
        return Collections.emptyList();
    }

    @Override
    public void close() {
        // Все данные находятся в памяти; открытых ресурсов нет.
    }
}
