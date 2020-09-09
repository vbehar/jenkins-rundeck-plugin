package org.jenkinsci.plugins.rundeck.cache;

import java.util.logging.Logger;

import org.jenkinsci.plugins.rundeck.RundeckNotifier;
import org.jenkinsci.plugins.rundeck.client.RundeckClientManager;
import org.rundeck.client.api.model.JobItem;

/**
 * Dummy Rundeck job cache implementation based. Used as a placeholder when caching is disabled.
 *
 * @author Marcin Zajączkowski
 * @since 3.6.0
 */
public class DummyRundeckJobCache implements RundeckJobCache {

    private static final Logger log = Logger.getLogger(DummyRundeckJobCache.class.getName());

    @Override
    public JobItem findJobById(String rundeckJobId, String rundeckInstanceName, RundeckClientManager rundeckInstance) {
        return RundeckNotifier.RundeckDescriptor.findJobUncached(rundeckJobId, rundeckInstance);
    }

    @Override
    public String logAndGetStats() {
        return "0% hit rate for dummy cache";
    }

    @Override
    public void invalidate() {
        log.warning("Invalidation of dummy cache");
    }
}
