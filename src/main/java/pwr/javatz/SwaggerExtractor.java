package pwr.javatz;

import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SwaggerExtractor {
    public static void main(String[] args) {
        SwaggerExtractor extractor = new SwaggerExtractor("./repos.txt", "./files.txt");
    }

    private Path swaggerFile = null;

    public SwaggerExtractor(String REPOS_LIST_FILE, String FILES_LIST_FILE) {
        var repoUrls = readFile(REPOS_LIST_FILE);
        var fileNames = readFile(FILES_LIST_FILE);
        int i = 0;

        for (String repoUrl : repoUrls) {
            processRepository(repoUrl, i++, fileNames);
        }
    }

    private List<String> readFile(String filePath) {
        try {
            return Files.readAllLines(Paths.get(filePath));
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private void processRepository(String repoUrl, int repoID, List<String> fileNames) {
        try {
            var localRepo = Files.createDirectory(Path.of("./repo" + repoID)).toFile();
            runCommand(localRepo, "git", "clone", repoUrl, localRepo.getAbsolutePath());

            swaggerFile = findSwaggerFile(localRepo.toPath(), fileNames);
            if (swaggerFile == null) {
                FileUtils.deleteDirectory(localRepo);
                return;
            }

            List<String> commits = getCommitHistory(localRepo);

            for (String commit : commits) {
                runCommand(localRepo, "git", "checkout", commit, localRepo.getAbsolutePath());
                swaggerFile = findSwaggerFile(localRepo.toPath(), fileNames);
                saveSwaggerSpec(localRepo.getName(), commit, swaggerFile);
            }
            FileUtils.deleteDirectory(localRepo);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void runCommand(File workingDir, String... command) throws IOException, InterruptedException {
        var builder = new ProcessBuilder(command);
        builder.directory(workingDir);
        builder.redirectErrorStream(true);
        var process = builder.start();
        process.waitFor();
    }

    private Path findSwaggerFile(Path repoPath, List<String> fileNames) throws IOException {
        try (var paths = Files.walk(repoPath)) {
            var swaggerFiles = paths
                    .filter(p -> fileNames.contains(p.getFileName().toString().toLowerCase()))
                    .toList();

            return swaggerFiles.isEmpty() ? null : swaggerFiles.get(0);
        }
    }

    private List<String> getCommitHistory(File repo) throws IOException {
        ProcessBuilder builder = new ProcessBuilder("git", "log", "--reverse", "--pretty=format:%H", "--find-renames=100%", "--follow", "--", swaggerFile.toAbsolutePath().toString());
        builder.directory(repo);
        Process process = builder.start();
        return new BufferedReader(new InputStreamReader(process.getInputStream())).lines().collect(Collectors.toList());
    }

    private void saveSwaggerSpec(String repoName, String commitHash, Path swaggerFile) throws IOException {
        Path outputDir = Paths.get("API Specki", repoName);
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("swagger_" + commitHash + ".json");
        Files.copy(swaggerFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
    }
}
