package org.jenkinsci.plugins.rundeck.client;

import okhttp3.ResponseBody;
import org.jenkinsci.plugins.rundeck.RundeckInstance;
import org.rundeck.client.RundeckClient;
import org.rundeck.client.api.RundeckApi;
import org.rundeck.client.api.model.*;
import org.rundeck.client.api.model.scheduler.ScheduledJobItem;
import org.rundeck.client.util.Client;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RundeckClientManager {

    final public static Integer API_VERSION = 32;
    private RundeckInstance rundeckInstance;
    private Client<RundeckApi> client;

    public RundeckClientManager(RundeckInstance rundeckInstance) {
        this.rundeckInstance = rundeckInstance;
    }

    public RundeckInstance getRundeckInstance() {
        return rundeckInstance;
    }

    public void setRundeckInstance(RundeckInstance rundeckInstance) {
        this.rundeckInstance = rundeckInstance;
    }

    public Client<RundeckApi> getClient() {
        return client;
    }

    public void setClient(Client<RundeckApi> client) {
        this.client = client;
    }

    public void buildClient(){
        if(client == null){
            RundeckClient.Builder builder = RundeckClient.builder().baseUrl(rundeckInstance.getUrl());

            if(rundeckInstance.getToken()!=null){
                builder.tokenAuth(rundeckInstance.getToken().getPlainText());
            }
            if(rundeckInstance.getLogin() != null && rundeckInstance.getPassword()!=null){
                builder.passwordAuth(rundeckInstance.getLogin(),rundeckInstance.getPassword().getPlainText() );
            }

            if(rundeckInstance.getApiVersion()!=null){
                builder.apiVersion(rundeckInstance.getApiVersion());
            }

            if(rundeckInstance.isSslCertificateTrustAllowSelfSigned()){
                builder.insecureSSL(true);
            }

            client = builder.build();
        }

    }

    public AbortResult abortExecution(String id) throws IOException {
        Call<AbortResult> rundeckOutputCall = client.getService().abortExecution(id);
        Response<AbortResult> abortResultResponse = rundeckOutputCall.execute();
        return abortResultResponse.body();
    }

    public ExecOutput getOutput(Long executionId, Long var2, Integer var3, Integer var4) throws IOException {
        return this.getOutput(executionId.toString(), var2,(long)var3,(long)var4);
    }

    public ExecOutput getOutput(String executionId, Long var2, Long var3, Long var4) throws IOException {
        Call<ExecOutput> rundeckOutputCall = client.getService().getOutput(executionId, var2, var3, var4);
        Response<ExecOutput> execOutputResponse = rundeckOutputCall.execute();
        return execOutputResponse.body();
    }


    public static enum ExecutionStatus {
        RUNNING("running"),
        SUCCEEDED("succeeded"),
        FAILED("failed"),
        ABORTED("aborted"),
        FAILED_WITH_RETRY("failed-with-retry"),
        TIMEDOUT("timedout");

        private String value;

        private ExecutionStatus(String value) {
            this.value = value;
        }

    }

    public Execution getExecution(String id) throws IOException {
        Call<Execution> callExecutions = client.getService().getExecution(id);
        Response<Execution> executionResponse = callExecutions.execute();

        if(executionResponse.isSuccessful()){
            return executionResponse.body();
        }

        return null;
    }

    public String findJobId(String project, String name, String groupPath) throws IOException {
        JobItem job = findJob(project, name, groupPath);

        if(job!=null){
            return job.getId();
        }
        return null;
    }

    public JobItem findJob(String project, String name, String groupPath) throws IOException {
        Call<List<JobItem>> listCall =  client.getService().listJobs(project, name, groupPath,"","");
        Response<List<JobItem>> execute = listCall.execute();

        if(execute.isSuccessful()){
            List<JobItem> body = execute.body();

            if(body.size()==0){
                return null;
            }
            JobItem foundJob = null;
            for(JobItem job:body){
                foundJob = job;
            }

            return foundJob;
        }else{
            return null;
        }
    }

    public JobItem getJob(String id) throws IOException {
        Call<ScheduledJobItem>  jobCall =  client.getService().getJobInfo(id);
        Response<ScheduledJobItem> scheduledJobItemResponse = jobCall.execute();
        return scheduledJobItemResponse.body();
    }

    public Response<Execution> runExecution(String jobId, String options, String nodeFilters) throws IOException {
        Map<String, String> inputOptions = new HashMap<>();
        JobRun jobRun = new JobRun();
        jobRun.setOptions(inputOptions);
        jobRun.setFilter(nodeFilters);
        Call<Execution> callExecutions = client.getService().runJob(jobId, jobRun);

        return callExecutions.execute();

    }

    public boolean ping() throws IOException {
        Response<ResponseBody> result = client.getService().getPing().execute();
        if(result.isSuccessful()){
            return true;
        }
        return false;
    }

    public boolean testAuth() throws IOException {
        Response<SystemInfo> result = client.getService().systemInfo().execute();
        if(result.isSuccessful()){
            return true;
        }
        return false;
    }



}
