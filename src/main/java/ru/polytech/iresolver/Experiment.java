package ru.polytech.iresolver;

import com.koval.resolver.common.api.configuration.Configuration;
import com.koval.resolver.common.api.model.result.IssueAnalysingResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.yaml.snakeyaml.Yaml;

/** Точка входа: подготовка моделей, анализ снимка Jira и сохранение отчётов. */
public final class Experiment {
    private Experiment() {
    }

    public static void main(String[] arguments) throws Exception {
        boolean retrain = Arrays.asList(arguments).contains("--train");
        Configuration configuration = readConfiguration(Paths.get("config/configuration.yml"));
        SnapshotClient snapshot = SnapshotClient.read(Paths.get("data/snapshot.json"));

        System.out.printf("Загружено %d исправленных и %d открытых задач.%n",
                snapshot.getResolvedCount(), snapshot.getOpenIssues().size());

        new ModelService(configuration).prepareModels(retrain);
        List<IssueAnalysingResult> results =
                new AnalysisService(configuration, snapshot).analyzeOpenIssues();
        new ResultWriter(configuration).write(results);

        System.out.printf("Готово: обработано %d задач. Отчёт: %s%n",
                results.size(), configuration.getReporters().getHtml().getOutputFile());
    }

    private static Configuration readConfiguration(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return new Yaml().loadAs(input, Configuration.class);
        }
    }
}
