package org.jenkinsci.plugins.rundeck.cache;

import static java.lang.String.format;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.jenkinsci.plugins.rundeck.RundeckNotifier;
import org.jenkinsci.plugins.rundeck.client.RundeckClientManager;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.rundeck.client.api.model.JobItem;

/**
 * In-memory Rundeck job cache implementation based on Cache from Guava.
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
        this.rundeckJobInstanceAwareCache = CacheBuilder.newBuilder()
                .expireAfterAccess(RUNDECK_INSTANCE_CACHE_CONTAINER_EXPIRATION_IN_DAYS, TimeUnit.DAYS)    //just in case given instance was removed
                .build(
                        new CacheLoader<String, Cache<String, JobItem>>() {
                            @Override
                            public Cache<String, JobItem> load(String rundeckInstanceName) throws Exception {
                                return createJobCacheForRundeckInstance(rundeckInstanceName, rundeckJobCacheConfig);
                            }
                        });
    }

    private Cache<String, JobItem> createJobCacheForRundeckInstance(String rundeckInstanceName, RundeckJobCacheConfig rundeckJobCacheConfig) {
        log.info(format("Loading (GENERATING) jobs cache container for Rundeck instance %s", rundeckInstanceName));
        return CacheBuilder.newBuilder()
                .expireAfterAccess(rundeckJobCacheConfig.getAfterAccessExpirationInMinutes(), TimeUnit.MINUTES)
                .maximumSize(rundeckJobCacheConfig.getMaximumSize())
                .build();
    }

    public JobItem findJobById(final String rundeckJobId, final String rundeckInstanceName, final RundeckClientManager rundeckInstance) {
        try {
            log.fine(format("Cached findJob request for jobId: %s (%s)", rundeckJobId, rundeckInstanceName));
            return findByJobIdInCacheOrAskServer(rundeckJobId, rundeckInstanceName, rundeckInstance);
        } catch (ExecutionException e) {
            throw rethrowUnwrappingRundeckApiExceptionIfPossible(e);
        }
    }

    @Override
    public String logAndGetStats() {
        return logStatsAndReturnsAsString();
    }

    private String logStatsAndReturnsAsString() {
        StringBuilder sb = new StringBuilder();
        if (rundeckJobInstanceAwareCache.size() == 0) {
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

    private JobItem findByJobIdInCacheOrAskServer(final String rundeckJobId, String rundeckInstanceName,
                                                  final RundeckClientManager rundeckInstance) throws ExecutionException {
        Cache<String, JobItem> rundeckJobCache = rundeckJobInstanceAwareCache.get(rundeckInstanceName);

        JobItem tmp = rundeckJobCache.get(rundeckJobId, new Callable<JobItem>() {
            public JobItem call() throws Exception {
                return RundeckNotifier.RundeckDescriptor.findJobUncached(rundeckJobId, rundeckInstance);
            }
        });
        logCacheStatsIfAppropriate(rundeckInstanceName, rundeckJobCache);
        return tmp;
    }

    private RuntimeException rethrowUnwrappingRundeckApiExceptionIfPossible(Exception e) {
        if (e.getCause() != null && e.getCause() instanceof IOException) {
            throw new RuntimeException(e.getMessage());
        }
        throw Throwables.propagate(e);
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
