package org.jenkinsci.plugins.rundeck.cache;

import static java.lang.String.format;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.jenkinsci.plugins.rundeck.RundeckNotifier;
import org.rundeck.api.RundeckApiException;
import org.rundeck.api.RundeckClient;
import org.rundeck.api.domain.RundeckJob;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;

public final class InMemoryRundeckJobCache implements RundeckJobCache {

    private static final Logger log = Logger.getLogger(InMemoryRundeckJobCache.class.getName());

    private static final int RUNDECK_INSTANCE_CACHE_CONTAINER_EXPIRATION_IN_DAYS = 1;

    //TODO: Remove that field - it is used only during the initialization
    private final RundeckJobCacheConfig rundeckJobCacheConfig;

    private final int cacheStatsDisplayHitThreshold;

    private final LoadingCache<String, Cache<String, RundeckJob>> rundeckJobInstanceAwareCache;

    private long hitCounter = 0;

    public InMemoryRundeckJobCache(RundeckJobCacheConfig rundeckJobCacheConfig) {
        this.rundeckJobCacheConfig = Objects.requireNonNull(rundeckJobCacheConfig);
        this.cacheStatsDisplayHitThreshold = rundeckJobCacheConfig.getCacheStatsDisplayHitThreshold();
        this.rundeckJobInstanceAwareCache = CacheBuilder.newBuilder()
                .expireAfterAccess(RUNDECK_INSTANCE_CACHE_CONTAINER_EXPIRATION_IN_DAYS, TimeUnit.DAYS)    //just in case given instance was removed
                .build(
                        new CacheLoader<String, Cache<String, RundeckJob>>() {
                            @Override
                            public Cache<String, RundeckJob> load(String rundeckInstanceName) throws Exception {
                                return createJobCacheForRundeckInstance(rundeckInstanceName);
                            }
                        });
    }

    private Cache<String, RundeckJob> createJobCacheForRundeckInstance(String rundeckInstanceName) {
        logInfoWithThreadId(format("Loading (GENERATING) jobs cache container for Rundeck instance %s", rundeckInstanceName));
        return CacheBuilder.newBuilder()
                .expireAfterAccess(rundeckJobCacheConfig.getAfterAccessExpirationInMinutes(), TimeUnit.MINUTES)
                .maximumSize(rundeckJobCacheConfig.getMaximumSize())
                .build();
    }

    public RundeckJob findJobById(final String rundeckJobId, final String rundeckInstanceName, final RundeckClient rundeckInstance) {
        try {
            logInfoWithThreadId(format("Cached findJob request for jobId: %s (%s)", rundeckJobId, rundeckInstanceName));   //TODO: Change to FINE
            return findByJobIdInCacheOrAskServer(rundeckJobId, rundeckInstanceName, rundeckInstance);
        } catch (UncheckedExecutionException | ExecutionException e) {
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
            for(Map.Entry<String, Cache<String, RundeckJob>> instanceCacheEntries: rundeckJobInstanceAwareCache.asMap().entrySet()) {
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

    private RundeckJob findByJobIdInCacheOrAskServer(final String rundeckJobId, String rundeckInstanceName,
                                                     final RundeckClient rundeckInstance) throws ExecutionException {
        Cache<String, RundeckJob> rundeckJobCache = rundeckJobInstanceAwareCache.get(rundeckInstanceName);

        RundeckJob tmp = rundeckJobCache.get(rundeckJobId, new Callable<RundeckJob>() {
            public RundeckJob call() throws Exception {
                return RundeckNotifier.RundeckDescriptor.findJobUncached(rundeckJobId, rundeckInstance);
            }
        });
        logCacheStatsIfAppropriate(rundeckInstanceName, rundeckJobCache);
        return tmp;
    }

    private RuntimeException rethrowUnwrappingRundeckApiExceptionIfPossible(Exception e) {
        if (e.getCause() != null && e.getCause() instanceof RundeckApiException) {
            throw new RundeckApiException(e.getMessage(), e);
        }
        throw Throwables.propagate(e);
    }

    private void logCacheStatsIfAppropriate(String instanceName, Cache<String, RundeckJob> jobCache) {
        if (++hitCounter % cacheStatsDisplayHitThreshold == 0) {
            logCacheStats(instanceName, jobCache);
        }
    }

    private void logCacheStats(String instanceName, Cache<String, ?> jobCache) {
        logInfoWithThreadId(format("%s: %s", instanceName, jobCache.stats()));
    }

    //TODO: Remove
    private static void logInfoWithThreadId(String message) {
        log.info(format("%d: %s", Thread.currentThread().getId(), message));
    }

}
