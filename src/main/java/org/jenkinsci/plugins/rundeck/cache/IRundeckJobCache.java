package org.jenkinsci.plugins.rundeck.cache;

import org.rundeck.api.RundeckClient;
import org.rundeck.api.domain.RundeckJob;

public interface IRundeckJobCache {

    RundeckJob findJobById(final String rundeckJobId, final String rundeckInstanceName, final RundeckClient rundeckInstance);

    String logAndGetStats();

    void invalidate();
}
