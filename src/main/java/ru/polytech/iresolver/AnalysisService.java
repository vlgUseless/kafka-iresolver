package ru.polytech.iresolver;

import com.koval.resolver.common.api.configuration.Configuration;
import com.koval.resolver.common.api.model.issue.Issue;
import com.koval.resolver.common.api.model.result.IssueAnalysingResult;
import com.koval.resolver.common.api.vectorization.TextDataExtractor;
import com.koval.resolver.common.api.vectorization.VectorModelSerializer;
import com.koval.resolver.processor.documentation.DocumentationProcessor;
import com.koval.resolver.processor.documentation.core.DocOutputFilesParser;
import com.koval.resolver.processor.documentation.core.FileRepository;
import com.koval.resolver.processor.issues.IssuesProcessor;
import com.koval.resolver.processor.rules.core.impl.DroolsRuleEngine;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Вызывает поиск похожих задач, документации и правила для каждой открытой карточки. */
public final class AnalysisService {
    private final Configuration configuration;
    private final SnapshotClient snapshot;

    public AnalysisService(Configuration configuration, SnapshotClient snapshot) {
        this.configuration = configuration;
        this.snapshot = snapshot;
    }

    public List<IssueAnalysingResult> analyzeOpenIssues() throws IOException {
        IssuesProcessor issuesProcessor = new IssuesProcessor(snapshot, configuration);
        DocumentationProcessor documentationProcessor = createDocumentationProcessor();
        List<IssueAnalysingResult> results = new ArrayList<>();
        String rulesLocation = configuration.getProcessors().getRuleEngine().getRulesLocation();

        try (DroolsRuleEngine rules = new DroolsRuleEngine(rulesLocation)) {
            for (Issue issue : snapshot.getOpenIssues()) {
                System.out.println("Анализ задачи: " + issue.getKey());
                IssueAnalysingResult result = new IssueAnalysingResult();
                issuesProcessor.run(issue, result);
                documentationProcessor.run(issue, result);
                result.setProposals(rules.execute(issue));
                results.add(result);
            }
        }
        return results;
    }

    private DocumentationProcessor createDocumentationProcessor() throws IOException {
        var documentation = configuration.getProcessors().getDocumentation();
        var vectorizer = configuration.getVectorizer();
        var model = new VectorModelSerializer(vectorizer).deserialize(
                new File(documentation.getVectorModelFile()), vectorizer.getLanguage());
        var metadata = new DocOutputFilesParser(documentation, new FileRepository());
        return new DocumentationProcessor(documentation, metadata, model, new TextDataExtractor());
    }
}
