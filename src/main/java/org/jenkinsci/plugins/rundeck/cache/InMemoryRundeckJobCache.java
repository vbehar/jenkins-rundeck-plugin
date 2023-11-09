package org.jenkinsci.plugins.rundeck.cache;

import static java.lang.String.format;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.jenkinsci.plugins.rundeck.RundeckNotifier;
import org.jenkinsci.plugins.rundeck.client.RundeckClientManager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.jenkinsci.plugins.rundeck.client.RundeckManager;
import org.rundeck.client.api.model.JobItem;

/**
 * In-memory Rundeck job cache implementation based on Caffeine.
 *
 * @author Marcin Zajączkowski
 * @since 3.6.0
 */
public class InMemoryRundeckJobCache implements RundeckJobCache {

    private static final Logger log = Logger.getLogger(InMemoryRundeckJobCache.class.getName());

    private static final int RUNDECK_INSTANCE_CACHE_CONTAINER_EXPIRATION_IN_DAYS = 1;

    private final int cacheStatsDisplayHitThreshold;

    private final LoadingCache<String, Cache<String, JobItem>> rundeckJobInstanceAwareCache;

    private long hitCounter = 0;

    public InMemoryRundeckJobCache(final RundeckJobCacheConfig rundeckJobCacheConfig) {
        Objects.requireNonNull(rundeckJobCacheConfig);
        this.cacheStatsDisplayHitThreshold = rundeckJobCacheConfig.getCacheStatsDisplayHitThreshold();
        this.rundeckJobInstanceAwareCache = Caffeine.newBuilder().recordStats()
                .expireAfterAccess(RUNDECK_INSTANCE_CACHE_CONTAINER_EXPIRATION_IN_DAYS, TimeUnit.DAYS)    //just in case given instance was removed
                .build(
                        rundeckInstanceName -> createJobCacheForRundeckInstance(rundeckInstanceName, rundeckJobCacheConfig));
    }

    private Cache<String, JobItem> createJobCacheForRundeckInstance(String rundeckInstanceName, RundeckJobCacheConfig rundeckJobCacheConfig) {
        log.info(format("Loading (GENERATING) jobs cache container for Rundeck instance %s", rundeckInstanceName));
        return Caffeine.newBuilder().recordStats()
                .expireAfterAccess(rundeckJobCacheConfig.getAfterAccessExpirationInMinutes(), TimeUnit.MINUTES)
                .maximumSize(rundeckJobCacheConfig.getMaximumSize())
                .build();
    }

    public JobItem findJobById(final String rundeckJobId, final String rundeckInstanceName, final RundeckManager rundeckInstance) {
        log.fine(format("Cached findJob request for jobId: %s (%s)", rundeckJobId, rundeckInstanceName));
        return findByJobIdInCacheOrAskServer(rundeckJobId, rundeckInstanceName, rundeckInstance);
    }

    @Override
    public String logAndGetStats() {
        return logStatsAndReturnsAsString();
    }

    private String logStatsAndReturnsAsString() {
        StringBuilder sb = new StringBuilder();
        if (rundeckJobInstanceAwareCache.estimatedSize() == 0) {
            sb.append("Cache is empty");
        } else {
            for(Map.Entry<String, Cache<String, JobItem>> instanceCacheEntries: rundeckJobInstanceAwareCache.asMap().entrySet()) {
                logCacheStats(instanceCacheEntries.getKey(), instanceCacheEntries.getValue());
                sb.append(format("%s: %s", instanceCacheEntries.getKey(), instanceCacheEntries.getValue().stats())).append("\n");
            }
        }
        logCacheStats("Meta", rundeckJobInstanceAwareCache);
        return sb.toString();
    }

    @Override
    public void invalidate() {
        logStatsAndReturnsAsString();
        log.info("Rundeck job cache invalidation");
        rundeckJobInstanceAwareCache.invalidateAll();
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private JobItem findByJobIdInCacheOrAskServer(final String rundeckJobId, String rundeckInstanceName,
                                                  final RundeckManager rundeckInstance) {
        Cache<String, JobItem> rundeckJobCache = rundeckJobInstanceAwareCache.get(rundeckInstanceName);

        JobItem tmp = rundeckJobCache.get(rundeckJobId, unused -> RundeckNotifier.RundeckDescriptor.findJobUncached(rundeckJobId, rundeckInstance));
        logCacheStatsIfAppropriate(rundeckInstanceName, rundeckJobCache);
        return tmp;
    }

    private void logCacheStatsIfAppropriate(String instanceName, Cache<String, JobItem> jobCache) {
        if (cacheStatsDisplayHitThreshold <= 0) {    //stats printing disabled
            return;
        }
        if (++hitCounter % cacheStatsDisplayHitThreshold == 0) {
            logCacheStats(instanceName, jobCache);
        }
    }

    private void logCacheStats(String instanceName, Cache<String, ?> jobCache) {
        log.info(format("%s: %s", instanceName, jobCache.stats()));
    }
}
