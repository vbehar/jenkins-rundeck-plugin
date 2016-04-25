package org.jenkinsci.plugins.rundeck.cache;

import org.jenkinsci.plugins.rundeck.RundeckNotifier;
import org.jenkinsci.plugins.rundeck.cache.IRundeckJobCache;
import org.rundeck.api.RundeckClient;
import org.rundeck.api.domain.RundeckJob;

public class DummyRundeckJobCache implements IRundeckJobCache {

    @Override
    public RundeckJob findJobById(String rundeckJobId, String rundeckInstanceName, RundeckClient rundeckInstance) {
        return RundeckNotifier.RundeckDescriptor.findJobUncached(rundeckJobId, rundeckInstance);
    }
}
