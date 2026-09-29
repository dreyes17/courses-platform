package com.example.courses.shared.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;

/**
 * In-memory catalog caches (Caffeine). Every write path evicts what it changes; the TTL only bounds staleness in
 * the cases eviction can't cover: a read that started before a write's commit and caches the old value after the
 * eviction, and other instances, whose local caches this one can't evict.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    /** CategoryView by id. */
    public static final String CATEGORIES = "categories";
    /** Pages of CategoryView by Pageable (page, size, sort): the category list is small and read often. */
    public static final String CATEGORY_PAGES = "category-pages";
    /** InstructorView by id. */
    public static final String INSTRUCTORS = "instructors";
    /** CourseView by id. It embeds the category and instructor names and the seat counters. */
    public static final String COURSES = "courses";

    @Bean
    CacheManager cacheManager(@Value("${app.cache.catalog-ttl:10m}") Duration catalogTtl,
                              @Value("${app.cache.course-ttl:1m}") Duration courseTtl) {
        CaffeineCacheManager caffeine = new CaffeineCacheManager();
        // A fixed set of caches: a mistyped cache name fails instead of silently creating an unbounded cache.
        caffeine.setCacheNames(Set.of());
        caffeine.setAllowNullValues(false);
        caffeine.registerCustomCache(CATEGORIES, cache(catalogTtl, 1_000));
        caffeine.registerCustomCache(CATEGORY_PAGES, cache(catalogTtl, 100));
        caffeine.registerCustomCache(INSTRUCTORS, cache(catalogTtl, 1_000));
        caffeine.registerCustomCache(COURSES, cache(courseTtl, 10_000));
        // Puts and evictions made inside a transaction are applied after it commits. Evicting before the commit
        // would let a concurrent read cache the old row again; on rollback nothing is evicted needlessly.
        return new TransactionAwareCacheManagerProxy(caffeine);
    }

    private static com.github.benmanes.caffeine.cache.Cache<Object, Object> cache(Duration ttl, long maximumSize) {
        // recordStats feeds the cache.gets / cache.puts / cache.evictions metrics exported to Prometheus.
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maximumSize).recordStats().build();
    }
}
