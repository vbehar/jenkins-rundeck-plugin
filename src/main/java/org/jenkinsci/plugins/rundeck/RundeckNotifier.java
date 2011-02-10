package org.jenkinsci.plugins.rundeck;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.rundeck.RundeckInstance.RundeckJobSchedulingException;
import org.jenkinsci.plugins.rundeck.RundeckInstance.RundeckLoginException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Jenkins {@link Notifier} that schedule a job execution on RunDeck (via the {@link RundeckInstance})
 * 
 * @author Vincent Behar
 */
public class RundeckNotifier extends Notifier {

    private final String groupPath;

    private final String jobName;

    private final String[] options;

    private final String tag;

    private final Boolean shouldFailTheBuild;

    @DataBoundConstructor
    public RundeckNotifier(String groupPath, String jobName, String[] options, String tag, Boolean shouldFailTheBuild) {
        this.groupPath = groupPath;
        this.jobName = jobName;
        this.options = options;
        this.tag = tag;
        this.shouldFailTheBuild = shouldFailTheBuild;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        RundeckInstance rundeck = getDescriptor().getRundeckInstance();

        if (rundeck == null || !rundeck.isConfigurationValid()) {
            listener.getLogger().println("Rundeck configuration is not valid ! " + rundeck);
            return false;
        }
        if (!rundeck.isAlive()) {
            listener.getLogger().println("Rundeck is not running !");
            return false;
        }

        if (StringUtils.isBlank(tag)) {
            listener.getLogger().println("Notifying rundeck...");
            return notifyRundeck(rundeck, listener);
        }

        for (Entry changeLog : build.getChangeSet()) {
            if (StringUtils.containsIgnoreCase(changeLog.getMsg(), tag)) {
                listener.getLogger().println("Found " + tag + " in changelog (from " + changeLog.getAuthor().getId()
                                             + ") - Notifying rundeck...");
                return notifyRundeck(rundeck, listener);
            }
        }

        return true;
    }

    /**
     * Schedule a job execution on RunDeck
     * 
     * @param rundeck instance to notify
     * @param listener for logging the result
     * @return true if successful, false otherwise
     */
    private boolean notifyRundeck(RundeckInstance rundeck, BuildListener listener) {
        try {
            rundeck.scheduleJobExecution(groupPath, jobName, options);
            listener.getLogger().println("Notification succeeded !");
            return true;
        } catch (RundeckLoginException e) {
            listener.getLogger().println("Login failed on " + rundeck + " : " + e.getMessage());
            return false;
        } catch (RundeckJobSchedulingException e) {
            listener.getLogger().println("Scheduling failed for job " + groupPath + "/" + jobName + " on " + rundeck
                                         + " : " + e.getMessage());
            return false;
        }
    }

    /**
     * if we should not fail the build, we need to run after finalized, so that the result of "perform" is not used by
     * jenkins
     */
    @Override
    public boolean needsToRunAfterFinalized() {
        return !shouldFailTheBuild;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public String getGroupPath() {
        return groupPath;
    }

    public String getJobName() {
        return jobName;
    }

    /**
     * @return a multi-lines string representation, for use in the view (jelly textarea)
     */
    public String getOptions() {
        return StringUtils.join(options, '\n');
    }

    public String getTag() {
        return tag;
    }

    public Boolean getShouldFailTheBuild() {
        return shouldFailTheBuild;
    }

    @Override
    public RundeckDescriptor getDescriptor() {
        return (RundeckDescriptor) super.getDescriptor();
    }

    @Extension(ordinal = 1000)
    public static final class RundeckDescriptor extends BuildStepDescriptor<Publisher> {

        private RundeckInstance rundeckInstance;

        public RundeckDescriptor() {
            super();
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            rundeckInstance = new RundeckInstance(json.getString("url"),
                                                  json.getString("login"),
                                                  json.getString("password"));

            save();
            return super.configure(req, json);
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new RundeckNotifier(formData.getString("groupPath"),
                                       formData.getString("jobName"),
                                       StringUtils.split(formData.getString("options"), '\n'),
                                       formData.getString("tag"),
                                       formData.getBoolean("shouldFailTheBuild"));
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "RunDeck";
        }

        public RundeckInstance getRundeckInstance() {
            return rundeckInstance;
        }

        public void setRundeckInstance(RundeckInstance rundeckInstance) {
            this.rundeckInstance = rundeckInstance;
        }

    }

}
