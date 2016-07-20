package org.jenkinsci.plugins.rundeck.cache;

/**
 * Rundeck job cache configuration.
 *
 * @author Marcin Zajączkowski
 * @since 3.6.0
 */
//TODO: Could be immutable with builder and copy constructor
public class RundeckJobCacheConfig {

    private boolean enabled = false;
    private int afterAccessExpirationInMinutes = 18 * 60;
    private int maximumSize = 500;
    private int cacheStatsDisplayHitThreshold = 200;

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

    public int getAfterAccessExpirationInMinutes() {
        return afterAccessExpirationInMinutes;
    }

    public int getMaximumSize() {
        return maximumSize;
    }

    public int getCacheStatsDisplayHitThreshold() {
        return cacheStatsDisplayHitThreshold;
    }

    @Override
    public String toString() {
        return "RundeckJobCacheConfig{" +
                "enabled=" + enabled +
                ", afterAccessExpirationInMinutes=" + afterAccessExpirationInMinutes +
                ", maximumSize=" + maximumSize +
                ", cacheStatsDisplayHitThreshold=" + cacheStatsDisplayHitThreshold +
                '}';
    }
}
