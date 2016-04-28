package org.jenkinsci.plugins.rundeck.cache;

//TODO: Could be immutable with builder and copy constructor
public class RundeckJobCacheConfig {

    private boolean enabled = false;
    @Deprecated
    private int jobDetailsAfterWriteExpirationInMinutes = 12 * 60;
    //TODO: Switch to int when all instances are migrated
    private Integer afterAccessExpirationInMinutes = 18 * 60;
    private Integer maximumSize = 500;
    private Integer cacheStatsDisplayHitThreshold = 200;

    private RundeckJobCacheConfig() {
    }

    public static RundeckJobCacheConfig initializeWithDefaultValues() {
        return new RundeckJobCacheConfig();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getJobDetailsAfterWriteExpirationInMinutes() {
        return jobDetailsAfterWriteExpirationInMinutes;
    }

    public Integer getAfterAccessExpirationInMinutes() {
        return afterAccessExpirationInMinutes;
    }

    @Deprecated
    public void setAfterAccessExpirationInMinutes(Integer afterAccessExpirationInMinutes) {
        this.afterAccessExpirationInMinutes = afterAccessExpirationInMinutes;
    }

    public Integer getMaximumSize() {
        return maximumSize;
    }

    @Deprecated
    public void setMaximumSize(Integer maximumSize) {
        this.maximumSize = maximumSize;
    }

    public Integer getCacheStatsDisplayHitThreshold() {
        return cacheStatsDisplayHitThreshold;
    }

    @Deprecated
    public void setCacheStatsDisplayHitThreshold(Integer cacheStatsDisplayHitThreshold) {
        this.cacheStatsDisplayHitThreshold = cacheStatsDisplayHitThreshold;
    }

    @Override
    public String toString() {
        return "RundeckJobCacheConfig{" +
                "enabled=" + enabled +
                ", jobDetailsAfterWriteExpirationInMinutes=" + jobDetailsAfterWriteExpirationInMinutes +
                ", afterAccessExpirationInMinutes=" + afterAccessExpirationInMinutes +
                ", maximumSize=" + maximumSize +
                ", cacheStatsDisplayHitThreshold=" + cacheStatsDisplayHitThreshold +
                '}';
    }
}
