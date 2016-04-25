package org.jenkinsci.plugins.rundeck.cache;

public class RundeckJobCacheConfig {

    private boolean enabled = false;
    private int jobDetailsAfterWriteExpirationInMinutes = 12 * 60;

    public RundeckJobCacheConfig(boolean enabled, int jobDetailsAfterWriteExpirationInMinutes) {
        this.enabled = enabled;
        this.jobDetailsAfterWriteExpirationInMinutes = jobDetailsAfterWriteExpirationInMinutes;
    }

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

    @Override
    public String toString() {
        return "RundeckJobCacheConfig{" +
                "enabled=" + enabled +
                ", jobDetailsAfterWriteExpirationInMinutes=" + jobDetailsAfterWriteExpirationInMinutes +
                '}';
    }
}
