package org.jenkinsci.plugins.rundeck.client;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.rundeck.client.api.model.Execution;
import org.rundeck.client.api.model.JobItem;

import java.net.URL;
import java.util.concurrent.TimeUnit;

public class ExecutionData extends Execution {

    Execution execution;
    public ExecutionData(Execution execution) {
        this.execution = execution;
        this.cloneExecution();
    }

    public Long getDurationInMillis() {
        if (execution.getDateStarted() == null || execution.getDateEnded() == null) {
            return null;
        }
        return execution.getDateEnded().unixtime - execution.getDateStarted().unixtime;
    }

    public Long getDurationInSeconds() {
        Long durationInMillis = getDurationInMillis();
        return durationInMillis != null ? TimeUnit.MILLISECONDS.toSeconds(durationInMillis) : null;
    }

    public String getDuration() {
        Long durationInMillis = getDurationInMillis();
        return durationInMillis != null ? DurationFormatUtils.formatDurationWords(durationInMillis, true, true) : null;
    }

    public String getShortDuration() {
        Long durationInMillis = getDurationInMillis();
        return durationInMillis != null ? DurationFormatUtils.formatDurationHMS(durationInMillis) : null;
    }

    private void cloneExecution(){
        this.setStatus(execution.getStatus());
        this.setDateStarted(execution.getDateStarted());
        this.setDateEnded(execution.getDateEnded());
        this.setDescription(execution.getDescription());
        this.setFailedNodes(execution.getFailedNodes());
        this.setProject(execution.getProject());
        this.setSuccessfulNodes(execution.getSuccessfulNodes());
        this.setId(execution.getId());
        this.setServerUUID(execution.getServerUUID());
        this.setUser(execution.getUser());
        this.setArgstring(execution.getArgstring());

        //clean URLS
        if(this.isValidURL(execution.getHref())){
            this.setHref(execution.getHref());
        }

        if(this.isValidURL(execution.getPermalink())){
            this.setPermalink(execution.getPermalink());
        }

        JobItem job = execution.getJob();
        if(job != null){
            if(!this.isValidURL(job.getPermalink())){
                job.setPermalink(null);
            }

            if(!this.isValidURL(job.getHref())){
                job.setHref(null);
            }
            this.setJob(job);
        }
    }

    private boolean isValidURL(String urlString) {
        try {
            URL url = new URL(urlString);
            url.toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
