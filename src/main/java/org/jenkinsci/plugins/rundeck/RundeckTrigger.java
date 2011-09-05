package org.jenkinsci.plugins.rundeck;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.rundeck.api.domain.RundeckExecution;

/**
 * Triggers a build when we receive a WebHook notification from RunDeck.
 * 
 * @author Vincent Behar
 */
public class RundeckTrigger extends Trigger<AbstractProject<?, ?>> {

    @DataBoundConstructor
    public RundeckTrigger() {
    }

    public void onNotification(RundeckExecution execution) {
        job.scheduleBuild(new RundeckCause(execution));
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
        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return "Build when we receive a notification from RunDeck";
        }

    }

}
