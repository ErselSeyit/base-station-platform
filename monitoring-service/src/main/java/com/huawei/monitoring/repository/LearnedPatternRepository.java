package com.huawei.monitoring.repository;

import com.huawei.monitoring.model.LearnedPattern;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for LearnedPattern entities.
 */
@Repository
public interface LearnedPatternRepository extends MongoRepository<LearnedPattern, String> {

    /**
     * Find pattern by problem code.
     */
    Optional<LearnedPattern> findByProblemCode(String problemCode);

    /**
     * Find patterns by category.
     */
    List<LearnedPattern> findByCategory(String category);

    /**
     * Find patterns with high success rate (>80%).
     */
    @Query("{ $expr: { $gt: [ { $divide: ['$resolvedCount', { $add: ['$resolvedCount', '$failedCount'] }] }, 0.8 ] } }")
    List<LearnedPattern> findHighSuccessPatterns();

    /**
     * Find patterns with low success rate (<50%) that need attention.
     */
    @Query("{ $expr: { $and: [ { $gt: [{ $add: ['$resolvedCount', '$failedCount'] }, 5] }, { $lt: [ { $divide: ['$resolvedCount', { $add: ['$resolvedCount', '$failedCount'] }] }, 0.5 ] } ] } }")
    List<LearnedPattern> findLowSuccessPatterns();

    /**
     * Find all patterns ordered by total cases.
     */
    @Query(value = "{}", sort = "{ 'resolvedCount': -1, 'failedCount': -1 }")
    List<LearnedPattern> findAllOrderByTotalCases();
}
