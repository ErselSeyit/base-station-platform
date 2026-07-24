package io.github.erselseyit.basestation.monitoring.config;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * One-off migration of stored metric documents from band-specific metric types
 * to the band-neutral model.
 *
 * <p>Before the migration, a reading was stored as {@code metricType:
 * "RSRP_NR700"}. Now the type is band-neutral and the band is a separate field,
 * so the same reading is {@code metricType: "RSRP", band: "N28"}. Documents
 * written before the change still carry the old names; without this migration
 * the enum would fail to deserialise them.
 *
 * <p>Idempotent: it only touches documents whose metricType still ends in a
 * band suffix, so re-running it is a no-op.
 */
@Component
public class MetricBandMigration {

    private static final Logger log = LoggerFactory.getLogger(MetricBandMigration.class);

    private static final String COLLECTION = "metric_data";

    /** Old band-specific type -> (new band-neutral type, band). */
    private static final Map<String, String[]> RENAMES = Map.of(
            "DL_THROUGHPUT_NR700",  new String[]{"DL_THROUGHPUT", "N28"},
            "UL_THROUGHPUT_NR700",  new String[]{"UL_THROUGHPUT", "N28"},
            "RSRP_NR700",           new String[]{"RSRP", "N28"},
            "SINR_NR700",           new String[]{"SINR", "N28"},
            "DL_THROUGHPUT_NR3500", new String[]{"DL_THROUGHPUT", "N78"},
            "UL_THROUGHPUT_NR3500", new String[]{"UL_THROUGHPUT", "N78"},
            "RSRP_NR3500",          new String[]{"RSRP", "N78"},
            "SINR_NR3500",          new String[]{"SINR", "N78"});

    private final MongoTemplate mongoTemplate;

    public MetricBandMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        long total = 0;
        for (Map.Entry<String, String[]> rename : RENAMES.entrySet()) {
            String oldType = rename.getKey();
            String newType = rename.getValue()[0];
            String band = rename.getValue()[1];

            Query query = new Query(Criteria.where("metricType").is(oldType));
            Update update = new Update().set("metricType", newType).set("band", band);

            long updated = mongoTemplate.updateMulti(query, update, COLLECTION).getModifiedCount();
            if (updated > 0) {
                log.info("Migrated {} metric documents from {} to {} band {}",
                        updated, oldType, newType, band);
                total += updated;
            }
        }
        if (total > 0) {
            log.info("Metric band migration complete: {} documents updated", total);
        }

        // Backfill: any pre-existing document with no band gets NONE, so the
        // field is present everywhere the new model expects it.
        long backfilled = mongoTemplate.updateMulti(
                new Query(Criteria.where("band").exists(false)),
                new Update().set("band", "NONE"),
                COLLECTION).getModifiedCount();
        if (backfilled > 0) {
            log.info("Backfilled band=NONE on {} metric documents", backfilled);
        }
    }
}
