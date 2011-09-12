package org.jenkinsci.plugins.rundeck;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.rundeck.api.domain.RundeckExecution;

/**
 * Triggers a build when we receive a WebHook notification from RunDeck.
 * 
 * @author Vincent Behar
 */
public class RundeckTrigger extends Trigger<AbstractProject<?, ?>> {

    private final Boolean filterJobs;

    private final List<RundeckJobIdentifier> jobsIdentifiers;

    @DataBoundConstructor
    public RundeckTrigger(Boolean filterJobs, List<RundeckJobIdentifier> jobsIdentifiers) {
        this.filterJobs = filterJobs != null ? filterJobs : false;
        this.jobsIdentifiers = jobsIdentifiers != null ? jobsIdentifiers : new ArrayList<RundeckJobIdentifier>();
    }

    /**
     * Called when we receive a RunDeck notification
     * 
     * @param execution at the origin of the notification
     */
    public void onNotification(RundeckExecution execution) {
        if (shouldScheduleBuild(execution)) {
            job.scheduleBuild(new RundeckCause(execution));
        }
    }

    /**
     * Filter notifications based on the {@link RundeckExecution} and the trigger configuration
     * 
     * @param execution at the origin of the notification
     * @return true if we should schedule a new build, false otherwise
     */
    private boolean shouldScheduleBuild(RundeckExecution execution) {
        if (!filterJobs) {
            return true;
        }
        for (RundeckJobIdentifier identifier : jobsIdentifiers) {
            if (identifier.matches(execution.getJob())) {
                return true;
            }
        }
        return false;
    }

    public Boolean getFilterJobs() {
        return filterJobs;
    }

    public List<RundeckJobIdentifier> getJobsIdentifiers() {
        return jobsIdentifiers;
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
            return new RundeckTrigger(formData.getJSONObject("filterJobs").getBoolean("value"),
                                      req.bindJSONToList(RundeckJobIdentifier.class,
                                                         formData.getJSONObject("filterJobs").get("jobsIdentifiers")));
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return "Build when we receive a notification from RunDeck";
        }
    }
}
