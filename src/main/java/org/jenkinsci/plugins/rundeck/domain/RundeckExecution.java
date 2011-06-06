package org.jenkinsci.plugins.rundeck.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * Represents a RunDeck execution, usually triggered by an API call (see {@link RundeckInstance}). An execution could be
 * a {@link RundeckJob} execution or an "ad-hoc" execution.
 * 
 * @author Vincent Behar
 */
public class RundeckExecution implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String url;

    private ExecutionStatus status;

    /** Optional - only if it is a job execution */
    private RundeckJob job;

    private String startedBy;

    private Date startedAt;

    /** only if the execution has ended */
    private Date endedAt;

    /** only if the execution was aborted */
    private String abortedBy;

    private String description;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public RundeckJob getJob() {
        return job;
    }

    public void setJob(RundeckJob job) {
        this.job = job;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public void setStartedBy(String startedBy) {
        this.startedBy = startedBy;
    }

    public Date getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Date startedAt) {
        this.startedAt = startedAt;
    }

    public Date getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Date endedAt) {
        this.endedAt = endedAt;
    }

    public String getAbortedBy() {
        return abortedBy;
    }

    public void setAbortedBy(String abortedBy) {
        this.abortedBy = abortedBy;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "RundeckExecution [abortedBy=" + abortedBy + ", description=" + description + ", endedAt=" + endedAt
               + ", id=" + id + ", job=" + job + ", startedAt=" + startedAt + ", startedBy=" + startedBy + ", status="
               + status + ", url=" + url + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((abortedBy == null) ? 0 : abortedBy.hashCode());
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((endedAt == null) ? 0 : endedAt.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((job == null) ? 0 : job.hashCode());
        result = prime * result + ((startedAt == null) ? 0 : startedAt.hashCode());
        result = prime * result + ((startedBy == null) ? 0 : startedBy.hashCode());
        result = prime * result + ((status == null) ? 0 : status.hashCode());
        result = prime * result + ((url == null) ? 0 : url.hashCode());
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
        RundeckExecution other = (RundeckExecution) obj;
        if (abortedBy == null) {
            if (other.abortedBy != null)
                return false;
        } else if (!abortedBy.equals(other.abortedBy))
            return false;
        if (description == null) {
            if (other.description != null)
                return false;
        } else if (!description.equals(other.description))
            return false;
        if (endedAt == null) {
            if (other.endedAt != null)
                return false;
        } else if (!endedAt.equals(other.endedAt))
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (job == null) {
            if (other.job != null)
                return false;
        } else if (!job.equals(other.job))
            return false;
        if (startedAt == null) {
            if (other.startedAt != null)
                return false;
        } else if (!startedAt.equals(other.startedAt))
            return false;
        if (startedBy == null) {
            if (other.startedBy != null)
                return false;
        } else if (!startedBy.equals(other.startedBy))
            return false;
        if (status == null) {
            if (other.status != null)
                return false;
        } else if (!status.equals(other.status))
            return false;
        if (url == null) {
            if (other.url != null)
                return false;
        } else if (!url.equals(other.url))
            return false;
        return true;
    }

    /**
     * The status of an execution
     */
    public static enum ExecutionStatus {
        RUNNING, SUCCEEDED, FAILED, ABORTED;
    }

}
