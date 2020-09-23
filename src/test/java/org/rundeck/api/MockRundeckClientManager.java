package org.rundeck.api;

import hudson.util.Secret;
import org.apache.commons.lang.time.DateUtils;
import org.jenkinsci.plugins.rundeck.RundeckInstance;
import org.jenkinsci.plugins.rundeck.client.RundeckManager;
import org.rundeck.client.api.model.*;

import java.io.IOException;
import java.util.Date;
import java.util.Properties;

public class MockRundeckClientManager implements RundeckManager {
    RundeckInstance rundeckInstance;

    public MockRundeckClientManager(String user, String password) {
        this.rundeckInstance = new RundeckInstance();
        rundeckInstance.setLogin(user);
        rundeckInstance.setPassword(Secret.fromString(password));
        rundeckInstance.setUrl("http://localhost:4440");
    }

    public MockRundeckClientManager() {
        this.rundeckInstance = new RundeckInstance();
        rundeckInstance.setLogin("admin");
        rundeckInstance.setPassword(Secret.fromString("password"));
        rundeckInstance.setUrl("http://localhost:4440");
    }

    @Override
    public AbortResult abortExecution(String id) throws IOException {
        return null;
    }

    @Override
    public ExecOutput getOutput(Long executionId, Long var2, Integer var3, Integer var4) throws IOException {
        return null;
    }

    @Override
    public ExecOutput getOutput(String executionId, Long var2, Long var3, Long var4) throws IOException {
        return null;
    }

    @Override
    public Execution getExecution(String id) throws IOException {
        Execution execution = new Execution();
        execution.setId("1");
        execution.setHref("http://localhost:4440/execution/follow/1");
        execution.setStatus("success");
        execution.setProject("test");
        return execution;
    }

    @Override
    public String findJobId(String project, String name, String groupPath) throws IOException {
        return "job-1234";
    }

    @Override
    public JobItem findJob(String project, String name, String groupPath) throws IOException {
        return null;
    }

    @Override
    public JobItem getJob(String id) throws IOException {
        return null;
    }

    @Override
    public Execution runExecution(String jobId, Properties options, Properties nodeFilters) throws IOException {
        Execution execution = new Execution();
        execution.setId("1");
        execution.setHref("http://localhost:4440/execution/follow/1");
        execution.setPermalink("http://localhost:4440/execution/follow/1");
        execution.setStatus("SUCCEEDED");
        execution.setProject("test");

        DateInfo dateStart = new DateInfo("2021-01-01T00:00:00Z");
        DateInfo dateEnd = new DateInfo("2021-01-01T00:03:27Z");

        execution.setDateStarted(dateStart);
        execution.setDateEnded(dateEnd);

        return execution;
    }

    @Override
    public boolean ping() throws IOException {
        return true;
    }

    @Override
    public boolean testAuth() throws IOException {
        return false;
    }

    @Override
    public RundeckInstance getRundeckInstance() {
        return this.rundeckInstance;
    }


}
