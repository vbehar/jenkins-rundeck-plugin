package org.rundeck.api;

import hudson.util.Secret;
import org.jenkinsci.plugins.rundeck.RundeckInstance;
import org.jenkinsci.plugins.rundeck.client.RundeckClientManager;
import org.rundeck.client.api.model.Execution;
import retrofit2.Response;

import java.io.IOException;
import java.util.Properties;

public class MockRundeckClientManager extends RundeckClientManager {

    public MockRundeckClientManager(String user, String password) {
        RundeckInstance rundeckInstance = new RundeckInstance();
        rundeckInstance.setLogin(user);
        rundeckInstance.setPassword(Secret.fromString(password));
        rundeckInstance.setUrl("http://localhost:4440");
        super.setRundeckInstance(rundeckInstance);
    }

    public MockRundeckClientManager() {
        RundeckInstance rundeckInstance = new RundeckInstance();
        rundeckInstance.setLogin("admin");
        rundeckInstance.setPassword(Secret.fromString("password"));
        rundeckInstance.setUrl("http://localhost:4440");
        super.setRundeckInstance(rundeckInstance);
    }

    @Override
    public Response<Execution> runExecution(String jobId, Properties options, Properties nodeFilters) throws IOException {
        return super.runExecution(jobId, options, nodeFilters);
    }
}
