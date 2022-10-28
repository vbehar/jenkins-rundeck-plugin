package org.jenkinsci.plugins.rundeck;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import javax.servlet.http.HttpServletResponse;

import jenkins.model.Jenkins;
import org.apache.commons.lang.StringEscapeUtils;
import org.jenkinsci.plugins.rundeck.client.ExecutionData;
import org.jenkinsci.plugins.rundeck.util.ParseJson;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.rundeck.client.api.model.Execution;
import org.rundeck.client.api.model.JobItem;

/**
 * Listener for Rundeck WebHook notifications (see http://rundeck.org/docs/manual/jobs.html#webhooks), will trigger a
 * build using {@link RundeckTrigger}
 *
 * @author Vincent Behar
 */
public class WebHookListener {

    static final String TOKEN = "rundeckTriggerToken";

    @RequirePOST
    public void doIndex(StaplerRequest request, StaplerResponse response) {

        // read request body / parse Rundeck execution
        try{

            String token = request.getHeader(TOKEN);

            Gson gson = new Gson();
            Reader reader = new InputStreamReader(request.getInputStream(), "UTF-8");
            JsonElement jsonElement = gson.fromJson(reader, JsonElement.class);
            JsonElement jsonElementCleaned = ParseJson.clean(jsonElement);
            Execution execution = gson.fromJson(jsonElementCleaned, Execution.class);
            ExecutionData executionSafeData = new ExecutionData(execution);

            // write a basic response
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/plain");

            // notify all registered triggers
            for (AbstractProject<?, ?> job : Jenkins.get().getAllItems(AbstractProject.class)) {
                RundeckTrigger trigger = job.getTrigger(RundeckTrigger.class);
                if (trigger != null) {
                    if (trigger.shouldScheduleBuild(executionSafeData, token)) {
                        RundeckTrigger.RundeckTriggerCheckResult result = trigger.validateExecution(executionSafeData);
                        if (result.isValid()) {
                            response.getWriter().append("[\"Triggering:\" : \"" + job.getFullDisplayName() + "\"\n");
                            response.getWriter().append("\"Execution\" : \"" + execution.getJob().getName() + "\"]\n");
                            trigger.onNotification(executionSafeData);
                        } else {
                            response.getWriter().append("{\"Error:\" : \"" + result.getMessage() + "\"}");
                            response.setStatus(400);
                        }
                    }
                }
            }
        }catch (JsonSyntaxException e){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain");
            try {
                response.getWriter().append(e.getMessage());
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }

        }catch (Exception e){
            throw new RuntimeException("Something failed!", e);
        }

    }



}
