package org.jenkinsci.plugins.rundeck.cache;

import java.util.logging.Logger;

import org.jenkinsci.plugins.rundeck.RundeckNotifier;
import org.rundeck.api.RundeckClient;
import org.rundeck.api.domain.RundeckJob;

public class DummyRundeckJobCache implements IRundeckJobCache {

    private static final Logger log = Logger.getLogger(DummyRundeckJobCache.class.getName());

    @Override
    public RundeckJob findJobById(String rundeckJobId, String rundeckInstanceName, RundeckClient rundeckInstance) {
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
