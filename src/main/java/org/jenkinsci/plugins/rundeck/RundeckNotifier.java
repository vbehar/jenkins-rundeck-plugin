package org.jenkinsci.plugins.rundeck;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildBadgeAction;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.rundeck.RundeckInstance.RundeckJobSchedulingException;
import org.jenkinsci.plugins.rundeck.RundeckInstance.RundeckLoginException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
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
        if (build.getResult() != Result.SUCCESS) {
            return true;
        }

        RundeckInstance rundeck = getDescriptor().getRundeckInstance();

        if (rundeck == null || !rundeck.isConfigurationValid()) {
            listener.getLogger().println("RunDeck configuration is not valid ! " + rundeck);
            return false;
        }
        if (!rundeck.isAlive()) {
            listener.getLogger().println("RunDeck is not running !");
            return false;
        }

        if (StringUtils.isBlank(tag)) {
            listener.getLogger().println("Notifying RunDeck...");
            return notifyRundeck(rundeck, build, listener);
        }

        for (Entry changeLog : build.getChangeSet()) {
            if (StringUtils.containsIgnoreCase(changeLog.getMsg(), tag)) {
                listener.getLogger().println("Found " + tag + " in changelog (from " + changeLog.getAuthor().getId()
                                             + ") - Notifying RunDeck...");
                return notifyRundeck(rundeck, build, listener);
            }
        }

        return true;
    }

    /**
     * Schedule a job execution on RunDeck
     * 
     * @param rundeck instance to notify
     * @param build for adding actions
     * @param listener for logging the result
     * @return true if successful, false otherwise
     */
    private boolean notifyRundeck(RundeckInstance rundeck, AbstractBuild<?, ?> build, BuildListener listener) {
        try {
            String executionUrl = rundeck.scheduleJobExecution(groupPath, jobName, options);
            build.addAction(new RundeckExecutionBuildBadgeAction(executionUrl));
            listener.getLogger().println("Notification succeeded ! Execution url : " + executionUrl);
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
     * If we should not fail the build, we need to run after finalized, so that the result of "perform" is not used by
     * Jenkins
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

        public FormValidation doTestConnection(@QueryParameter("rundeck.url") final String url,
                @QueryParameter("rundeck.login") final String login,
                @QueryParameter("rundeck.password") final String password) {
            RundeckInstance rundeck = new RundeckInstance(url, login, password);
            if (!rundeck.isConfigurationValid()) {
                return FormValidation.error("RunDeck configuration is not valid !");
            }
            if (!rundeck.isAlive()) {
                return FormValidation.error("We couldn't find a live RunDeck instance at %s", rundeck.getUrl());
            }
            if (!rundeck.isLoginValid()) {
                return FormValidation.error("Your credentials for the user %s are not valid !", rundeck.getLogin());
            }
            return FormValidation.ok("Your RunDeck instance is alive, and your credentials are valid !");
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

    /**
     * {@link BuildBadgeAction} used to display a RunDeck icon + a link to the RunDeck execution page, on the Jenkins
     * build history and build result page.
     */
    public static class RundeckExecutionBuildBadgeAction implements BuildBadgeAction {

        private final String executionUrl;

        public RundeckExecutionBuildBadgeAction(String executionUrl) {
            super();
            this.executionUrl = executionUrl;
        }

        public String getDisplayName() {
            return "RunDeck Execution Result";
        }

        public String getIconFileName() {
            return "/plugin/rundeck/images/rundeck_24x24.png";
        }

        public String getUrlName() {
            return executionUrl;
        }

    }

}
