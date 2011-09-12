package org.jenkinsci.plugins.rundeck;

import java.io.Serializable;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.rundeck.api.domain.RundeckJob;

/**
 * Identifier for a {@link RundeckJob}. Could be either a job's UUID, or a reference to a job in the format
 * "project:group/job"
 * 
 * @author Vincent Behar
 */
public class RundeckJobIdentifier implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String identifier;

    @DataBoundConstructor
    public RundeckJobIdentifier(String identifier) {
        this.identifier = StringUtils.trim(identifier);
    }

    /**
     * Check if this jobIdentifier matches (= identifies) the given job
     * 
     * @param job to test
     * @return true if it matches, false otherwise
     */
    public boolean matches(RundeckJob job) {
        if (job == null || StringUtils.isBlank(identifier)) {
            return false;
        }

        // UUID
        if (StringUtils.equalsIgnoreCase(job.getId(), identifier)) {
            return true;
        }

        // "project:group/job" reference
        String jobReference = job.getProject() + ":" + job.getFullName();
        if (StringUtils.equalsIgnoreCase(jobReference, identifier)) {
            return true;
        }

        return false;
    }

    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String toString() {
        return "RundeckJobIdentifier [identifier=" + identifier + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
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
        RundeckJobIdentifier other = (RundeckJobIdentifier) obj;
        if (identifier == null) {
            if (other.identifier != null)
                return false;
        } else if (!identifier.equals(other.identifier))
            return false;
        return true;
    }
}
