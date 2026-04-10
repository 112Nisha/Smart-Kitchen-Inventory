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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class NavigationAssistantService {
    private static final String GEMINI_MODEL = "gemini-2.5-flash";
    private static final String GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final String DEFAULT_TENANT = "restaurant-a";
    private static final Duration CONTEXT_CACHE_TTL = Duration.ofMinutes(18);
    private static final int MAX_CONTEXT_FILES = 72;
    private static final int MAX_SUMMARY_SCAN_CHARS = 2200;
    private static final int MAX_FALLBACK_EXCERPT_CHARS = 700;
    private static final int MAX_SUMMARY_CHARS = 220;
    private static final int MAX_SUMMARY_SIGNALS = 8;
    private static final int MAX_CONTEXT_PAYLOAD_CHARS = 6500;
    private static final int MAX_RETRIEVED_SNIPPETS = 3;
    private static final int MAX_QUERY_TERMS = 8;
    private static final Pattern RESTRICTED_INTENT_PATTERN = Pattern.compile(
            "\\b(database|db|schema|sql|sqlite|jdbc|password|passwd|secret|token|api\\s*key|source\\s*code|codebase|repository|java\\s*class|method\\s*implementation|file\\s*path)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RESTRICTED_OUTPUT_PATTERN = Pattern.compile(
            "\\b(database|schema|sql|sqlite|jdbc|password|passwd|secret|token|api\\s*key|source\\s*code|codebase|repository|stack\\s*trace)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ROUTE_SIGNAL_PATTERN = Pattern.compile(
            "(?i)(?:@WebServlet\\(|url-pattern>|href\\s*=\\s*\"|action\\s*=\\s*\"|fetch\\(\\s*\"|fetch\\(\\s*')(/[-a-z0-9_./]+)");
    private static final Pattern TITLE_SIGNAL_PATTERN = Pattern.compile("(?i)<title[^>]*>([^<]{1,100})</title>");
    private static final Pattern HEADING_SIGNAL_PATTERN = Pattern.compile("(?i)<h[1-3][^>]*>([^<]{1,100})</h[1-3]>");
    private static final Pattern BUTTON_SIGNAL_PATTERN = Pattern.compile("(?i)<button[^>]*>([^<]{1,80})</button>");
    private static final Pattern LABEL_SIGNAL_PATTERN = Pattern.compile("(?i)<label[^>]*>([^<]{1,80})</label>");
    private static final Pattern CONTROLLER_ACTION_PATTERN = Pattern.compile("(?i)\\b(add|update|delete|remove|create|list|view|manage|switch)\\b");
    private static final Pattern SHORT_ANSWER_FIELD_PATTERN = Pattern.compile(
            "\\\"shortAnswer\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\\\"])*)\\\"",
            Pattern.CASE_INSENSITIVE
        );
    private static final List<ShortcutRule> SHORTCUT_RULES = List.of(
            new ShortcutRule("/dashboard", "dashboard", "Dashboard"),
            new ShortcutRule("/inventory", "inventory", "Inventory"),
            new ShortcutRule("/expiry-alerts", "expiry alerts", "Expiry Alerts"),
            new ShortcutRule("/shopping-list", "shopping list", "Shopping List"),
            new ShortcutRule("/recommendations", "recommendations", "Dish Suggestions"),
            new ShortcutRule("/recipes/add", "add recipe", "Add Recipe"),
            new ShortcutRule("/recipes/manage", "manage recipes", "Manage Recipes")
    );

    private final HttpClient httpClient;
    private final Gson gson;
    private final Path projectRootOverride;
    private final Object contextLock = new Object();
    private volatile ContextSnapshot cachedContext;

    public NavigationAssistantService() {
        this(HttpClient.newHttpClient(), new Gson(), null);
    }

    NavigationAssistantService(HttpClient httpClient, Gson gson) {
        this(httpClient, gson, null);
    }

    NavigationAssistantService(HttpClient httpClient, Gson gson, Path projectRootOverride) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
        this.gson = Objects.requireNonNull(gson, "gson is required");
        this.projectRootOverride = projectRootOverride;
    }

    public AssistantReply answer(String tenantId, String userMessage) {
        String safeTenant = normalizeTenant(tenantId);
        String safeMessage = userMessage == null ? "" : userMessage.trim();
        AssistantReply fallback = unavailableReply(null);

        if (isRestrictedIntent(safeMessage)) {
            return restrictedContentReply(safeTenant);
        }

        AssistantReply shortcutReply = tryShortcutReply(safeTenant, safeMessage);
        if (shortcutReply != null) {
            return shortcutReply;
        }

        String apiKey = configuredApiKey();
        if (apiKey.isBlank()) {
            logFailure(new IllegalStateException("GEMINI_API_KEY is blank"), safeTenant, safeMessage);
            return fallback;
        }

        try {
            AssistantReply aiReply = requestGemini(safeTenant, safeMessage, apiKey);
            return sanitizeReply(aiReply, safeTenant, fallback);
        } catch (RuntimeException ex) {
            logFailure(ex, safeTenant, safeMessage);
            return unavailableReply(ex);
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
            throw new IllegalStateException("Gemini request failed with status " + response.statusCode()
                    + " body=" + compactErrorBody(response.body(), 420));
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
        String question = userMessage.isBlank()
            ? "Give me an overview of this website and where I should start."
            : userMessage;

        return "You are a navigation assistant for the Smart Kitchen Inventory web app.\\n"
            + "Use the retrieved feature summaries below to answer questions about this website.\\n"
            + "Do not invent features or routes that are not present in the context.\\n"
            + "If information is missing in the provided context, say so clearly.\\n"
            + "Safety rules:\\n"
            + "- Only provide information visible to end users in the website interface.\\n"
            + "- Never reveal databases, SQL/schema, passwords, secrets, API keys/tokens, or source-code internals.\\n"
            + "- If asked for internal details, refuse briefly and redirect to visible website actions.\\n"
                + "Return ONLY valid JSON with this exact schema:\\n"
                + "{\\n"
                + "  \\\"shortAnswer\\\": string,\\n"
                + "  \\\"steps\\\": [string],\\n"
                + "  \\\"actions\\\": [{\\\"label\\\": string, \\\"url\\\": string}],\\n"
                + "  \\\"quickTips\\\": [string]\\n"
                + "}\\n"
                + "Constraints:\\n"
                + "- shortAnswer: one plain-language sentence.\\n"
            + "- steps: 1 to 6 concrete steps.\\n"
            + "- actions: 0 to 4 one-click actions.\\n"
                + "- quickTips: 0 to 3 optional tips.\\n"
            + "- Every action URL must be an in-app relative path that starts with /.\\n"
            + "- Every action URL should include ?tenant=" + tenantId + "\\n"
                + "- Do not output markdown fences.\\n"
            + "Tenant: " + tenantId + "\\n"
            + "User question: " + question + "\\n"
                + "Retrieved feature context:\\n"
                + codebaseContextSnapshot(question);
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
            String recoveredShortAnswer = recoverShortAnswer(cleaned);
            if (!recoveredShortAnswer.isBlank()) {
                return new AssistantReply(recoveredShortAnswer, List.of(), List.of(), List.of());
            }

            String plainText = sanitizeLine(cleaned);
            boolean looksLikeBrokenJson = plainText.startsWith("{") || plainText.contains("\"shortAnswer\"");
            if (!plainText.isBlank() && !looksLikeBrokenJson) {
                return new AssistantReply(plainText, List.of(), List.of(), List.of());
            }

            throw new IllegalStateException("Gemini response is not valid JSON", ex);
        }
    }

    private String recoverShortAnswer(String rawModelText) {
        if (rawModelText == null || rawModelText.isBlank()) {
            return "";
        }

        Matcher matcher = SHORT_ANSWER_FIELD_PATTERN.matcher(rawModelText);
        if (!matcher.find()) {
            return "";
        }

        String escapedValue = matcher.group(1);
        try {
            return sanitizeLine(gson.fromJson("\"" + escapedValue + "\"", String.class));
        } catch (JsonSyntaxException ignored) {
            return sanitizeLine(escapedValue
                    .replace("\\n", " ")
                    .replace("\\r", " ")
                    .replace("\\t", " ")
                    .replace("\\\"", "\""));
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
        List<String> steps = sanitizeLines(aiReply.steps(), 8);
        List<QuickAction> actions = sanitizeActions(aiReply.actions(), tenantId);
        List<String> tips = sanitizeLines(aiReply.quickTips(), 4);

        if (shortAnswer.isBlank()) {
            return fallback;
        }

        AssistantReply cleaned = new AssistantReply(shortAnswer, steps, actions, tips);
        if (containsRestrictedOutput(cleaned)) {
            return restrictedContentReply(tenantId);
        }
        return cleaned;
    }

    private AssistantReply unavailableReply(RuntimeException failure) {
        return new AssistantReply(
            "Guide is temporarily unavailable. Please check server logs for the exact error.",
            List.of(),
            List.of(),
            List.of()
        );
    }

    private void logFailure(RuntimeException failure, String tenantId, String userMessage) {
        if (failure == null) {
            return;
        }

        String tenant = sanitizeLine(tenantId);
        String message = sanitizeLine(userMessage);
        System.err.println("[NavigationAssistantService] failure tenant=" + tenant + " userMessage=" + message);
        failure.printStackTrace(System.err);
    }

    private AssistantReply restrictedContentReply(String tenantId) {
        return new AssistantReply(
                "I can only help with actions that users can perform in the website interface.",
                List.of(
                        "Ask about tasks like adding ingredients, checking expiry alerts, dish suggestions, or shopping list usage.",
                        "Use the page actions below to continue in the app."
                ),
                List.of(
                        new QuickAction("Open Dashboard", "/dashboard?tenant=" + urlEncode(tenantId)),
                        new QuickAction("Open Inventory", "/inventory?tenant=" + urlEncode(tenantId))
                ),
                List.of()
        );
    }

    private boolean isRestrictedIntent(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return RESTRICTED_INTENT_PATTERN.matcher(message).find();
    }

    private boolean containsRestrictedOutput(AssistantReply reply) {
        if (reply == null) {
            return false;
        }

        StringBuilder combined = new StringBuilder();
        combined.append(reply.shortAnswer()).append(' ');
        for (String step : reply.steps()) {
            combined.append(step).append(' ');
        }
        for (QuickAction action : reply.actions()) {
            combined.append(action.label()).append(' ').append(action.url()).append(' ');
        }
        for (String tip : reply.quickTips()) {
            combined.append(tip).append(' ');
        }

        return RESTRICTED_OUTPUT_PATTERN.matcher(combined.toString()).find();
    }

    private AssistantReply tryShortcutReply(String tenantId, String userMessage) {
        String normalized = normalizeShortcutQuery(userMessage);
        if (normalized.isBlank()) {
            return null;
        }

        for (ShortcutRule rule : SHORTCUT_RULES) {
            if (isShortcutMatch(normalized, rule)) {
                String actionUrl = rule.path() + "?tenant=" + urlEncode(tenantId);
                return new AssistantReply(
                        "Use the " + rule.label() + " page for this.",
                        List.of("Open " + rule.label() + " using the action below."),
                        List.of(new QuickAction("Open " + rule.label(), actionUrl)),
                        List.of()
                );
            }
        }

        return null;
    }

    private boolean isShortcutMatch(String normalizedQuestion, ShortcutRule rule) {
        if (normalizedQuestion.equals(rule.path()) || normalizedQuestion.equals(rule.path().substring(1))) {
            return true;
        }

        String feature = rule.feature();
        return normalizedQuestion.equals(feature)
                || normalizedQuestion.equals(feature + " page")
                || normalizedQuestion.equals("open " + feature)
                || normalizedQuestion.equals("go to " + feature);
    }

    private String normalizeShortcutQuery(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "";
        }

        return userMessage
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
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
            return List.of();
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

            if (cleaned.size() >= 4) {
                break;
            }
        }
        return cleaned;
    }

    private String sanitizeActionUrl(String rawUrl, String tenantId) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }

        String candidate = rawUrl.trim();
        if (candidate.startsWith("http://") || candidate.startsWith("https://") || candidate.contains("://")) {
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

        path = path.trim();
        if (!path.startsWith("/") || path.contains("//") || path.contains("..")) {
            return "";
        }

        path = normalizeRouteAlias(path);

        return path + "?tenant=" + urlEncode(tenantId);
    }

    private String normalizeRouteAlias(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }

        return switch (path) {
            case "/dish-recommendations", "/dish-suggestions" -> "/recommendations";
            default -> path;
        };
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
        return "";
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

    private String codebaseContextSnapshot(String question) {
        ContextSnapshot current = cachedContext;
        Instant now = Instant.now();

        if (current != null && now.isBefore(current.generatedAt().plus(CONTEXT_CACHE_TTL))) {
            return relevantFeatureContext(current.entries(), question);
        }

        synchronized (contextLock) {
            ContextSnapshot refreshed = cachedContext;
            if (refreshed != null && now.isBefore(refreshed.generatedAt().plus(CONTEXT_CACHE_TTL))) {
                return relevantFeatureContext(refreshed.entries(), question);
            }

            List<IndexedContextEntry> entries = buildCodebaseContext();
            ContextSnapshot snapshot = new ContextSnapshot(entries, Instant.now());
            cachedContext = snapshot;
            return relevantFeatureContext(snapshot.entries(), question);
        }
    }

    private String relevantFeatureContext(List<IndexedContextEntry> entries, String question) {
        if (entries == null || entries.isEmpty()) {
            return "No readable source files were indexed.";
        }

        Set<String> terms = extractQueryTerms(question);
        List<ScoredContextEntry> selected = topRelevantEntries(entries, terms);
        boolean lowConfidence = isLowSummaryConfidence(selected, terms);

        StringBuilder context = new StringBuilder();
        context.append("Indexed files: ").append(entries.size()).append('\n');
        context.append("Retrieved summaries: ").append(selected.size()).append('\n');
        context.append("Context mode: ").append(lowConfidence ? "fallback-snippets" : "feature-summaries").append('\n');

        for (ScoredContextEntry item : selected) {
            if (context.length() >= MAX_CONTEXT_PAYLOAD_CHARS) {
                break;
            }

            IndexedContextEntry entry = item.entry();
            if (lowConfidence) {
                context.append("\n[FALLBACK_SNIPPET] ").append(entry.relativePath()).append('\n');
                context.append(entry.fallbackExcerpt()).append('\n');
            } else {
                context.append("\n[FEATURE_SUMMARY] ")
                        .append(entry.relativePath())
                        .append(" -> ")
                        .append(entry.featureSummary())
                        .append('\n');
            }
        }

        if (context.length() > MAX_CONTEXT_PAYLOAD_CHARS) {
            return context.substring(0, MAX_CONTEXT_PAYLOAD_CHARS);
        }
        return context.toString();
    }

    private List<ScoredContextEntry> topRelevantEntries(List<IndexedContextEntry> entries, Set<String> terms) {
        if (terms.isEmpty()) {
            return entries.stream()
                    .limit(MAX_RETRIEVED_SNIPPETS)
                    .map(entry -> new ScoredContextEntry(entry, 0))
                    .toList();
        }

        List<ScoredContextEntry> scored = new ArrayList<>();
        for (IndexedContextEntry entry : entries) {
            int score = scoreEntry(entry, terms);
            if (score > 0) {
                scored.add(new ScoredContextEntry(entry, score));
            }
        }

        if (scored.isEmpty()) {
            return entries.stream()
                    .limit(MAX_RETRIEVED_SNIPPETS)
                    .map(entry -> new ScoredContextEntry(entry, 0))
                    .toList();
        }

        scored.sort(Comparator.comparingInt(ScoredContextEntry::score)
                .reversed()
                .thenComparing(item -> item.entry().relativePath()));

        List<ScoredContextEntry> selected = new ArrayList<>();
        for (ScoredContextEntry item : scored) {
            selected.add(item);
            if (selected.size() >= MAX_RETRIEVED_SNIPPETS) {
                break;
            }
        }

        return selected;
    }

    private boolean isLowSummaryConfidence(List<ScoredContextEntry> selected, Set<String> terms) {
        if (terms.isEmpty()) {
            return false;
        }

        if (selected == null || selected.isEmpty()) {
            return true;
        }

        return selected.get(0).score() < 4;
    }

    private int scoreEntry(IndexedContextEntry entry, Set<String> terms) {
        int score = 0;
        String path = entry.relativePath().toLowerCase(Locale.ROOT);
        String search = entry.searchText();

        for (String term : terms) {
            if (path.contains(term)) {
                score += 4;
            }
            if (search.contains(term)) {
                score += 2;
            }
        }

        return score;
    }

    private Set<String> extractQueryTerms(String question) {
        if (question == null || question.isBlank()) {
            return Set.of();
        }

        String normalized = question
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();

        if (normalized.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() < 3) {
                continue;
            }

            terms.add(token);
            if (terms.size() >= MAX_QUERY_TERMS) {
                break;
            }
        }

        return terms;
    }

    private List<IndexedContextEntry> buildCodebaseContext() {
        Path root = locateProjectRoot();
        List<Path> files = collectContextFiles(root);

        List<IndexedContextEntry> entries = new ArrayList<>();

        int appendedFiles = 0;
        for (Path file : files) {
            if (appendedFiles >= MAX_CONTEXT_FILES) {
                break;
            }

            String scannedContent = readFileExcerpt(file, MAX_SUMMARY_SCAN_CHARS);
            if (scannedContent.isBlank()) {
                continue;
            }

            String relative = toUnixPath(root.relativize(file).toString());
            String summary = extractFeatureSummary(relative, scannedContent);
            String fallbackExcerpt = fallbackExcerpt(scannedContent, MAX_FALLBACK_EXCERPT_CHARS);
            String searchText = (relative + "\n" + summary + "\n" + scannedContent).toLowerCase(Locale.ROOT);
            entries.add(new IndexedContextEntry(relative, summary, fallbackExcerpt, searchText));
            appendedFiles++;
        }

        return entries;
    }

    private String extractFeatureSummary(String relativePath, String content) {
        LinkedHashSet<String> signals = new LinkedHashSet<>();

        addRouteSignals(content, signals, 4);
        addTextSignals(TITLE_SIGNAL_PATTERN, "title", content, signals, 2);
        addTextSignals(HEADING_SIGNAL_PATTERN, "heading", content, signals, 3);
        addTextSignals(BUTTON_SIGNAL_PATTERN, "button", content, signals, 3);
        addTextSignals(LABEL_SIGNAL_PATTERN, "label", content, signals, 3);
        addControllerActionSignals(content, signals, 3);

        if (signals.isEmpty()) {
            String fileName = relativePath;
            int slashIndex = fileName.lastIndexOf('/');
            if (slashIndex >= 0 && slashIndex + 1 < fileName.length()) {
                fileName = fileName.substring(slashIndex + 1);
            }
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                fileName = fileName.substring(0, dotIndex);
            }
            signals.add(fileName.replace('-', ' ').replace('_', ' '));
        }

        StringBuilder summary = new StringBuilder();
        int added = 0;
        for (String signal : signals) {
            if (signal.isBlank()) {
                continue;
            }
            if (added > 0) {
                summary.append(", ");
            }
            summary.append(signal);
            added++;
            if (added >= MAX_SUMMARY_SIGNALS || summary.length() >= MAX_SUMMARY_CHARS) {
                break;
            }
        }

        return truncateSummary(summary.toString(), MAX_SUMMARY_CHARS);
    }

    private void addRouteSignals(String content, Set<String> signals, int maxItems) {
        if (content == null || content.isBlank() || maxItems < 1) {
            return;
        }

        Matcher matcher = ROUTE_SIGNAL_PATTERN.matcher(content);
        int added = 0;
        while (matcher.find() && added < maxItems) {
            String route = sanitizeLine(matcher.group(1));
            if (route.isBlank() || !route.startsWith("/") || route.startsWith("/assets")) {
                continue;
            }
            int queryIndex = route.indexOf('?');
            if (queryIndex >= 0) {
                route = route.substring(0, queryIndex);
            }

            if (signals.add("route " + route)) {
                added++;
            }
        }
    }

    private void addTextSignals(
            Pattern pattern,
            String prefix,
            String content,
            Set<String> signals,
            int maxItems
    ) {
        if (pattern == null || prefix == null || content == null || content.isBlank() || maxItems < 1) {
            return;
        }

        Matcher matcher = pattern.matcher(content);
        int added = 0;
        while (matcher.find() && added < maxItems) {
            String normalized = normalizeFeatureSignal(matcher.group(1));
            if (normalized.isBlank()) {
                continue;
            }

            if (signals.add(prefix + " " + normalized)) {
                added++;
            }
        }
    }

    private void addControllerActionSignals(String content, Set<String> signals, int maxItems) {
        if (content == null || content.isBlank() || maxItems < 1) {
            return;
        }

        Matcher matcher = CONTROLLER_ACTION_PATTERN.matcher(content);
        int added = 0;
        while (matcher.find() && added < maxItems) {
            String action = normalizeFeatureSignal(matcher.group(1));
            if (action.isBlank()) {
                continue;
            }

            if (signals.add("action " + action)) {
                added++;
            }
        }
    }

    private String normalizeFeatureSignal(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String cleaned = sanitizeLine(raw)
                .replaceAll("(?i)&nbsp;", " ")
                .replaceAll("[\"'`]", "")
                .trim();

        if (cleaned.length() > 60) {
            cleaned = cleaned.substring(0, 60).trim();
        }

        return cleaned;
    }

    private String truncateSummary(String summary, int maxChars) {
        if (summary == null || summary.isBlank() || maxChars < 1) {
            return "";
        }
        if (summary.length() <= maxChars) {
            return summary;
        }
        return summary.substring(0, maxChars).trim();
    }

    private String fallbackExcerpt(String content, int maxChars) {
        if (content == null || content.isBlank() || maxChars < 1) {
            return "";
        }
        if (content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars).trim() + "\n...[truncated]";
    }

    private Path locateProjectRoot() {
        if (projectRootOverride != null) {
            return projectRootOverride.toAbsolutePath().normalize();
        }

        Path current = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && current != null; i++) {
            if (Files.exists(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("src"))) {
                return current;
            }
            current = current.getParent();
        }

        return Paths.get("").toAbsolutePath().normalize();
    }

    private List<Path> collectContextFiles(Path root) {
        List<Path> files = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) {
            return files;
        }

        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> isContextCandidate(root, path))
                    .sorted(Comparator.comparing(path -> toUnixPath(root.relativize(path).toString())))
                    .forEach(files::add);
        } catch (IOException ignored) {
            return List.of();
        }

        return files;
    }

    private boolean isContextCandidate(Path root, Path file) {
        String relative = toUnixPath(root.relativize(file).toString());
        String lower = relative.toLowerCase(Locale.ROOT);

        if (lower.startsWith("target/")
                || lower.startsWith(".git/")
                || lower.startsWith(".idea/")
                || lower.startsWith(".vscode/")
                || lower.contains("/node_modules/")) {
            return false;
        }

        if (lower.startsWith("src/main/webapp/")) {
            return lower.endsWith(".jsp")
                    || lower.endsWith(".jspf")
                    || lower.endsWith(".html")
                    || lower.endsWith(".css")
                    || lower.endsWith(".js")
                    || lower.endsWith(".xml");
        }

        if (lower.startsWith("src/main/java/app/web/")) {
            return lower.endsWith(".java");
        }

        if (!lower.startsWith("src/")) {
            return false;
        }

        return false;
    }

    private String readFileExcerpt(Path file, int maxChars) {
        if (file == null || maxChars < 1) {
            return "";
        }

        try {
            if (Files.size(file) > 256_000) {
                return "";
            }
        } catch (IOException ignored) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        boolean truncated = false;

        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            for (String line : (Iterable<String>) lines::iterator) {
                out.append(line).append('\n');
                if (out.length() >= maxChars) {
                    truncated = true;
                    break;
                }
            }
        } catch (IOException ignored) {
            return "";
        }

        String excerpt = out.toString().trim();
        if (truncated && !excerpt.isBlank()) {
            return excerpt + "\n...[truncated]";
        }
        return excerpt;
    }

    private String toUnixPath(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    private String compactErrorBody(String body, int maxChars) {
        if (body == null || body.isBlank() || maxChars < 1) {
            return "";
        }

        String compact = body
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        if (compact.length() <= maxChars) {
            return compact;
        }
        return compact.substring(0, maxChars);
    }

    private record ContextSnapshot(List<IndexedContextEntry> entries, Instant generatedAt) {
    }

        private record IndexedContextEntry(
            String relativePath,
            String featureSummary,
            String fallbackExcerpt,
            String searchText
        ) {
    }

    private record ScoredContextEntry(IndexedContextEntry entry, int score) {
    }

        private record ShortcutRule(String path, String feature, String label) {
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