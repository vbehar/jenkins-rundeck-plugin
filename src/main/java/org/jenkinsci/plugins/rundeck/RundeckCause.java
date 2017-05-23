package org.jenkinsci.plugins.rundeck;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import org.apache.commons.lang.StringUtils;
import org.rundeck.api.domain.RundeckExecution;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The cause of a Rundeck initiated build (encapsulates the {@link RundeckExecution} at the origin of the Rundeck
 * notification).
 * 
 * @author Vincent Behar
 */
public class RundeckCause extends Cause {

    private static final Pattern ARG_STRING_PATTERN = Pattern.compile("(\\S+)\\s\"?(.+)");

    private final RundeckExecution execution;

    /**
     * Instantiate a new cause for the given execution
     * 
     * @param execution at the origin of the Rundeck notification
     */
    public RundeckCause(RundeckExecution execution) {
        super();
        this.execution = execution;
    }

    @Override
    public String getShortDescription() {
        StringBuilder description = new StringBuilder();
        if (execution != null) {
            description.append("Started by <a href=\"");
            description.append(execution.getUrl());
            description.append("\">Rundeck Execution #");
            description.append(execution.getId());
            description.append("</a>");
            if (execution.getJob() != null) {
                description.append(" [");
                description.append(execution.getJob().getProject());
                description.append("] ");
                description.append(execution.getJob().getFullName());
            }
        } else {
            description.append("Started by a Rundeck Notification");
        }
        return description.toString();
    }

    @Override
    public void onAddedTo(AbstractBuild build) {
        super.onAddedTo(build);
        build.addAction(new RundeckExecutionEnvironmentContributingAction(execution));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((execution == null) ? 0 : execution.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RundeckCause other = (RundeckCause) obj;
        if (execution == null) {
            if (other.execution != null)
                return false;
        } else if (!execution.equals(other.execution))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "RundeckCause [execution=" + execution + "]";
    }

    /**
     * {@link EnvironmentContributingAction} used to make information about the {@link RundeckExecution} available to
     * the build (as environment variables)
     */
    public static class RundeckExecutionEnvironmentContributingAction implements EnvironmentContributingAction {

        private final RundeckExecution execution;

        /**
         * Instantiate a new action, which will use the data from the given execution
         * 
         * @param execution at the origin of the Rundeck notification
         */
        public RundeckExecutionEnvironmentContributingAction(RundeckExecution execution) {
            super();
            this.execution = execution;
        }

        public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
            if (execution != null) {
                if (execution.getJob() != null) {
                    env.put("RDECK_JOB_ID", String.valueOf(execution.getJob().getId()));
                    env.put("RDECK_JOB_NAME", String.valueOf(execution.getJob().getName()));
                    env.put("RDECK_JOB_GROUP", String.valueOf(execution.getJob().getGroup()));
                    env.put("RDECK_JOB_DESCRIPTION", String.valueOf(execution.getJob().getDescription()));
                    env.put("RDECK_PROJECT", String.valueOf(execution.getJob().getProject()));
                }
                env.put("RDECK_EXEC_ID", String.valueOf(execution.getId()));
                env.put("RDECK_EXEC_STATUS", String.valueOf(execution.getStatus()));
                env.put("RDECK_EXEC_STARTED_BY", String.valueOf(execution.getStartedBy()));
                env.put("RDECK_EXEC_STARTED_AT", String.valueOf(execution.getStartedAt()));
                env.put("RDECK_EXEC_ENDED_AT", String.valueOf(execution.getEndedAt()));
                env.put("RDECK_EXEC_ABORTED_BY", String.valueOf(execution.getAbortedBy()));
                env.put("RDECK_EXEC_DURATION_MILLIS", String.valueOf(execution.getDurationInMillis()));
                env.put("RDECK_EXEC_DURATION_SECONDS", String.valueOf(execution.getDurationInSeconds()));
                env.put("RDECK_EXEC_DURATION", String.valueOf(execution.getDuration()));
                env.put("RDECK_EXEC_SHORT_DURATION", String.valueOf(execution.getShortDuration()));
                env.put("RDECK_EXEC_URL", String.valueOf(execution.getUrl()));
                env.put("RDECK_EXEC_DESCRIPTION", String.valueOf(execution.getDescription()));

                if ( StringUtils.isNotEmpty(execution.getArgstring())) {
                    // Split argstring around hyphens at the beginning or a combination of whitespace and hyphen
                    String[] args = execution.getArgstring().split("^-|\\s-");
                    for (int i = 1; i < args.length; i++) {
                        final Matcher matcher = ARG_STRING_PATTERN.matcher(args[i]);
                        if (matcher.matches()) {
                            final String key = matcher.group(1);
                            String value = StringUtils.trim(matcher.group(2));
                            if (value.endsWith("\"")) {
                                value = value.substring(0, value.length()-1);
                            }

                            env.put("RDECK_EXEC_ARG_" + key, value);
                        }
                    }
                }

            }
        }

        public String getDisplayName() {
            return execution != null ? "Started by Rundeck Execution #" + execution.getId() : null;
        }

        public String getIconFileName() {
            return execution != null ? "/plugin/rundeck/images/rundeck_24x24.png" : null;
        }

        public String getUrlName() {
            return execution != null ? execution.getUrl() : null;
        }
    }

}
