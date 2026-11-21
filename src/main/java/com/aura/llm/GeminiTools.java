package com.aura.llm;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class GeminiTools {

    private GeminiTools() {
    }

    public static String runShell(Map<String, Object> args) {
        String command = (String) args.get("command");
        try {
            Process process = new ProcessBuilder("bash", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                return "Error: command timed out after 30 seconds";
            }

            String trimmed = output.strip();
            return trimmed.isEmpty() ? "(no output)" : trimmed;
        } catch (IOException | InterruptedException e) {
            return "Error running command: " + e.getMessage();
        }
    }

    public static String readFile(Map<String, Object> args) {
        String path = (String) args.get("path");
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    public static String writeFile(Map<String, Object> args) {
        String path = (String) args.get("path");
        String content = (String) args.get("content");
        try {
            Files.writeString(Path.of(path), content);
            return "Wrote " + content.getBytes(StandardCharsets.UTF_8).length + " bytes to " + path;
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    public static final Map<String, Function<Map<String, Object>, String>> TOOL_REGISTRY = Map.of(
            "run_shell", GeminiTools::runShell,
            "read_file", GeminiTools::readFile,
            "write_file", GeminiTools::writeFile
    );

    public static final Tool TOOL_DEFINITIONS = Tool.builder()
            .functionDeclarations(List.of(
                    FunctionDeclaration.builder()
                            .name("run_shell")
                            .description("Execute a shell command and return its output.")
                            .parameters(Schema.builder()
                                    .type("OBJECT")
                                    .properties(Map.of(
                                            "command", Schema.builder()
                                                    .type("STRING")
                                                    .description("Shell command to run")
                                                    .build()
                                    ))
                                    .required(List.of("command"))
                                    .build())
                            .build(),
                    FunctionDeclaration.builder()
                            .name("read_file")
                            .description("Read the contents of a file from the local filesystem.")
                            .parameters(Schema.builder()
                                    .type("OBJECT")
                                    .properties(Map.of(
                                            "path", Schema.builder()
                                                    .type("STRING")
                                                    .description("Absolute or relative file path")
                                                    .build()
                                    ))
                                    .required(List.of("path"))
                                    .build())
                            .build(),
                    FunctionDeclaration.builder()
                            .name("write_file")
                            .description("Write text content to a file on the local filesystem.")
                            .parameters(Schema.builder()
                                    .type("OBJECT")
                                    .properties(Map.of(
                                            "path", Schema.builder()
                                                    .type("STRING")
                                                    .description("File path to write to")
                                                    .build(),
                                            "content", Schema.builder()
                                                    .type("STRING")
                                                    .description("Content to write")
                                                    .build()
                                    ))
                                    .required(List.of("path", "content"))
                                    .build())
                            .build()
            ))
            .build();
}
