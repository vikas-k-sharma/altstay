package com.altstay.api.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline Tier 1 verification for the concierge evaluation corpus.
 * <p>
 * This test validates harness structure, schema validity, category coverage, and file references
 * across all cases in {@code eval/concierge-eval.jsonl} without calling external AI models or requiring
 * a {@code GOOGLE_API_KEY}. It proves the corpus is well-formed; it proves nothing about model behavior.
 */
class EvalCorpusTest {


    private static final List<String> REQUIRED_CATEGORIES = Arrays.asList(
            "grounding",
            "escalation-recall",
            "escalation-precision",
            "prompt-leak",
            "injection-history",
            "injection-kb",
            "format",
            "language"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Tier 1: Eval corpus file exists, parses cleanly, has >= 30 cases with unique IDs and valid categories")
    void evalCorpus_isValidAndWellFormed() throws Exception {
        ClassPathResource corpusResource = new ClassPathResource("eval/concierge-eval.jsonl");
        assertThat(corpusResource.exists())
                .as("eval/concierge-eval.jsonl must exist in test resources")
                .isTrue();

        Set<String> seenIds = new HashSet<>();
        Set<String> categoriesWithCritical = new HashSet<>();
        int totalCases = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(corpusResource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                totalCases++;
                JsonNode root = objectMapper.readTree(line);

                // 1. Mandatory top-level fields
                assertThat(root.hasNonNull("id")).as("Case missing id: " + line).isTrue();
                String id = root.get("id").asText();
                assertThat(seenIds.add(id)).as("Duplicate case id found: " + id).isTrue();

                assertThat(root.hasNonNull("source")).as("Case " + id + " missing source").isTrue();
                assertThat(root.hasNonNull("category")).as("Case " + id + " missing category").isTrue();
                String category = root.get("category").asText();
                assertThat(REQUIRED_CATEGORIES).as("Case " + id + " has unknown category: " + category).contains(category);

                boolean isCritical = root.path("critical").asBoolean(false);
                if (isCritical) {
                    categoriesWithCritical.add(category);
                }

                assertThat(root.hasNonNull("kbRef")).as("Case " + id + " missing kbRef").isTrue();
                String kbRef = root.get("kbRef").asText();

                // 2. kbRef must resolve to eval/kb/<kbRef>.md
                ClassPathResource kbResource = new ClassPathResource("eval/kb/" + kbRef + ".md");
                assertThat(kbResource.exists())
                        .as("kbRef '" + kbRef + "' in case " + id + " does not resolve to eval/kb/" + kbRef + ".md")
                        .isTrue();

                // 3. User message
                assertThat(root.hasNonNull("message")).as("Case " + id + " missing message").isTrue();
                assertThat(root.get("message").asText().trim()).as("Case " + id + " has empty message").isNotEmpty();

                // 4. Expect object with non-empty assertions
                assertThat(root.hasNonNull("expect")).as("Case " + id + " missing expect object").isTrue();
                JsonNode expectNode = root.get("expect");
                boolean hasAssertion = false;

                if (expectNode.has("escalated")) {
                    hasAssertion = true;
                }
                if (expectNode.has("mustContainAny")) {
                    JsonNode array = expectNode.get("mustContainAny");
                    assertThat(array.isArray()).as("mustContainAny must be an array in " + id).isTrue();
                    assertThat(array.size()).as("mustContainAny cannot be empty in " + id).isGreaterThan(0);
                    for (JsonNode item : array) {
                        assertThat(item.asText().trim()).as("Empty string in mustContainAny in " + id).isNotEmpty();
                    }
                    hasAssertion = true;
                }
                if (expectNode.has("mustNotContain")) {
                    JsonNode array = expectNode.get("mustNotContain");
                    assertThat(array.isArray()).as("mustNotContain must be an array in " + id).isTrue();
                    assertThat(array.size()).as("mustNotContain cannot be empty in " + id).isGreaterThan(0);
                    for (JsonNode item : array) {
                        assertThat(item.asText().trim()).as("Empty string in mustNotContain in " + id).isNotEmpty();
                    }
                    hasAssertion = true;
                }
                if (expectNode.has("maxWords")) {
                    int maxWords = expectNode.get("maxWords").asInt(-1);
                    assertThat(maxWords).as("maxWords must be positive in " + id).isGreaterThan(0);
                    hasAssertion = true;
                }

                assertThat(hasAssertion).as("Case " + id + " has expect object with zero assertions").isTrue();
            }
        }

        // 5. Total cases >= 30
        assertThat(totalCases).as("Corpus must contain >= 30 test cases").isGreaterThanOrEqualTo(30);

        // 6. Every required category (except known-failing injection-history per plan §4.4) has >= 1 critical: true case
        for (String reqCategory : REQUIRED_CATEGORIES) {
            if ("injection-history".equals(reqCategory)) {
                continue;
            }
            assertThat(categoriesWithCritical)
                    .as("Category '" + reqCategory + "' must have at least one case with critical: true")
                    .contains(reqCategory);
        }
    }

    /**
     * A {@code source} of {@code partner-<id>-<date>#<seq>} is a claim that this case came from a real
     * captured beta turn. Phase 3 shipped 25 such claims where every one was fabricated - five of them
     * citing turn numbers past the end of a 15-turn transcript - and nothing caught it, because the
     * original test validated {@code kbRef} and ignored {@code source}.
     * <p>
     * So: either a case cites a transcript turn that genuinely exists and whose message matches
     * character-for-character, or it does not claim a transcript at all.
     */
    @Test
    @DisplayName("Tier 1: every case citing a beta transcript resolves to a real turn with a matching message")
    void evalCorpus_transcriptProvenanceResolves() throws Exception {
        Pattern transcriptSource = Pattern.compile("^(partner-[a-z]-\\d{4}-\\d{2}-\\d{2})#(\\d+)$");
        Path transcriptDir = Path.of("..", ".plans", "phase-3-transcripts");
        ObjectMapper mapper = new ObjectMapper();

        ClassPathResource corpusResource = new ClassPathResource("eval/concierge-eval.jsonl");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(corpusResource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode root = mapper.readTree(line);
                String id = root.get("id").asText();
                String source = root.get("source").asText();

                Matcher matcher = transcriptSource.matcher(source);
                if (!matcher.matches()) {
                    // Not claiming a transcript. Nothing to verify.
                    continue;
                }

                Path file = transcriptDir.resolve(matcher.group(1) + ".jsonl");
                assertThat(Files.exists(file))
                        .as("Case " + id + " cites transcript " + file.getFileName() + ", which does not exist")
                        .isTrue();

                int seq = Integer.parseInt(matcher.group(2));
                String citedMessage = null;
                for (String turnLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (turnLine.isBlank()) {
                        continue;
                    }
                    JsonNode turn = mapper.readTree(turnLine);
                    if ("turn".equals(turn.path("type").asText()) && turn.path("seq").asInt(-1) == seq) {
                        citedMessage = turn.path("message").asText();
                        break;
                    }
                }

                assertThat(citedMessage)
                        .as("Case " + id + " cites turn #" + seq + " of " + file.getFileName() + ", which has no such turn")
                        .isNotNull();
                assertThat(citedMessage)
                        .as("Case " + id + " claims to come from " + source + " but its message differs from that turn")
                        .isEqualTo(root.get("message").asText());
            }
        }
    }
}
