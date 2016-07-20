package org.jenkinsci.plugins.rundeck.cache;

import org.rundeck.api.RundeckClient;
import org.rundeck.api.domain.RundeckJob;

/**
 * Interface with operation for Rundeck job cache.
 *
 * @author Marcin Zajączkowski
 * @since 3.6.0
 */
public interface RundeckJobCache {

    RundeckJob findJobById(final String rundeckJobId, final String rundeckInstanceName, final RundeckClient rundeckInstance);

    String logAndGetStats();

    void invalidate();
}
