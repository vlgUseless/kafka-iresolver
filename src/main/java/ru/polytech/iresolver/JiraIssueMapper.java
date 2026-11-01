package ru.polytech.iresolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.koval.resolver.common.api.model.issue.Comment;
import com.koval.resolver.common.api.model.issue.Issue;
import com.koval.resolver.common.api.model.issue.IssueType;
import com.koval.resolver.common.api.model.issue.User;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.joda.time.DateTime;

/** Преобразует JSON карточки Jira в модель данных оригинального iResolver. */
final class JiraIssueMapper {
    Issue fromJson(JsonNode source) {
        String key = source.path("key").asText();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Карточка Jira не содержит key");
        }

        JsonNode fields = source.path("fields");
        Issue issue = new Issue();
        issue.setKey(key);
        issue.setLink(URI.create("https://issues.apache.org/jira/browse/" + key));
        issue.setSummary(fields.path("summary").asText(""));
        issue.setDescription(fields.path("description").asText(""));
        issue.setStatus(fields.path("status").path("name").asText(""));
        issue.setPriority(fields.path("priority").path("name").asText(""));
        issue.setResolution(fields.path("resolution").path("name").asText(""));
        issue.setIssueType(new IssueType(fields.path("issuetype").path("name").asText(""), false));
        issue.setCreationDate(readDate(fields.path("created")));
        issue.setUpdateDate(readDate(fields.path("updated")));
        issue.setAssignee(readUser(fields.path("assignee")));
        issue.setReporter(readUser(fields.path("reporter")));
        issue.setLabels(readLabels(fields.path("labels")));
        issue.setComments(readComments(fields.path("comment").path("comments")));

        // Эти поля не загружаются в снимок и не используются выбранными правилами.
        issue.setAttachments(Collections.emptyList());
        issue.setIssueLinks(Collections.emptyList());
        issue.setComponents(Collections.emptyList());
        issue.setFixVersions(Collections.emptyList());
        issue.setAffectedVersions(Collections.emptyList());
        issue.setSubTasks(Collections.emptyList());
        issue.setIssueFields(Collections.emptyList());
        return issue;
    }

    private User readUser(JsonNode source) {
        User user = new User();
        boolean missing = source.isNull() || source.isMissingNode();
        user.setName(missing ? "<unknown>" : source.path("name").asText("<unknown>"));
        user.setDisplayName(missing ? "<unknown>" : source.path("displayName").asText("<unknown>"));
        user.setGroups(Collections.emptyList());
        return user;
    }

    private DateTime readDate(JsonNode source) {
        return source.isTextual() && !source.asText().isEmpty()
                ? new DateTime(source.asText()) : null;
    }

    private List<String> readLabels(JsonNode source) {
        List<String> labels = new ArrayList<>();
        for (JsonNode label : source) {
            labels.add(label.asText());
        }
        return labels;
    }

    private List<Comment> readComments(JsonNode source) {
        List<Comment> comments = new ArrayList<>();
        for (JsonNode item : source) {
            Comment comment = new Comment();
            comment.setAuthor(readUser(item.path("author")));
            comment.setBody(item.path("body").asText(""));
            comments.add(comment);
        }
        return comments;
    }
}
