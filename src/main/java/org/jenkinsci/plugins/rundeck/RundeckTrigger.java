package org.jenkinsci.plugins.rundeck;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import hudson.util.ListBoxModel;
import hudson.util.Secret;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.rundeck.client.ExecutionData;
import org.jenkinsci.plugins.rundeck.client.RundeckClientManager;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.rundeck.client.api.model.Execution;
import org.rundeck.client.api.model.JobItem;

/**
 * Triggers a build when we receive a WebHook notification from Rundeck.
 *
 * @author Vincent Behar
 */
public class RundeckTrigger extends Trigger<AbstractProject<?, ?>> {

    private final Boolean filterJobs;

    private final List<String> jobsIdentifiers;

    private final List<String> executionStatuses;

    private final Secret token;


    @DataBoundConstructor
    public RundeckTrigger(Boolean filterJobs, List<String> jobsIdentifiers, List<String> executionStatuses, Secret token) {
        this.filterJobs = filterJobs != null ? filterJobs : false;
        this.jobsIdentifiers = jobsIdentifiers != null ? jobsIdentifiers : new ArrayList<String>();
        this.executionStatuses = executionStatuses != null ? executionStatuses : Arrays.asList("SUCCEEDED");
        this.token = this.filterJobs ? null:token;

    }

    /**
     * Called when we receive a Rundeck notification
     *
     * @param execution at the origin of the notification
     */
    public void onNotification(ExecutionData execution) {
        if(job != null){
            job.scheduleBuild(new RundeckCause(execution));
        }
    }

    public RundeckTriggerCheckResult validateExecution(ExecutionData execution){
        RundeckNotifier.RundeckDescriptor descriptor = new RundeckNotifier.RundeckDescriptor();
        // Map<String, RundeckInstance> instances = descriptor.getRundeckInstances();

        RundeckInstance rundeckSelectedInstance = null;
        // for (Map.Entry<String,RundeckInstance> instanceMap  : instances.entrySet()){
        //     RundeckInstance rundeckInstance = instanceMap.getValue();
        //     if(execution.getHref() != null && execution.getHref().toLowerCase().startsWith(rundeckInstance.getUrl().toLowerCase())){
        //         rundeckSelectedInstance = rundeckInstance;
        //     }
        // }
        for(RundeckInstance rundeckInstance : descriptor.getRundeckInstances()) {
           if(execution.getHref() != null && execution.getHref().toLowerCase().startsWith(rundeckInstance.getUrl().toLowerCase())){
                rundeckSelectedInstance = rundeckInstance;
            }
        }

        if(rundeckSelectedInstance != null){
            return validateRundeckExecution(rundeckSelectedInstance, execution);
        }

        return new RundeckTrigger.RundeckTriggerCheckResult("Rundeck instance not found", false);
    }

    private RundeckTriggerCheckResult validateRundeckExecution(RundeckInstance rundeckInstance, ExecutionData executionData){

        RundeckClientManager rundeck = RundeckInstanceBuilder.createClient(rundeckInstance);

        try {
            Execution execution = rundeck.getExecution(executionData.getId());
            if(execution!=null){

                if(
                   execution.getJob().getId().equals(executionData.getJob().getId()) &&
                   execution.getDateStarted().unixtime == executionData.getDateStarted().unixtime
                ){
                    return new RundeckTriggerCheckResult("OK", true);
                }else{
                    return new RundeckTriggerCheckResult("Execution doesn't match with original values", false);
                }

            }
        } catch (Exception e) {
            return new RundeckTriggerCheckResult(e.getMessage(), false);
        }

        return new RundeckTriggerCheckResult("execution not found", false);
    }

    /**
     * Filter notifications based on the {@link Execution} and the trigger configuration
     *
     * @param execution at the origin of the notification
     * @return true if we should schedule a new build, false otherwise
     */
    public boolean shouldScheduleBuild(ExecutionData execution, String requestToken) throws UnsupportedEncodingException {
        if (!filterJobs) {
            if(requestToken == null){
                return false;
            }

            if(this.token != null &&  MessageDigest.isEqual(this.token.getPlainText().getBytes(StandardCharsets.UTF_8), requestToken.getBytes(StandardCharsets.UTF_8))){
                return true;
            }

            return false;
        }

        if (!executionStatuses.contains(execution.getStatus().toUpperCase())) {
            return false;
        }
        for (String jobIdentifier : jobsIdentifiers) {
            if (identifierMatchesJob(jobIdentifier, execution.getJob())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the given jobIdentifier matches (= identifies) the given job
     *
     * @param jobIdentifier could be either a job's UUID, or a reference to a job in the format "project:group/job"
     * @param job to test
     * @return true if it matches, false otherwise
     */
    private boolean identifierMatchesJob(String jobIdentifier, JobItem job) {
        if (job == null || StringUtils.isBlank(jobIdentifier)) {
            return false;
        }

        // UUID
        if (StringUtils.equalsIgnoreCase(job.getId(), jobIdentifier)) {
            return true;
        }

        String fullname = job.getName();
        if (job.getGroup() != null) {
            fullname = job.getGroup()+"/"+job.getName();
        }
        // "project:group/job" reference
        String jobReference = job.getProject() + ":" + fullname;
        if (StringUtils.equalsIgnoreCase(jobReference, jobIdentifier)) {
            return true;
        }

        return false;
    }

    public Boolean isTokenConfigured(){
        if(token!=null){
            return true;
        }
        return false;
    }


    public String getRandomValue() {
        if(token==null){
            UUID uuid = UUID.randomUUID();
            String uuidAsString = uuid.toString();
            return uuidAsString;
        }else{
            return token.getPlainText();
        }
    }


    public Boolean getFilterJobs() {
        return filterJobs;
    }

    public List<String> getJobsIdentifiers() {
        return jobsIdentifiers;
    }

    public List<String> getExecutionStatuses() {
        return executionStatuses;
    }

    public Secret getToken() {
        return token;
    }

    @Override
    public RundeckDescriptor getDescriptor() {
        return (RundeckDescriptor) super.getDescriptor();
    }

    @Extension
    public static class RundeckDescriptor extends TriggerDescriptor {

        public RundeckDescriptor() {
            super();
            load();
        }

        @Override
        public Trigger<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            Secret tokenSecret=null;
            try{
                String token = formData.getJSONObject("filterJobs").getString("token");
                if(token!=null){
                    tokenSecret = Secret.fromString(formData.getJSONObject("filterJobs").getString("token"));
                }
            }catch(Exception e){
                tokenSecret = null;
            }

            return new RundeckTrigger(formData.getJSONObject("filterJobs").getBoolean("value"),
                                      bindJSONToList(formData.getJSONObject("filterJobs").get("jobsIdentifiers")),
                                      bindJSONToList(formData.get("executionStatuses")),
                                      tokenSecret
                    );
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return "Build when we receive a notification from Rundeck";
        }

        public String getDefaultRandomValue() {
            UUID uuid = UUID.randomUUID();
            String uuidAsString = uuid.toString();
            return uuidAsString;
        }

        /**
         * Simplistic version of StaplerRequest.bindJSONToList, in order to use a List of String
         */
        private List<String> bindJSONToList(Object src) {
            List<String> result = new ArrayList<String>();
            if (src instanceof String) {
                result.add((String) src);
            } else if (src instanceof JSONObject) {
                result.add(((JSONObject) src).getString("value"));
            } else if (src instanceof JSONArray) {
                for (Object elem : (JSONArray) src) {
                    if (elem instanceof String) {
                        result.add((String) elem);
                    } else if (elem instanceof JSONObject) {
                        result.add(((JSONObject) elem).getString("value"));
                    }
                }
            }
            return result;
        }
    }

    static class RundeckTriggerCheckResult {
        String message;
        boolean valid;

        public RundeckTriggerCheckResult(String message, boolean result) {
            this.message = message;
            this.valid = result;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }
    }


}
