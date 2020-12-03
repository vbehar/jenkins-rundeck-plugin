package org.jenkinsci.plugins.rundeck;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import javax.servlet.http.HttpServletResponse;
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

    @RequirePOST
    public void doIndex(StaplerRequest request, StaplerResponse response) {

        // read request body / parse Rundeck execution
        try{

            Gson gson = new Gson();

            Reader reader = new InputStreamReader(request.getInputStream(), "UTF-8");
            Execution execution = gson.fromJson(reader, Execution.class);

            // write a basic response
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/plain");

            // notify all registered triggers
            for (AbstractProject<?, ?> job : Hudson.getInstance().getAllItems(AbstractProject.class)) {
                RundeckTrigger trigger = job.getTrigger(RundeckTrigger.class);
                if (trigger != null) {
                    response.getWriter().append("[\"Triggering:\" : \""+job.getFullDisplayName()+"\"\n");
                    response.getWriter().append("\"Execution\" : \"" + execution.getJob().getName()+"\"]\n");
                    trigger.onNotification(execution);
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
