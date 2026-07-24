package io.github.erselseyit.basestation.monitoring.config;

import io.github.erselseyit.basestation.monitoring.model.LearnedPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Startup migration that deduplicates LearnedPattern documents.
 *
 * Root cause: The {@code @Indexed(unique = true)} annotation on problemCode
 * was not enforced because {@code auto-index-creation} was disabled.
 * This migration merges any existing duplicates so the unique index can
 * be created cleanly on startup.
 *
 * Idempotent: safe to run on every startup — does nothing if no duplicates exist.
 */
@Component
public class LearnedPatternMigration {

    private static final Logger log = LoggerFactory.getLogger(LearnedPatternMigration.class);

    private final MongoTemplate mongoTemplate;

    public LearnedPatternMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void deduplicatePatterns() {
        List<LearnedPattern> allPatterns = mongoTemplate.findAll(LearnedPattern.class);
        if (allPatterns.isEmpty()) {
            return;
        }

        // Group by problemCode
        Map<String, List<LearnedPattern>> grouped = allPatterns.stream()
                .collect(Collectors.groupingBy(LearnedPattern::getProblemCode));

        int totalMerged = 0;

        for (Map.Entry<String, List<LearnedPattern>> entry : grouped.entrySet()) {
            List<LearnedPattern> duplicates = entry.getValue();
            if (duplicates.size() <= 1) {
                continue;
            }

            String problemCode = entry.getKey();
            log.warn("Found {} duplicate LearnedPattern documents for '{}' — merging",
                    duplicates.size(), problemCode);

            // Keep the one with the most total cases
            duplicates.sort(Comparator.comparingInt(
                    (LearnedPattern p) -> p.getResolvedCount() + p.getFailedCount()
            ).reversed());

            LearnedPattern keeper = duplicates.get(0);

            // Merge counts from duplicates into the keeper
            for (int i = 1; i < duplicates.size(); i++) {
                LearnedPattern dup = duplicates.get(i);
                keeper.setResolvedCount(keeper.getResolvedCount() + dup.getResolvedCount());
                keeper.setFailedCount(keeper.getFailedCount() + dup.getFailedCount());

                // Merge successful solution records
                for (LearnedPattern.SolutionRecord sr : dup.getSuccessfulSolutions()) {
                    mergeSolutionRecord(keeper.getSuccessfulSolutions(), sr);
                }
                for (LearnedPattern.SolutionRecord sr : dup.getFailedSolutions()) {
                    mergeSolutionRecord(keeper.getFailedSolutions(), sr);
                }

                // Delete the duplicate
                mongoTemplate.remove(
                        new Query(Criteria.where("_id").is(dup.getId())),
                        LearnedPattern.class
                );
                totalMerged++;
            }

            // Save the merged keeper
            mongoTemplate.save(keeper);
            log.info("Merged '{}': kept id={} with {} resolved + {} failed",
                    problemCode, keeper.getId(),
                    keeper.getResolvedCount(), keeper.getFailedCount());
        }

        if (totalMerged > 0) {
            log.info("LearnedPattern dedup complete: merged {} duplicate documents", totalMerged);
        } else {
            log.info("No duplicate LearnedPattern documents found");
        }

        // Ensure unique index on problemCode exists (auto-index-creation is disabled
        // because it runs during MongoTemplate init — before we can clean up duplicates)
        try {
            mongoTemplate.indexOps(LearnedPattern.class)
                    .ensureIndex(new Index().on("problemCode", Sort.Direction.ASC).unique());
            log.info("Ensured unique index on LearnedPattern.problemCode");
        } catch (Exception e) {
            log.error("Failed to create unique index on problemCode: {}", e.getMessage(), e);
        }
    }

    private void mergeSolutionRecord(List<LearnedPattern.SolutionRecord> target,
                                     LearnedPattern.SolutionRecord source) {
        for (LearnedPattern.SolutionRecord existing : target) {
            if (existing.getAction().equals(source.getAction())) {
                existing.setCount(existing.getCount() + source.getCount());
                existing.setTotalRating(existing.getTotalRating() + source.getTotalRating());
                return;
            }
        }
        // No matching action found — add as new record
        LearnedPattern.SolutionRecord copy = new LearnedPattern.SolutionRecord(
                source.getAction(), source.getCommands()
        );
        copy.setCount(source.getCount());
        copy.setTotalRating(source.getTotalRating());
        target.add(copy);
    }
}
