package app.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class NavigationAssistantService {
    private static final String GEMINI_API_KEY = "AIzaSyAXTO-ghKrRpTkUuxcHuPEe2VXlFN0HnxU";
    private static final String GEMINI_MODEL = "gemini-2.5-flash";
    private static final String GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final String DEFAULT_TENANT = "restaurant-a";

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/dashboard",
            "/inventory",
            "/expiry-alerts",
            "/recommendations",
            "/shopping-list",
            "/recipes/add",
            "/recipes/manage"
    );

    private final HttpClient httpClient;
    private final Gson gson;

    public NavigationAssistantService() {
        this(HttpClient.newHttpClient(), new Gson());
    }

    NavigationAssistantService(HttpClient httpClient, Gson gson) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
        this.gson = Objects.requireNonNull(gson, "gson is required");
    }

    public AssistantReply answer(String tenantId, String userMessage) {
        String safeTenant = normalizeTenant(tenantId);
        String safeMessage = userMessage == null ? "" : userMessage.trim();
        AssistantReply fallback = fallbackReply(safeTenant, safeMessage);

        String apiKey = configuredApiKey();
        if (apiKey.isBlank()) {
            return fallback;
        }

        try {
            AssistantReply aiReply = requestGemini(safeTenant, safeMessage, apiKey);
            return sanitizeReply(aiReply, safeTenant, fallback);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private AssistantReply requestGemini(String tenantId, String userMessage, String apiKey) {
        JsonObject requestPayload = buildGeminiPayload(tenantId, userMessage);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_ENDPOINT + GEMINI_MODEL + ":generateContent?key="
                        + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestPayload)))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to call Gemini endpoint", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini request was interrupted", ex);
        }

        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Gemini request failed with status " + response.statusCode());
        }

        JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
        String modelText = extractGeminiText(responseJson);
        return parseModelReply(modelText);
    }

    private JsonObject buildGeminiPayload(String tenantId, String userMessage) {
        JsonObject part = new JsonObject();
        part.addProperty("text", buildPrompt(tenantId, userMessage));

        JsonArray parts = new JsonArray();
        parts.add(part);

        JsonObject content = new JsonObject();
        content.add("parts", parts);

        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.2);
        generationConfig.addProperty("responseMimeType", "application/json");

        JsonObject payload = new JsonObject();
        payload.add("contents", contents);
        payload.add("generationConfig", generationConfig);
        return payload;
    }

    private String buildPrompt(String tenantId, String userMessage) {
        return "You are a navigation assistant for the Smart Kitchen Inventory web app.\\n"
                + "Answer with guidance only, not policy discussions.\\n"
                + "Return ONLY valid JSON with this exact schema:\\n"
                + "{\\n"
                + "  \\\"shortAnswer\\\": string,\\n"
                + "  \\\"steps\\\": [string],\\n"
                + "  \\\"actions\\\": [{\\\"label\\\": string, \\\"url\\\": string}],\\n"
                + "  \\\"quickTips\\\": [string]\\n"
                + "}\\n"
                + "Constraints:\\n"
                + "- shortAnswer: one plain-language sentence.\\n"
                + "- steps: 3 to 5 concrete steps.\\n"
                + "- actions: 1 to 3 one-click actions.\\n"
                + "- quickTips: 0 to 3 optional tips.\\n"
                + "- Allowed action paths only: /dashboard, /inventory, /expiry-alerts, /recommendations, /shopping-list, /recipes/add, /recipes/manage\\n"
                + "- Every action URL must include ?tenant=" + tenantId + "\\n"
                + "- Do not output markdown fences.\\n"
                + "User question: " + (userMessage.isBlank() ? "Help me navigate this app." : userMessage);
    }

    private AssistantReply parseModelReply(String rawModelText) {
        String cleaned = stripCodeFence(rawModelText);
        try {
            JsonObject json = gson.fromJson(cleaned, JsonObject.class);
            if (json == null) {
                throw new IllegalStateException("Gemini JSON payload is empty");
            }

            String shortAnswer = readString(json, "shortAnswer");
            List<String> steps = readStringArray(json.getAsJsonArray("steps"), 6);
            List<QuickAction> actions = readActionArray(json.getAsJsonArray("actions"));
            List<String> quickTips = readStringArray(json.getAsJsonArray("quickTips"), 4);
            return new AssistantReply(shortAnswer, steps, actions, quickTips);
        } catch (JsonSyntaxException ex) {
            throw new IllegalStateException("Gemini response is not valid JSON", ex);
        }
    }

    private String extractGeminiText(JsonObject responseJson) {
        if (responseJson == null) {
            throw new IllegalStateException("Gemini response is empty");
        }

        JsonArray candidates = responseJson.getAsJsonArray("candidates");
        if (candidates == null || candidates.size() == 0) {
            throw new IllegalStateException("Gemini response has no candidates");
        }

        JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
        JsonObject content = firstCandidate.getAsJsonObject("content");
        if (content == null) {
            throw new IllegalStateException("Gemini candidate has no content");
        }

        JsonArray parts = content.getAsJsonArray("parts");
        if (parts == null || parts.size() == 0) {
            throw new IllegalStateException("Gemini candidate has no parts");
        }

        JsonObject firstPart = parts.get(0).getAsJsonObject();
        JsonElement text = firstPart.get("text");
        if (text == null || text.isJsonNull()) {
            throw new IllegalStateException("Gemini part has no text");
        }

        return text.getAsString();
    }

    private AssistantReply sanitizeReply(AssistantReply aiReply, String tenantId, AssistantReply fallback) {
        if (aiReply == null) {
            return fallback;
        }

        String shortAnswer = sanitizeLine(aiReply.shortAnswer());
        List<String> steps = sanitizeLines(aiReply.steps(), 6);
        List<QuickAction> actions = sanitizeActions(aiReply.actions(), tenantId);
        List<String> tips = sanitizeLines(aiReply.quickTips(), 4);

        if (shortAnswer.isBlank() || steps.isEmpty() || actions.isEmpty()) {
            return fallback;
        }

        return new AssistantReply(shortAnswer, steps, actions, tips);
    }

    private AssistantReply fallbackReply(String tenantId, String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);

        if (containsAny(normalized, "add", "new item", "new ingredient", "add item", "add ingredient")
            && !containsAny(normalized, "recipe", "dish")) {
            return new AssistantReply(
                    "To add a new ingredient, use the Inventory page and submit the Add Ingredient form.",
                    List.of(
                            "Open Inventory.",
                            "Fill in Name, Quantity, and Unit.",
                            "Set Expiry Date and Low Stock Threshold.",
                            "Click Add Ingredient.",
                            "Check Current Inventory to confirm it was added."
                    ),
                    List.of(action("Open Inventory", "/inventory", tenantId)),
                    List.of(
                            "Use a positive quantity value.",
                            "Expiry Date is required.",
                            "Use the same ingredient naming style for clean reports."
                    )
            );
        }

        if (containsAny(normalized, "use ingredient", "mark used", "used", "consume", "discard", "throw away")) {
            return new AssistantReply(
                    "You can update usage or discard stock from the Current Inventory actions.",
                    List.of(
                            "Open Inventory.",
                            "Find the ingredient in Current Inventory.",
                            "Use the Use form and enter Qty used, then submit.",
                            "If spoiled, click Discard instead.",
                            "Refresh the table and confirm the quantity or state changed."
                    ),
                    List.of(action("Open Inventory", "/inventory", tenantId)),
                    List.of(
                            "Qty used must be greater than 0.",
                            "Discard is permanent for that stock record."
                    )
            );
        }

        if (containsAny(normalized, "shopping", "low stock", "reorder", "buy", "procurement")) {
            return new AssistantReply(
                    "Use Shopping List to see what needs reorder based on your low-stock thresholds.",
                    List.of(
                            "Open Shopping List.",
                            "Review ingredients listed at or below threshold.",
                            "Check the suggested reorder quantity column.",
                            "Optionally open Inventory to adjust thresholds for future runs.",
                            "Place your purchase order based on the list."
                    ),
                    List.of(
                            action("Open Shopping List", "/shopping-list", tenantId),
                            action("Adjust Inventory", "/inventory", tenantId)
                    ),
                    List.of(
                            "Threshold tuning improves reorder quality.",
                            "Review this list once per shift in busy kitchens."
                    )
            );
        }

        if (containsAny(normalized, "tenant", "restaurant", "switch", "change restaurant")) {
            return new AssistantReply(
                    "You can switch restaurants by changing the tenant ID from the dashboard.",
                    List.of(
                            "Open Dashboard.",
                            "Find the Switch Restaurant (Tenant) section.",
                            "Enter the tenant ID you want.",
                            "Click Switch.",
                            "Navigate to other pages and verify they show the same tenant."
                    ),
                    List.of(action("Open Dashboard", "/dashboard", tenantId)),
                    List.of(
                            "Tenant IDs are normalized to lowercase letters, numbers, underscore, and dash.",
                            "If the tenant is blank, the app falls back to restaurant-a."
                    )
            );
        }

        if (containsAny(normalized, "dish", "cook", "recipe", "log cooked", "recommend")) {
            return new AssistantReply(
                    "Use Dish Suggestions to log cooked dishes and track near-expiry rescue impact.",
                    List.of(
                            "Open Dish Suggestions.",
                            "Review the suggested dishes and rescue scores.",
                            "Pick a dish and click Log as Cooked.",
                            "Read the Cooking Impact Summary shown at the top.",
                            "Use Add New Recipe if you need a custom dish."
                    ),
                    List.of(
                            action("Open Dish Suggestions", "/recommendations", tenantId),
                            action("Add New Recipe", "/recipes/add", tenantId)
                    ),
                    List.of(
                            "Dishes with higher rescue score help reduce waste faster.",
                            "Check ingredient availability before service starts."
                    )
            );
        }

        return new AssistantReply(
                "I can guide you through any screen; start with the page closest to your task.",
                List.of(
                        "Open Dashboard for a quick system overview.",
                        "Go to Inventory for stock updates and add/use/discard actions.",
                        "Visit Expiry Alerts to review urgency and notifications.",
                        "Use Dish Suggestions for near-expiry cooking decisions.",
                        "Check Shopping List when planning purchases."
                ),
                List.of(
                        action("Open Dashboard", "/dashboard", tenantId),
                        action("Open Inventory", "/inventory", tenantId)
                ),
                List.of(
                        "Ask in plain language, like: How do I add a new item?",
                        "Mention your goal and I will return steps and quick actions."
                )
        );
    }

    private List<String> readStringArray(JsonArray array, int maxItems) {
        if (array == null || array.size() == 0 || maxItems < 1) {
            return List.of();
        }

        List<String> output = new ArrayList<>();
        for (JsonElement item : array) {
            if (!item.isJsonPrimitive()) {
                continue;
            }

            String value = sanitizeLine(item.getAsString());
            if (!value.isBlank()) {
                output.add(value);
            }

            if (output.size() >= maxItems) {
                break;
            }
        }

        return output;
    }

    private List<QuickAction> readActionArray(JsonArray array) {
        if (array == null || array.size() == 0) {
            return List.of();
        }

        List<QuickAction> output = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject actionJson = element.getAsJsonObject();
            output.add(new QuickAction(
                    readString(actionJson, "label"),
                    readString(actionJson, "url")
            ));
        }
        return output;
    }

    private String readString(JsonObject json, String key) {
        if (json == null || key == null || key.isBlank()) {
            return "";
        }

        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull()) {
            return "";
        }

        if (!value.isJsonPrimitive()) {
            return "";
        }

        return sanitizeLine(value.getAsString());
    }

    private List<String> sanitizeLines(List<String> source, int maxItems) {
        if (source == null || source.isEmpty() || maxItems < 1) {
            return List.of();
        }

        List<String> cleaned = new ArrayList<>();
        for (String line : source) {
            String value = sanitizeLine(line);
            if (!value.isBlank()) {
                cleaned.add(value);
            }

            if (cleaned.size() >= maxItems) {
                break;
            }
        }
        return cleaned;
    }

    private List<QuickAction> sanitizeActions(List<QuickAction> rawActions, String tenantId) {
        if (rawActions == null || rawActions.isEmpty()) {
            return List.of(action("Open Dashboard", "/dashboard", tenantId));
        }

        Set<String> seen = new LinkedHashSet<>();
        List<QuickAction> cleaned = new ArrayList<>();

        for (QuickAction action : rawActions) {
            if (action == null) {
                continue;
            }

            String label = sanitizeLine(action.label());
            String url = sanitizeActionUrl(action.url(), tenantId);
            if (label.isBlank() || url.isBlank()) {
                continue;
            }

            String signature = label + "|" + url;
            if (seen.add(signature)) {
                cleaned.add(new QuickAction(label, url));
            }

            if (cleaned.size() >= 3) {
                break;
            }
        }

        if (cleaned.isEmpty()) {
            cleaned.add(action("Open Dashboard", "/dashboard", tenantId));
        }
        return cleaned;
    }

    private String sanitizeActionUrl(String rawUrl, String tenantId) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }

        String candidate = rawUrl.trim();
        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            return "";
        }

        int fragmentIndex = candidate.indexOf('#');
        if (fragmentIndex >= 0) {
            candidate = candidate.substring(0, fragmentIndex);
        }

        String path = candidate;
        int queryIndex = candidate.indexOf('?');
        if (queryIndex >= 0) {
            path = candidate.substring(0, queryIndex);
        }

        if (!ALLOWED_PATHS.contains(path)) {
            return "";
        }

        return path + "?tenant=" + urlEncode(tenantId);
    }

    private QuickAction action(String label, String path, String tenantId) {
        return new QuickAction(label, path + "?tenant=" + urlEncode(tenantId));
    }

    private boolean containsAny(String text, String... terms) {
        if (text == null || text.isBlank() || terms == null) {
            return false;
        }

        for (String term : terms) {
            if (term != null && !term.isBlank() && text.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String sanitizeLine(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String stripCodeFence(String modelText) {
        if (modelText == null) {
            return "";
        }

        String trimmed = modelText.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstLineBreak = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLineBreak < 0 || lastFence <= firstLineBreak) {
            return trimmed;
        }

        return trimmed.substring(firstLineBreak + 1, lastFence).trim();
    }

    private String configuredApiKey() {
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        return GEMINI_API_KEY;
    }

    private String normalizeTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return DEFAULT_TENANT;
        }

        String normalized = tenantId.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return normalized.isBlank() ? DEFAULT_TENANT : normalized;
    }

    private String urlEncode(String input) {
        return URLEncoder.encode(input == null ? "" : input, StandardCharsets.UTF_8);
    }

    public record AssistantReply(
            String shortAnswer,
            List<String> steps,
            List<QuickAction> actions,
            List<String> quickTips
    ) {
        public AssistantReply {
            shortAnswer = shortAnswer == null ? "" : shortAnswer;
            steps = steps == null ? List.of() : List.copyOf(steps);
            actions = actions == null ? List.of() : List.copyOf(actions);
            quickTips = quickTips == null ? List.of() : List.copyOf(quickTips);
        }
    }

    public record QuickAction(String label, String url) {
        public QuickAction {
            label = label == null ? "" : label;
            url = url == null ? "" : url;
        }
    }
}