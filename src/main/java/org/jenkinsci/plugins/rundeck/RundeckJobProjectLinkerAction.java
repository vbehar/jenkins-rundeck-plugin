package org.jenkinsci.plugins.rundeck;

import hudson.model.Action;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.export.Exported;
import org.rundeck.api.RundeckApiException;
import org.rundeck.api.RundeckClient;
import org.rundeck.api.domain.RundeckJob;

/**
 * {@link Action} used to display a RunDeck icon with a link to the RunDeck job page, and some information about the
 * RunDeck job, on the Jenkins job page.
 * 
 * @author Vincent Behar
 */
public class RundeckJobProjectLinkerAction implements Action {

    private final RundeckClient rundeck;

    private final RundeckJob rundeckJob;

    private final String rundeckJobUrl;

    /**
     * Load the RunDeck job details (name, description, and so on) using the RunDeck API.
     * 
     * @param rundeck client used for talking to the RunDeck API
     * @param rundeckJobId ID of the RunDeck job
     * @throws RundeckApiException in case of error while loading the job details from RunDeck API
     * @throws IllegalArgumentException if rundeck or rundeckJobId is null
     */
    public RundeckJobProjectLinkerAction(RundeckClient rundeck, String rundeckJobId) throws RundeckApiException,
            IllegalArgumentException {
        if (rundeck == null) {
            throw new IllegalArgumentException("rundeckClient should not be null !");
        }
        this.rundeck = rundeck;
        this.rundeckJob = RundeckNotifier.RundeckDescriptor.findJob(rundeckJobId, rundeck);
        this.rundeckJobUrl = buildRundeckJobUrl();
    }

    /**
     * Build the absolute url to the RunDeck job page.
     * 
     * @return the absolute url to the RunDeck job page, or null if unable to build it
     */
    private String buildRundeckJobUrl() {
        StringBuilder url = new StringBuilder();
        url.append(rundeck.getUrl());
        if (!StringUtils.endsWith(rundeck.getUrl(), "/")) {
            url.append("/");
        }
        url.append("job/show/");
        url.append(rundeckJob.getId());
        return url.toString();
    }

    @Exported
    public RundeckJob getRundeckJob() {
        return rundeckJob;
    }

    public String getIconFileName() {
        return "/plugin/rundeck/images/rundeck_24x24.png";
    }

    public String getDisplayName() {
        return "Job: [" + rundeckJob.getProject() + "] " + rundeckJob.getName();
    }

    public String getUrlName() {
        return rundeckJobUrl;
    }

}
