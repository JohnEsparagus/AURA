package com.aura.llm;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final String MODEL = "gemini-3.8-flash";
    private static final int MAX_ITERATIONS = 10;

    private final Client client;
    private final List<Content> history = new ArrayList<>();

    public GeminiService() {
        this.client = new Client();
    }

    public synchronized String generate(String prompt) {
        history.add(Content.builder().role("user").parts(List.of(Part.fromText(prompt))).build());
        return runLoop();
    }

    public synchronized void reset() {
        history.clear();
    }

    private String runLoop() {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .tools(List.of(GeminiTools.TOOL_DEFINITIONS))
                .build();

        GenerateContentResponse response = null;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            response = client.models.generateContent(MODEL, history, config);

            Content content = response.candidates().get().getFirst().content().orElseThrow();
            history.add(content);

            List<FunctionCall> functionCalls = response.functionCalls();
            if (functionCalls == null || functionCalls.isEmpty()) {
                return response.text();
            }

            for (FunctionCall call : functionCalls) {
                handleToolCall(call);
            }
        }

        return response != null ? response.text() : "";
    }

    private void handleToolCall(FunctionCall call) {
        String name = call.name().orElse("");
        Map<String, Object> args = call.args().orElse(Map.of());

        var fn = GeminiTools.TOOL_REGISTRY.get(name);
        String result = fn != null ? fn.apply(args) : "Unknown tool: " + name;

        Part responsePart = Part.fromFunctionResponse(name, Map.of("result", result));
        history.add(Content.builder().role("user").parts(List.of(responsePart)).build());
    }
}
