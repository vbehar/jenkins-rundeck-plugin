package org.rundeck.api;


import java.util.Date;

/**
 * Just a mock {@link RundeckClient} which is always successful
 */
public class MockRundeckClient { //extends RundeckClient {

    private static final long serialVersionUID = 1L;

    /*
    public MockRundeckClient() {
        super("http://localhost:4440");
        setLogin("admin");
        setPassword("admin");
    }

    public MockRundeckClient(String user, String password) {
        super("http://localhost:4440");
        setLogin(user);
        setPassword(password);
    }

    @Override
    public void ping() {
        // successful
    }

    public void testCredentials() {
        // successful
    }

    @Override
    public RundeckExecution triggerJob(RunJob job) {
        return initExecution(RundeckExecution.ExecutionStatus.RUNNING);
    }

    @Override
    public RundeckExecution getExecution(Long executionId) {
        return initExecution(RundeckExecution.ExecutionStatus.SUCCEEDED);
    }

    @Override
    public RundeckJob getJob(String jobId) {
        RundeckJob job = new RundeckJob();
        return job;
    }

    private RundeckExecution initExecution(RundeckExecution.ExecutionStatus status) {
        RundeckExecution execution = new RundeckExecution();
        execution.setId(1L);
        execution.setUrl("http://localhost:4440/execution/follow/1");
        execution.setStatus(status);
        execution.setStartedAt(new Date(1310159014640L));
        if (RundeckExecution.ExecutionStatus.SUCCEEDED.equals(status)) {
            Date endedAt = execution.getStartedAt();
            endedAt = DateUtils.addMinutes(endedAt, 3);
            endedAt = DateUtils.addSeconds(endedAt, 27);
            execution.setEndedAt(endedAt);
        }
        return execution;
    }
    */

}
