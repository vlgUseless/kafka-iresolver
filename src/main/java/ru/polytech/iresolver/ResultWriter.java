package ru.polytech.iresolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koval.resolver.common.api.configuration.Configuration;
import com.koval.resolver.common.api.model.issue.Issue;
import com.koval.resolver.common.api.model.issue.User;
import com.koval.resolver.common.api.model.result.DocumentationResult;
import com.koval.resolver.common.api.model.result.IssueAnalysingResult;
import com.koval.resolver.common.api.model.result.Pair;
import com.koval.resolver.reporter.html.HtmlReportGenerator;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.yaml.snakeyaml.Yaml;

/** Сохраняет оригинальный HTML-отчёт и данные для последующей проверки результатов. */
public final class ResultWriter {
    private final Configuration configuration;
    private final ObjectMapper json = new ObjectMapper();

    public ResultWriter(Configuration configuration) {
        this.configuration = configuration;
    }

    public void write(List<IssueAnalysingResult> results) throws IOException {
        Path outputDirectory = Paths.get(configuration.getReporters().getHtml().getOutputFile())
                .toAbsolutePath().getParent();
        Files.createDirectories(outputDirectory);
        new HtmlReportGenerator(configuration.getReporters().getHtml(),
                Arrays.asList("issues", "documentation", "rule-engine")).generate(results);

        ArrayNode summary = json.createArrayNode();
        for (IssueAnalysingResult result : results) {
            summary.add(toJson(result));
        }
        json.writerWithDefaultPrettyPrinter()
                .writeValue(outputDirectory.resolve("results.json").toFile(), summary);
        try (Writer writer = Files.newBufferedWriter(
                outputDirectory.resolve("results.yml"), StandardCharsets.UTF_8)) {
            new Yaml().dump(json.convertValue(summary, List.class), writer);
        }
    }

    private ObjectNode toJson(IssueAnalysingResult result) {
        ObjectNode row = json.createObjectNode();
        row.put("key", result.getOriginalIssue().getKey());
        row.put("summary", result.getOriginalIssue().getSummary());
        writeSimilarIssues(row.putArray("similar"), result);
        writeEngineers(row.putArray("engineers"), result);
        writeDocumentation(row.putArray("documentation"), result);
        ArrayNode proposals = row.putArray("proposals");
        for (String proposal : result.getProposals()) {
            proposals.add(proposal);
        }
        return row;
    }

    private void writeSimilarIssues(ArrayNode target, IssueAnalysingResult result) {
        for (Pair<Issue, Double> pair : result.getSimilarIssues()) {
            ObjectNode item = target.addObject();
            item.put("key", pair.getEntity().getKey());
            item.put("summary", pair.getEntity().getSummary());
            item.put("score", pair.getMetric());
        }
    }

    private void writeEngineers(ArrayNode target, IssueAnalysingResult result) {
        for (Pair<User, Integer> pair : result.getQualifiedUsers()) {
            ObjectNode item = target.addObject();
            item.put("name", pair.getEntity().getDisplayName());
            item.put("login", pair.getEntity().getName());
            item.put("rank", pair.getMetric());
        }
    }

    private void writeDocumentation(ArrayNode target, IssueAnalysingResult result) {
        for (DocumentationResult document : result.getDocumentationResults()) {
            ObjectNode item = target.addObject();
            item.put("file", document.getFileName());
            item.put("page", document.getPageNumber());
            item.put("score", document.getRank());
        }
    }
}
