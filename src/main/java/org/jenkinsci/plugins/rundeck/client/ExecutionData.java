package org.jenkinsci.plugins.rundeck.client;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.rundeck.client.api.model.DateInfo;
import org.rundeck.client.api.model.Execution;

import java.util.concurrent.TimeUnit;

public class ExecutionData extends Execution {

    Execution execution;
    public ExecutionData(Execution execution) {
        this.execution = execution;
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

}
