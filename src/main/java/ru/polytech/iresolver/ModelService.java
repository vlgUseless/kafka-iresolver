package ru.polytech.iresolver;

import com.koval.resolver.common.api.configuration.Configuration;
import com.koval.resolver.common.api.vectorization.VectorModel;
import com.koval.resolver.common.api.vectorization.VectorModelCreator;
import com.koval.resolver.common.api.vectorization.VectorModelSerializer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipFile;

/** Обучает оригинальные модели iResolver или использует сохранённые ZIP-модели. */
public final class ModelService {
    private final Configuration configuration;

    public ModelService(Configuration configuration) {
        this.configuration = configuration;
    }

    public void prepareModels(boolean retrain) throws IOException {
        var issues = configuration.getProcessors().getIssues();
        var documentation = configuration.getProcessors().getDocumentation();
        prepareModel(issues.getDataSetFile(), issues.getVectorModelFile(), retrain);
        prepareModel(documentation.getDataSetFile(), documentation.getVectorModelFile(), retrain);
    }

    private void prepareModel(String datasetName, String modelName, boolean retrain) throws IOException {
        Path modelFile = Paths.get(modelName);
        if (!retrain && Files.exists(modelFile)) {
            verifyArchive(modelFile);
            System.out.println("Используется сохранённая модель: " + modelFile);
            return;
        }

        Path dataset = Paths.get(datasetName);
        if (!Files.isRegularFile(dataset)) {
            throw new IOException("Нет обучающего набора: " + dataset);
        }
        Files.createDirectories(modelFile.toAbsolutePath().getParent());
        System.out.println("Обучение модели по набору: " + dataset);
        VectorModel model = new VectorModelCreator(configuration.getVectorizer())
                .createFromFile(dataset.toFile());

        // Сначала записываем и проверяем временный файл, сохраняя прежнюю модель при ошибке.
        Path temporaryFile = modelFile.resolveSibling(modelFile.getFileName() + ".tmp");
        try {
            new VectorModelSerializer(configuration.getVectorizer())
                    .serialize(model, temporaryFile.toString());
            verifyArchive(temporaryFile);
            Files.move(temporaryFile, modelFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private void verifyArchive(Path path) throws IOException {
        try (ZipFile archive = new ZipFile(path.toFile())) {
            if (archive.getEntry("labels.txt") == null || archive.getEntry("syn0.txt") == null) {
                throw new IOException("Неполная модель Doc2Vec: " + path + ". Запустите с -Train.");
            }
        } catch (java.util.zip.ZipException exception) {
            throw new IOException("Повреждена модель " + path + ". Запустите с -Train.", exception);
        }
    }
}
