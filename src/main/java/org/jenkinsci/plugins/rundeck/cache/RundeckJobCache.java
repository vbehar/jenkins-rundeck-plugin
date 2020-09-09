package org.jenkinsci.plugins.rundeck.cache;

import org.jenkinsci.plugins.rundeck.client.RundeckClientManager;
import org.rundeck.client.api.model.JobItem;

/**
 * Interface with operation for Rundeck job cache.
 *
 * @author Marcin Zajączkowski
 * @since 3.6.0
 */
public interface RundeckJobCache {

    JobItem findJobById(final String rundeckJobId, final String rundeckInstanceName, final RundeckClientManager rundeckInstance);

    String logAndGetStats();

    void invalidate();
}
