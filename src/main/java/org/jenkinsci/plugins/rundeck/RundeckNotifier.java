package org.jenkinsci.plugins.rundeck;

import static java.lang.String.format;

import hudson.AbortException;
import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Action;
import hudson.model.BuildBadgeAction;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Hudson;
import hudson.model.Run.Artifact;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.rundeck.cache.DummyRundeckJobCache;
import org.jenkinsci.plugins.rundeck.cache.RundeckJobCache;
import org.jenkinsci.plugins.rundeck.cache.InMemoryRundeckJobCache;
import org.jenkinsci.plugins.rundeck.cache.RundeckJobCacheConfig;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.rundeck.api.RunJobBuilder;
import org.rundeck.api.RundeckApiException;
import org.rundeck.api.RundeckApiException.RundeckApiLoginException;
import org.rundeck.api.RundeckClient;
import org.rundeck.api.RundeckClientBuilder;
import org.rundeck.api.domain.RundeckAbort;
import org.rundeck.api.domain.RundeckExecution;
import org.rundeck.api.domain.RundeckExecution.ExecutionStatus;
import org.rundeck.api.domain.RundeckJob;
import org.rundeck.api.domain.RundeckOutput;
import org.rundeck.api.domain.RundeckOutputEntry;

import javax.annotation.Nonnull;

/**
 * Jenkins {@link Notifier} that runs a job on Rundeck (via the {@link RundeckClient})
 * 
 * @author Vincent Behar
 */
public class RundeckNotifier extends Notifier implements SimpleBuildStep {

    private static final Logger log = Logger.getLogger(RundeckNotifier.class.getName());

    /** Pattern used for the token expansion of $ARTIFACT_NAME{regex} */
    private static final transient Pattern TOKEN_ARTIFACT_NAME_PATTERN = Pattern.compile("\\$ARTIFACT_NAME\\{(.+)\\}");

    /** Pattern used for extracting the job reference (project:group/name) */
    private static final transient Pattern JOB_REFERENCE_PATTERN = Pattern.compile("^([^:]+?):(.*?)\\/?([^/]+)$");

    private static final int DELAY_BETWEEN_POLLS_IN_MILLIS = 5000;

    private String rundeckInstance; //TODO: Could be renamed to rundeckInstanceName

    private final String jobId;

    private final String options;

    private final String nodeFilters;

    private transient final String tag;

    private String[] tags;

    private final Boolean shouldWaitForRundeckJob;

    private final Boolean shouldFailTheBuild;

    private final Boolean includeRundeckLogs;

    private final Boolean tailLog;

    RundeckNotifier(String rundeckInstance, String jobId, String options, String nodeFilters, String tag,
            Boolean shouldWaitForRundeckJob, Boolean shouldFailTheBuild) {
       this(rundeckInstance, jobId, options, nodeFilters, tag, shouldWaitForRundeckJob, shouldFailTheBuild, false, false);
    }

    @DataBoundConstructor
    public RundeckNotifier(String rundeckInstance, String jobId, String options, String nodeFilters, String tags,
            Boolean shouldWaitForRundeckJob, Boolean shouldFailTheBuild, Boolean includeRundeckLogs, Boolean tailLog) {
        this.rundeckInstance = rundeckInstance;
        this.jobId = jobId;
        this.options = options;
        this.nodeFilters = nodeFilters;
        this.tags = extracttags(tags,",");
        this.tag = null;
        this.shouldWaitForRundeckJob = shouldWaitForRundeckJob;
        this.shouldFailTheBuild = shouldFailTheBuild;
        this.includeRundeckLogs = includeRundeckLogs;
        this.tailLog = tailLog;
    }

    public Object readResolve() {
        if (StringUtils.isEmpty(rundeckInstance)) {
            this.rundeckInstance = "Default";
        }
        if (tags == null) {
            this.tags = extracttags(this.tag, ",");
        }
        return this;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        if (run.getResult() != Result.SUCCESS && run.getResult() != null) {
            return;
        }

        RundeckClient rundeck = getDescriptor().getRundeckInstance(this.rundeckInstance);

        if (rundeck == null) {
            listener.getLogger().println("Rundeck configuration is not valid !");
            throw new AbortException("Rundeck configuration is not valid !");
        }
        try {
            rundeck.ping();
        } catch (RundeckApiException e) {
            listener.getLogger().println("Rundeck is not running !");
            throw new AbortException("Rundeck is not running !");
        }

        if (shouldNotifyRundeck(run, listener)) {
            notifyRundeck(rundeck, run, listener);
        }
    }

    private ChangeLogSet<? extends Entry> getChangeSet(@Nonnull Run<?, ?> run) {
        if (run instanceof AbstractBuild<?,?>) {
            AbstractBuild<?,?> b = (AbstractBuild<?,?>) run;
            return b.getChangeSet();
        } else {
            return ChangeLogSet.createEmpty(run);
        }
    }

    /**
     * Check if we need to notify Rundeck for this build. If we have a tag, we will look for it in the changelog of the
     * build and in the changelog of all upstream builds.
     * 
     * @param build for checking the changelog
     * @param listener for logging the result
     * @return true if we should notify Rundeck, false otherwise
     */
    private boolean shouldNotifyRundeck(@Nonnull Run<?, ?> build, @Nonnull TaskListener listener) {
        if (tags.length == 0) {
            listener.getLogger().println("Notifying Rundeck...");
            return true;
        }

        // check for the tag in the changelog
        for (Entry changeLog : getChangeSet(build)) {
            for(String tag: tags) {
                if (StringUtils.containsIgnoreCase(changeLog.getMsg(), tag)) {
                    listener.getLogger().println("Found " + tag + " in changelog (from " + changeLog.getAuthor().getId()
                            + ") - Notifying Rundeck...");
                    return true;
                }
            }
        }

        // if we have an upstream cause, check for the tag in the changelog from upstream
        for (Cause cause : build.getCauses()) {
            if (UpstreamCause.class.isInstance(cause)) {
                UpstreamCause upstreamCause = (UpstreamCause) cause;
                TopLevelItem item = Hudson.getInstance().getItem(upstreamCause.getUpstreamProject());
                if (AbstractProject.class.isInstance(item)) {
                    AbstractProject<?, ?> upstreamProject = (AbstractProject<?, ?>) item;
                    AbstractBuild<?, ?> upstreamBuild = upstreamProject.getBuildByNumber(upstreamCause.getUpstreamBuild());
                    if (upstreamBuild != null) {
                        for (Entry changeLog : upstreamBuild.getChangeSet()) {
                            for(String tag: tags) {
                                if (StringUtils.containsIgnoreCase(changeLog.getMsg(), tag)) {
                                    listener.getLogger().println("Found " + tag + " in changelog (from "
                                            + changeLog.getAuthor().getId() + ") in upstream build ("
                                            + upstreamBuild.getFullDisplayName()
                                            + ") - Notifying Rundeck...");
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Notify Rundeck : run a job on Rundeck
     * 
     * @param rundeck instance to notify
     * @param build for adding actions
     * @param listener for logging the result
     * @return true if successful, false otherwise
     */
    private void notifyRundeck(RundeckClient rundeck, Run<?, ?> build, TaskListener listener) throws AbortException {
        String runtimeJobId;
        // perform environment substitution before finding the rundeck job
        try {
            EnvVars env = build.getEnvironment(listener);
            runtimeJobId = env.expand(jobId);
            listener.getLogger().println("Looking for jobId : " + runtimeJobId);
        } catch (IOException | InterruptedException e) {
            listener.getLogger().println("Failed substituting environment in: " + jobId + " : " + e.getMessage());
            throw new AbortException("Failed substituting environment in: " + jobId + " : " + e.getMessage());
        }

        //if the jobId is in the form "project:[group/*]name", find the actual job ID first.
        String foundJobId = null;
        try {
            foundJobId = RundeckDescriptor.findJobId(runtimeJobId, rundeck);
        } catch (RundeckApiException e) {
            listener.getLogger().println("Failed to get job with the identifier : " + runtimeJobId + " : "+e.getMessage());
            throw new AbortException("Failed to get job with the identifier : " + runtimeJobId + " : "+e.getMessage());
        } catch (IllegalArgumentException e) {
            listener.getLogger().println("Failed to get job with the identifier : " + runtimeJobId + " : " +e.getMessage());
            throw new AbortException("Failed to get job with the identifier : " + runtimeJobId + " : " +e.getMessage());
        }
        if (foundJobId == null) {
            listener.getLogger().println("Could not find a job with the identifier : " + runtimeJobId);
            throw new AbortException("Could not find a job with the identifier : " + runtimeJobId);
        }
        try {
            RundeckExecution execution = rundeck.triggerJob(RunJobBuilder.builder()
                    .setJobId(foundJobId)
                    .setOptions(parseProperties(options, build, listener))
                    .setNodeFilters(parseProperties(nodeFilters, build, listener))
                    .build());

            listener.getLogger().printf("Notification succeeded ! Execution #%d, at %s (status : %s)%n",
                    execution.getId(), execution.getUrl(), execution.getStatus());
            build.addAction(new RundeckExecutionBuildBadgeAction(execution.getUrl()));

            if (Boolean.TRUE.equals(shouldWaitForRundeckJob)) {
                listener.getLogger().println("Waiting for Rundeck execution to finish...");

                if (Boolean.TRUE.equals(includeRundeckLogs) && Boolean.TRUE.equals(tailLog)) {
                    execution = waitTailingRundeckLogsAndReturnExecution(rundeck, listener, execution);
                } else {
                    execution = waitForRundeckExecutionToFinishAndReturnIt(rundeck, listener, execution);

                    if (Boolean.TRUE.equals(includeRundeckLogs)) {
                        getAndPrintRundeckLogsForExecution(rundeck, listener, execution.getId());
                    }
                }

                switch (execution.getStatus()) {
                    case SUCCEEDED:
                        return;
                    case ABORTED:
                    case FAILED:
                    case RUNNING:   //possible if it was unable to abort execution after an interruption
                        if (getShouldFailTheBuild())
                            build.setResult(Result.FAILURE);
                        throw new AbortException();
                    default:
                        throw new IllegalStateException(format("Unexpected executions status: %s", execution.getStatus()));
                }
            } else {
                return;
            }
        } catch (RundeckApiLoginException e) {
            listener.getLogger().println("Login failed on " + rundeck.getUrl() + " : " + e.getMessage());
            throw new AbortException("Login failed on " + rundeck.getUrl() + " : " + e.getMessage());
        } catch (RundeckApiException.RundeckApiTokenException e) {
            listener.getLogger().println("Token auth failed on " + rundeck.getUrl() + " : " + e.getMessage());
            throw new AbortException("Token auth failed on " + rundeck.getUrl() + " : " + e.getMessage());
        } catch (RundeckApiException e) {
            listener.getLogger().println("Error while talking to Rundeck's API at " + rundeck.getUrl() + " : "
                                         + e.getMessage());
            throw new AbortException("Error while talking to Rundeck's API at " + rundeck.getUrl() + " : " + e.getMessage());
        } catch (IllegalArgumentException e) {
            listener.getLogger().println("Configuration error : " + e.getMessage());
            throw new AbortException("Configuration error : " + e.getMessage());
        }
    }

    private RundeckExecution waitTailingRundeckLogsAndReturnExecution(RundeckClient rundeck, TaskListener listener, RundeckExecution execution) {
        listener.getLogger().println("BEGIN RUNDECK TAILED LOG OUTPUT");
        RunDeckLogTail runDeckLogTail = new RunDeckLogTail(rundeck, execution.getId());
        for (List<RundeckOutputEntry> aRunDeckLogTail : runDeckLogTail) {
            for (RundeckOutputEntry rundeckOutputEntry : aRunDeckLogTail) {
                listener.getLogger().println(String.format("[%s] [%s] %s",
                        new Object[]{ rundeckOutputEntry.getTime(), rundeckOutputEntry.getLevel(), rundeckOutputEntry.getMessage() }));
            }
        }
        listener.getLogger().println("END RUNDECK TAILED LOG OUTPUT");

        execution = rundeck.getExecution(execution.getId());
        logExecutionStatus(listener, execution, "finished");
        return execution;
    }

    private void logExecutionStatus(TaskListener listener, RundeckExecution execution, String operationName) {
        listener.getLogger().printf("Rundeck execution #%d %s in %s, with status : %s%n", execution.getId(), operationName,
                execution.getDuration(), execution.getStatus());
    }

    /**
     * Parse the given input (should be in the Java-Properties syntax) and expand Jenkins environment variables.
     *
     * @param input specified in the Java-Properties syntax (multi-line, key and value separated by = or :)
     * @param build for retrieving Jenkins environment variables
     * @param listener for retrieving Jenkins environment variables and logging the errors
     * @return A {@link Properties} instance (may be empty), or null if unable to parse the options
     */
    private Properties parseProperties(String input, Run<?, ?> build, TaskListener listener) {
        if (StringUtils.isBlank(input)) {
            return new Properties();
        }

        // try to expand jenkins env vars
        try {
            EnvVars envVars = build.getEnvironment(listener);
            input = Util.replaceMacro(input, envVars);
        } catch (Exception e) {
            listener.getLogger().println("Failed to expand environment variables : " + e.getMessage());
        }

        // expand our custom tokens : $ARTIFACT_NAME{regex} => name of the first matching artifact found
        // http://groups.google.com/group/rundeck-discuss/browse_thread/thread/94a6833b84fdc10b
        Matcher matcher = TOKEN_ARTIFACT_NAME_PATTERN.matcher(input);
        int idx = 0;
        while (matcher.reset(input).find(idx)) {
            idx = matcher.end();
            String regex = matcher.group(1);
            Pattern pattern = Pattern.compile(regex);
            for (@SuppressWarnings("rawtypes")
            Artifact artifact : build.getArtifacts()) {
                if (pattern.matcher(artifact.getFileName()).matches()) {
                    input = StringUtils.replace(input, matcher.group(0), artifact.getFileName());
                    idx = matcher.start() + artifact.getFileName().length();
                    break;
                }
            }
        }

        try {
            return Util.loadProperties(input);
        } catch (IOException e) {
            listener.getLogger().println("Failed to parse : " + input);
            listener.getLogger().println("Error : " + e.getMessage());
            return null;
        }
    }

    private RundeckExecution waitForRundeckExecutionToFinishAndReturnIt(RundeckClient rundeck, TaskListener listener,
                                                                        RundeckExecution execution) {
        try {
            while (ExecutionStatus.RUNNING.equals(execution.getStatus())) {
                Thread.sleep(DELAY_BETWEEN_POLLS_IN_MILLIS);
                execution = rundeck.getExecution(execution.getId());
            }
            logExecutionStatus(listener, execution, "finished");
        } catch (InterruptedException e) {
            listener.getLogger().println("Waiting was interrupted. Probably build was cancelled. Reason: " + e);
            listener.getLogger().println("Trying to abort Rundeck execution...");
            RundeckAbort rundeckAbort = rundeck.abortExecution(execution.getId());
            listener.getLogger().printf("Abort status: %s%n", rundeckAbort.getStatus());
            execution = rundeck.getExecution(execution.getId());
            logExecutionStatus(listener, execution, "aborted");
        }
        return execution;
    }

    private void getAndPrintRundeckLogsForExecution(RundeckClient rundeck, TaskListener listener, Long executionId) {
        listener.getLogger().println("BEGIN RUNDECK LOG OUTPUT");
        RundeckOutput rundeckOutput = rundeck.getExecutionOutput(executionId, 0, 0, 0);
        if (null != rundeckOutput) {
            List<RundeckOutputEntry> logEntries = rundeckOutput.getLogEntries();
            if (null != logEntries) {
                for (RundeckOutputEntry rundeckOutputEntry : logEntries) {
                    listener.getLogger().println(rundeckOutputEntry.getMessage());
                }
            }
        }
        listener.getLogger().println("END RUNDECK LOG OUTPUT");
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        try {
            return new RundeckJobProjectLinkerAction(rundeckInstance, getDescriptor().getRundeckInstance(this.rundeckInstance), jobId);
        } catch (RundeckApiException | IllegalArgumentException e) {
            log.warning(format("Unable to create rundeck job project linked action for '%s'. Exception: %s: %s", project.getDisplayName(),
                    e.getClass().getSimpleName(), e.getMessage()));
            return null;
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

    public String getRundeckInstance() {
        return this.rundeckInstance;
    }

    public String getJobIdentifier() {
        return jobId;
    }

    public String getJobId() {
        return jobId;
    }

    public String getOptions() {
        return options;
    }

    public String getNodeFilters() {
        return nodeFilters;
    }

    public String getTag() {
        StringBuilder builder = new StringBuilder();

        for (int i=0; i<tags.length; i++) {
            builder.append(tags[i]);

            if (i+1<tags.length) {
                builder.append(",");
            }
        }

        return builder.toString();
    }

    public String[] getTags() {
        return tags;
    }

    public Boolean getShouldWaitForRundeckJob() {
        return shouldWaitForRundeckJob;
    }

    public Boolean getShouldFailTheBuild() {
        return shouldFailTheBuild;
    }

    public Boolean getIncludeRundeckLogs() {
        return includeRundeckLogs;
    }

    public Boolean getTailLog() {
        return tailLog;
    }

    @Override
    public RundeckDescriptor getDescriptor() {
        return (RundeckDescriptor) super.getDescriptor();
    }

    @Extension(ordinal = 1000)
    public static final class RundeckDescriptor extends BuildStepDescriptor<Publisher> {

        @Deprecated
        private transient RundeckClient rundeckInstance;

        @CopyOnWrite
        private volatile Map<String, RundeckClient> rundeckInstances = new LinkedHashMap<>();

        private volatile transient RundeckJobCache rundeckJobCache = new DummyRundeckJobCache();

        private volatile RundeckJobCacheConfig rundeckJobCacheConfig = RundeckJobCacheConfig.initializeWithDefaultValues();

        public RundeckDescriptor() {
            super();
            load();
        }

        public synchronized void load() {
            super.load();
            initializeRundeckJobCache();
        }

        private void initializeRundeckJobCache() {
            if (rundeckJobCacheConfig.isEnabled()) {
                log.info("Rundeck job cache enabled. Using following configuration: " + rundeckJobCacheConfig);
                rundeckJobCache = new InMemoryRundeckJobCache(rundeckJobCacheConfig);
            } else {
                log.info("Rundeck job cache DISABLED.");
                rundeckJobCache.invalidate();
                rundeckJobCache = new DummyRundeckJobCache();
            }
        }

        // support backward compatibility
        protected Object readResolve() {
            if (rundeckInstance != null) {
                Map<String, RundeckClient> instance = new LinkedHashMap<String, RundeckClient>();
                instance.put("Default", rundeckInstance);
                this.setRundeckInstances(instance);
            }
            return this;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {

            JSONArray instances = json.optJSONArray("inst");

            if (instances == null) {
                instances = new JSONArray();

                if (json.optJSONObject("inst") != null) {
                    instances.add(json.getJSONObject("inst"));
                }
            }

            Map<String, RundeckClient> newInstances = new LinkedHashMap<String, RundeckClient>(instances.size());

            try {
                for (int i=0; i< instances.size(); i++) {
                    JSONObject instance = instances.getJSONObject(i);

                    if (!StringUtils.isEmpty(instance.getString("name"))) {
                        RundeckClientBuilder builder = RundeckClient.builder();
                        builder.url(instance.getString("url"));
                        if (instance.get("authtoken") != null && !"".equals(instance.getString("authtoken"))) {
                            builder.token(instance.getString("authtoken"));
                        } else {
                            builder.login(instance.getString("login"), instance.getString("password"));
                        }

                        if (instance.optInt("apiversion") > 0) {
                            builder.version(instance.getInt("apiversion"));
                        }
                        newInstances.put(instance.getString("name"), builder.build());
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warning(format("Unable to deserialize Rundeck instances fom JSON. %s: %s", e.getClass().getSimpleName(), e.getMessage()));
            }

            this.setRundeckInstances(newInstances);

            configureRundeckJobCache(json);

            save();
            return super.configure(req, json);
        }

        private void configureRundeckJobCache(JSONObject json) {
            boolean cacheEnabledAsBoolean = json.has("rundeckJobCacheEnabled");
            if (cacheEnabledAsBoolean == rundeckJobCacheConfig.isEnabled()) {   //nothing changed
                return;
            }

            rundeckJobCacheConfig.setEnabled(cacheEnabledAsBoolean);
            initializeRundeckJobCache();
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String rundeckInstance = formData.getString("rundeckInstance");
            String jobIdentifier = formData.getString("jobIdentifier");
            if (!jobIdentifier.contains("$")) {
                // Only check the job name if there are no environment variables to substitute
                RundeckJob job = null;
                try {
                    job = findJobUncached(jobIdentifier, this.getRundeckInstance(rundeckInstance));
                } catch (RundeckApiException | IllegalArgumentException e) {
                    throw new FormException("Failed to get job with the identifier : " + jobIdentifier, e, "jobIdentifier");
                }
                if (job == null) {
                    throw new FormException("Could not find a job with the identifier : " + jobIdentifier, "jobIdentifier");
                }
            }
            return new RundeckNotifier(rundeckInstance,
                                       jobIdentifier,
                                       formData.getString("options"),
                                       formData.getString("nodeFilters"),
                                       formData.getString("tag"),
                                       formData.getBoolean("shouldWaitForRundeckJob"),
                                       formData.getBoolean("shouldFailTheBuild"),
                                       formData.getBoolean("includeRundeckLogs"),
                                       formData.getBoolean("tailLog"));
        }

        @SuppressWarnings("unused")
        public FormValidation doDisplayCacheStatistics() {
            return FormValidation.ok(rundeckJobCache.logAndGetStats());
        }

        @SuppressWarnings("unused")
        public FormValidation doInvalidateCache() {
            rundeckJobCache.invalidate();
            return FormValidation.ok("Done");
        }

        @SuppressWarnings("unused")
        public FormValidation doTestConnection(@QueryParameter("rundeck.url") final String url,
                @QueryParameter("rundeck.login") final String login,
                @QueryParameter("rundeck.password") final String password,
                @QueryParameter(value = "rundeck.authtoken", fixEmpty = true) final String token,
                @QueryParameter(value = "rundeck.apiversion", fixEmpty = true) final Integer apiversion) {

            RundeckClient rundeck = null;
            RundeckClientBuilder builder = RundeckClient.builder().url(url);
            if (null != apiversion && apiversion > 0) {
                builder.version(apiversion);
            } else {
                builder.version(RundeckClient.API_VERSION);
            }
            try {
                if (null != token) {
                    rundeck = builder.token(token).build();
                } else {
                    rundeck = builder.login(login, password).build();
                }
            } catch (IllegalArgumentException e) {
                return FormValidation.error("Rundeck configuration is not valid ! %s", e.getMessage());
            }
            try {
                rundeck.ping();
            } catch (RundeckApiException e) {
                return FormValidation.error("We couldn't find a live Rundeck instance at %s", rundeck.getUrl());
            }
            try {
                rundeck.testAuth();
            } catch (RundeckApiLoginException e) {
                return FormValidation.error("Your credentials for the user %s are not valid !", rundeck.getLogin());
            } catch (RundeckApiException.RundeckApiTokenException e) {
                return FormValidation.error("Your token authentication is not valid!");
            }
            return FormValidation.ok("Your Rundeck instance is alive, and your credentials are valid !");
        }

        public FormValidation doCheckJobIdentifier(@QueryParameter("jobIdentifier") final String jobIdentifier,
                @QueryParameter("rundeckInstance") final String rundeckInstance) {
            if (this.getRundeckInstance(rundeckInstance) == null) {
                return FormValidation.error("Rundeck global configuration is not valid !");
            }
            if (StringUtils.isBlank(jobIdentifier)) {
                return FormValidation.error("The job identifier is mandatory !");
            }
            try {
                if (jobIdentifier.contains("$")) {
                    return FormValidation.warning("Unable to substitute environment at configuration time. " +
                            "The build will fail if the job does not exist");
                }
                RundeckJob job = findJobUncached(jobIdentifier, this.getRundeckInstance(rundeckInstance));
                if (job == null) {
                    return FormValidation.error("Could not find a job with the identifier : %s", jobIdentifier);
                } else {
                    return FormValidation.ok("Your Rundeck job is : %s [%s] %s",
                                             job.getId(),
                                             job.getProject(),
                                             job.getFullName());
                }
            } catch (RundeckApiException e) {
                return FormValidation.error("Failed to get job details : %s", e.getMessage());
            } catch (IllegalArgumentException e) {
                return FormValidation.error("Failed to get job details : %s", e.getMessage());
            }
        }

        /**
         * Return a rundeck Job ID, by find a rundeck job if the identifier is a project:[group/]*name format, otherwise
         * returning the original identifier as the ID.
         * @param jobIdentifier either a Job ID, or "project:[group/]*name"
         * @param rundeckClient the client instance
         * @return a job UUID
         * @throws RundeckApiException
         * @throws IllegalArgumentException
         */
        static String findJobId(String jobIdentifier, RundeckClient rundeckClient) throws RundeckApiException,
                IllegalArgumentException {
            log.fine(format("findJobId request for jobId: %s", jobIdentifier));
            //TODO: Could be rewritten to be cache as well, if needed
            Matcher matcher = JOB_REFERENCE_PATTERN.matcher(jobIdentifier);
            if (matcher.find() && matcher.groupCount() == 3) {
                String project = matcher.group(1);
                String groupPath = matcher.group(2);
                String name = matcher.group(3);
                RundeckJob foundJob = rundeckClient.findJob(project, groupPath, name);
                return foundJob == null ? null : foundJob.getId();
            } else {
                return jobIdentifier;
            }
        }

        /**
         * Find a {@link RundeckJob} with the given identifier using internal cache if possible.
         *
         * @param jobIdentifier either a simple ID, an UUID or a reference (project:group/name)
         * @param rundeckInstanceName Rundeck instance name
         * @param rundeckInstance Rundeck client instance
         * @return the {@link RundeckJob} found, or null if not found
         * @throws RundeckApiException in case of error, or if no job with this ID
         * @throws IllegalArgumentException if the identifier is not valid
         */
        public static RundeckJob findJob(String jobIdentifier, String rundeckInstanceName, RundeckClient rundeckInstance) throws RundeckApiException, IllegalArgumentException {
            RundeckJobCache rundeckJobCache = getRundeckDescriptor().rundeckJobCache;
            return rundeckJobCache.findJobById(jobIdentifier, rundeckInstanceName, rundeckInstance);
        }

        public static RundeckJob findJobUncached(String jobIdentifier, RundeckClient rundeckInstance) throws RundeckApiException, IllegalArgumentException {
            RundeckJobCacheConfig rundeckJobCacheConfig = getRundeckDescriptor().getRundeckJobCacheConfig();
            if (rundeckJobCacheConfig.isEnabled()) {
                log.info(format("NOT CACHED findJobUncached request for jobId: %s", jobIdentifier));
            } else {
                log.fine(format("findJobUncached request for jobId: %s (cache disabled)", jobIdentifier));
            }
            Matcher matcher = JOB_REFERENCE_PATTERN.matcher(jobIdentifier);
            if (matcher.find() && matcher.groupCount() == 3) {
                String project = matcher.group(1);
                String groupPath = matcher.group(2);
                String name = matcher.group(3);
                return rundeckInstance.findJob(project, groupPath, name);
            } else {
                return rundeckInstance.getJob(jobIdentifier);
            }
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Rundeck";
        }

        public String getApiVersion(RundeckClient instance) {
            if (instance != null) {
                try {
                    Method method = instance.getClass().getDeclaredMethod("getApiVersion");
                    method.setAccessible(true);

                    return method.invoke(instance).toString();
                } catch (SecurityException | NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }

            return "";
        }

        public RundeckClient getRundeckInstance(String key) {
            return rundeckInstances.get(key);
        }

        public void addRundeckInstance(String key, RundeckClient instance) {
            Map<String, RundeckClient> instances = new LinkedHashMap<String, RundeckClient>(this.rundeckInstances);
            instances.put(key, instance);
            this.setRundeckInstances(instances);
        }

        public Map<String, RundeckClient> getRundeckInstances() {
            return rundeckInstances;
        }

        public void setRundeckInstances(Map<String, RundeckClient> instances) {
            this.rundeckInstances = instances;
        }

        public RundeckJobCacheConfig getRundeckJobCacheConfig() {
            return rundeckJobCacheConfig;
        }
    }

    /**
     * {@link BuildBadgeAction} used to display a Rundeck icon + a link to the Rundeck execution page, on the Jenkins
     * build history and build result page.
     */
    public static class RundeckExecutionBuildBadgeAction implements BuildBadgeAction {

        private final String executionUrl;

        public RundeckExecutionBuildBadgeAction(String executionUrl) {
            super();
            this.executionUrl = executionUrl;
        }

        public String getDisplayName() {
            return "Rundeck Execution Result";
        }

        public String getIconFileName() {
            return "/plugin/rundeck/images/rundeck_24x24.png";
        }

        public String getUrlName() {
            return executionUrl;
        }

    }

    /**
     *
     * @param tagsStr
     * @return
     */
    private String[] extracttags(String tagsStr, String delimiter){

        if (tagsStr == null)
            return new String[0];
        List<String> list = new ArrayList<String>(Arrays.asList(tagsStr.split(delimiter)));

        for (ListIterator<String> iterator = list.listIterator(); iterator.hasNext(); ) {
            String tag  = iterator.next();
            tag=tag.replaceAll("\\s+","").trim();
            iterator.remove();
            if (!tag.equals(""))
                iterator.add(tag);

        }

        return list.toArray(new String[list.size()]);
    }

    private static RundeckDescriptor getRundeckDescriptor() {
        return Jenkins.getInstance().getExtensionList(RundeckDescriptor.class).get(0);
    }
}
