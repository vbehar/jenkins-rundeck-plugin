package org.jenkinsci.plugins.rundeck;

import hudson.model.AbstractProject;
import hudson.model.Hudson;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.dom4j.Document;
import org.jenkinsci.plugins.rundeck.util.ParserXML;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.rundeck.api.domain.RundeckExecution;
import org.rundeck.api.parser.ExecutionParser;

/**
 * Listener for Rundeck WebHook notifications (see http://rundeck.org/docs/manual/jobs.html#webhooks), will trigger a
 * build using {@link RundeckTrigger}
 *
 * @author Vincent Behar
 */
public class WebHookListener {

    @RequirePOST
    public void doIndex(StaplerRequest request, StaplerResponse response) throws IOException {

        // read request body / parse Rundeck execution
        Document document = null;

        try{
            document = ParserXML.loadDocument(request.getInputStream());

            IOUtils.closeQuietly(request.getInputStream());
            ExecutionParser parser = new ExecutionParser("notification/executions/execution");
            RundeckExecution execution = parser.parseXmlNode(document);

            // write a basic response
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/plain");

            // notify all registered triggers
            for (AbstractProject<?, ?> job : Hudson.getInstance().getAllItems(AbstractProject.class)) {
                RundeckTrigger trigger = job.getTrigger(RundeckTrigger.class);
                if (trigger != null) {
                    response.getWriter().append("[\"Triggering:\" : \""+job.getFullDisplayName()+"\"\n");
                    response.getWriter().append("\"Execution\" : \"" + execution.getJob()+"\"]\n");
                    trigger.onNotification(execution);
                }
            }
        }catch (Exception e){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain");
            response.getWriter().append(e.getMessage());
        }

    }

}
