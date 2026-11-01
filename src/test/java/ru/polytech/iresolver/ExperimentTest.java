package ru.polytech.iresolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.resolver.common.api.model.issue.Issue;
import com.koval.resolver.common.api.model.issue.IssueType;
import com.koval.resolver.common.api.configuration.Configuration;
import com.koval.resolver.common.api.configuration.component.ProcessorsConfiguration;
import com.koval.resolver.common.api.configuration.component.VectorizerConfiguration;
import com.koval.resolver.common.api.configuration.component.processors.IssuesProcessorConfiguration;
import com.koval.resolver.common.api.configuration.component.processors.DocumentationProcessorConfiguration;
import com.koval.resolver.processor.rules.core.impl.DroolsRuleEngine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/** Проверки поведения DRL и преобразования снимка; запускаются через run.ps1 -Test. */
public final class ExperimentTest {
    private final List<String> passed = new ArrayList<>();

    public static void main(String[] arguments) throws Exception {
        ExperimentTest suite = new ExperimentTest();
        suite.checkReproductionRule();
        suite.checkMissingFields();
        suite.checkInvalidSnapshots();
        suite.checkModelTraining();
        Files.createDirectories(Paths.get("output"));
        Files.write(Paths.get("output/test-results.txt"), suite.passed, StandardCharsets.UTF_8);
        suite.passed.forEach(System.out::println);
        System.out.printf("Успешно выполнено %d проверок.%n", suite.passed.size());
    }

    private void checkReproductionRule() throws IOException {
        try (DroolsRuleEngine engine = new DroolsRuleEngine("file:rules/Kafka*.drl")) {
            expectProposals(engine, "Bug без шагов", "KAFKA-TEST", "Bug",
                    "Broker fails after startup.", 1);
            expectProposals(engine, "Bug с шагами", "KAFKA-TEST", "Bug",
                    "Steps to reproduce: start broker, send request.", 0);
            expectProposals(engine, "Улучшение", "KAFKA-TEST", "Improvement", "New feature", 0);
            expectProposals(engine, "Другой проект", "HDFS-TEST", "Bug", "Broker fails.", 0);
            expectProposals(engine, "Описание null", "KAFKA-TEST", "Bug", null, 0);
        }
    }

    private void expectProposals(DroolsRuleEngine engine, String name, String key,
                                 String type, String description, int expected) {
        Issue issue = new Issue();
        issue.setKey(key);
        issue.setIssueType(new IssueType(type, false));
        issue.setDescription(description);
        List<String> proposals = engine.execute(issue);
        check(proposals.size() == expected,
                name + ": ожидалось " + expected + ", получено " + proposals.size());
        passed.add("PASS: " + name + " -> " + expected + " предложений");
    }

    private void checkMissingFields() throws IOException {
        var source = new ObjectMapper().readTree("{\"key\":\"KAFKA-1\",\"fields\":{}}");
        Issue issue = new JiraIssueMapper().fromJson(source);
        check(issue.getDescription().isEmpty(), "Описание должно заменяться пустой строкой");
        check(issue.getAssignee().getName().equals("<unknown>"), "Нужен неизвестный исполнитель");
        check(issue.getComments().isEmpty(), "Отсутствующие комментарии должны давать пустой список");
        check(issue.getCreationDate() == null, "Отсутствующая дата должна оставаться null");
        passed.add("PASS: отсутствующие поля Jira обрабатываются без выдуманных дат");
    }

    private void checkInvalidSnapshots() throws IOException {
        String issue = "{\"key\":\"KAFKA-1\",\"fields\":{}}";
        expectInvalidSnapshot("Пересечение выборок",
                "{\"resolved\":[" + issue + "],\"opened\":[" + issue + "]}");
        expectInvalidSnapshot("Нет массива opened", "{\"resolved\":[]}");
    }

    private void expectInvalidSnapshot(String name, String content) throws IOException {
        Path directory = Paths.get("build/test-data");
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, "snapshot-", ".json");
        try {
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
            try {
                SnapshotClient.read(file);
                throw new AssertionError("Ошибочный снимок принят: " + name);
            } catch (IOException expected) {
                passed.add("PASS: " + name + " отклонён");
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private void checkModelTraining() throws IOException {
        Path directory = Paths.get("build/test-data/models");
        Files.createDirectories(directory);
        Path dataset = directory.resolve("documents.txt");
        Files.write(dataset, List.of(
                "DOC_1|consumer subscription changes trigger group rebalance",
                "DOC_2|consumer group subscription assignment rebalance",
                "DOC_3|transaction coordinator loads transaction state",
                "DOC_4|broker transaction state recovery failure",
                "DOC_5|consumer logging task readiness processing"), StandardCharsets.UTF_8);

        VectorizerConfiguration vectorizer = new VectorizerConfiguration();
        vectorizer.setLanguage("English");
        vectorizer.setBatchSize(10);
        vectorizer.setIterations(1);
        vectorizer.setEpochs(1);
        vectorizer.setLayerSize(20);
        vectorizer.setLearningRate(0.025);
        vectorizer.setMinWordFrequency(1);
        vectorizer.setWindowSize(2);
        vectorizer.setTopResults(2);

        var issues = new IssuesProcessorConfiguration();
        issues.setDataSetFile(dataset.toString());
        issues.setVectorModelFile(directory.resolve("issues.zip").toString());
        var documentation = new DocumentationProcessorConfiguration();
        documentation.setDataSetFile(dataset.toString());
        documentation.setVectorModelFile(directory.resolve("documentation.zip").toString());
        var processors = new ProcessorsConfiguration();
        processors.setIssues(issues);
        processors.setDocumentation(documentation);
        Configuration configuration = new Configuration();
        configuration.setVectorizer(vectorizer);
        configuration.setProcessors(processors);

        ModelService models = new ModelService(configuration);
        models.prepareModels(true);
        try (ZipFile archive = new ZipFile(issues.getVectorModelFile())) {
            String labels = new String(archive.getInputStream(archive.getEntry("labels.txt"))
                    .readAllBytes(), StandardCharsets.UTF_8);
            check(labels.lines().count() == 5, "После обучения должны сохраняться пять меток");
        }
        models.prepareModels(false);
        passed.add("PASS: обучение двух моделей и повторная загрузка ZIP с пятью метками");

        Files.write(Paths.get(issues.getVectorModelFile()), new byte[] {1, 2, 3});
        try {
            models.prepareModels(false);
            throw new AssertionError("Повреждённая модель принята");
        } catch (IOException expected) {
            passed.add("PASS: повреждённая модель отклонена с сообщением об ошибке");
        }
    }
}
