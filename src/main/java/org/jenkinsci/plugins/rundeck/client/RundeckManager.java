package org.jenkinsci.plugins.rundeck.client;

import org.jenkinsci.plugins.rundeck.RundeckInstance;
import org.rundeck.client.api.model.AbortResult;
import org.rundeck.client.api.model.ExecOutput;
import org.rundeck.client.api.model.Execution;
import org.rundeck.client.api.model.JobItem;

import java.io.IOException;
import java.util.Properties;

public interface RundeckManager {
    AbortResult abortExecution(String id) throws IOException;

    ExecOutput getOutput(Long executionId, Long var2, Integer var3, Integer var4) throws IOException;

    ExecOutput getOutput(String executionId, Long var2, Long var3, Long var4) throws IOException;

    Execution getExecution(String id) throws IOException;

    String findJobId(String project, String name, String groupPath) throws IOException;

    JobItem findJob(String project, String name, String groupPath) throws IOException;

    JobItem getJob(String id) throws IOException;

    Execution runExecution(String jobId, Properties options, Properties nodeFilters) throws IOException;

    boolean ping() throws IOException;

    boolean testAuth() throws IOException;

    RundeckInstance getRundeckInstance();
}
