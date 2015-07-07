package org.jenkinsci.plugins.rundeck;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.ObjectUtils;
import org.rundeck.api.RundeckApiException;
import org.rundeck.api.RundeckClient;
import org.rundeck.api.RundeckClientBuilder;
import org.rundeck.api.domain.RundeckOutput;
import org.rundeck.api.domain.RundeckOutputEntry;

/**
 * This class implements logtailing for Rundeck.
 * 
 * @author ckramer
 */
public class RunDeckLogTail implements Iterable<List<RundeckOutputEntry>> {

    private static final Logger log = Logger.getLogger(RunDeckLogTail.class.getName());

    private final RundeckClient rundeckClient;
    private final Long executionId;
    private final int maxlines;
    private final int maxRetries;
    private final long sleepRetry;
    private final long sleepUnmodified;
    private final long sleepModified;

    protected List<RundeckOutputEntry> next;

    /**
     * Standard constructor that contains sensible defaults for handling the API calls correctly.
     * @param rundeckClient
     * @param executionId
     */
    public RunDeckLogTail(RundeckClient rundeckClient, Long executionId) {
        this(rundeckClient, executionId, 50, 5, 15000L, 5000L, 2000L);
    }

    /**
     * Extended constructor containing all the variables that can be set. 
     * @param rundeckClient the runDeckClient
     * @param executionId the id of the RunDeck job
     * @param maxlines the maximum number of lines to fetch on each API call
     * @param maxRetries the maximum number of retry attempts if the api call fails
     * @param sleepRetry sleep time in ms that will be triggered if a api call fails
     * @param sleepUnmodified sleep time in ms when the results are unmodified
     * @param sleepModified sleep time in ms when the results are modified.
     */
    public RunDeckLogTail(RundeckClient rundeckClient, Long executionId, int maxlines, int maxRetries, long sleepRetry, long sleepUnmodified, long sleepModified) {
        this.rundeckClient = rundeckClient;
        this.executionId = executionId;
        this.maxlines = maxlines;
        this.maxRetries = maxRetries;
        this.sleepRetry = sleepRetry;
        this.sleepUnmodified = sleepUnmodified;
        this.sleepModified = sleepModified;
        next = new ArrayList<RundeckOutputEntry>(maxlines);
    }

    public RunDeckLogTailIterator iterator() {
        return new RunDeckLogTailIterator();
    }

    protected class RunDeckLogTailIterator implements Iterator<List<RundeckOutputEntry>> {

        protected int offset;
        protected long lastmod;
        protected boolean completed;
        protected int retries;

        /**
         * This will clear and update the result set for the @link {@link #next()} call using the RunDeck Client to perform an API call, it will also update the
         * offset and last modification date for the next API call. If there are no changes since the last call, this method will sleep for 5 seconds. If there
         * are changes, it will sleep for 2 seconds so it won't overload the API. Once the API call returns with 'completed' the next call to this method will
         * return false.</br> If for some reason the sleep is interrupted, the next call to this method will return false.</br>
         */
        public boolean hasNext() {

            if (completed) {
                return false;
            }

            next.clear();
            
            try {
                try {
                    log.log(Level.FINE, "Performing API call for executionId [{0}], using offset [{1}] and lastmod [{2}], fetching a maximum of [{3}] lines.", new Object[] {
                            executionId, offset, lastmod, maxlines });
                    RundeckOutput rundeckOutput = rundeckClient.getExecutionOutput(executionId, offset, lastmod, maxlines);
                    updateIterationState(rundeckOutput);
                    addRunDeckOutputEntriesToResults(rundeckOutput);
                    if (!completed) {
                        log.log(Level.INFO, "RunDecks Execution Output is not yet completed. Initializing pause to prevent API hammering");
                        handleSleep(rundeckOutput.isUnmodified());
                    }
                } catch (RundeckApiException e) {
                    log.log(Level.WARNING, "Caught RuntimeException while handling API call for logs. Will retry for max [{0}] times or rethrow exception.", new Object[]{maxRetries, e});
                    sleepOrThrowException(e);
                }
            } catch (InterruptedException e) {
                log.warning("Caught InterruptedException, will set completed to 'true'.");
                completed = true;
            }
            return true;
        }

        private void sleepOrThrowException(RuntimeException e) throws InterruptedException {
            if (retries >= maxRetries) {
                log.log(Level.SEVERE, "Giving up after [{0}] retries...", new Object[] {maxRetries, e});
                throw e;
            }
            retries++;
            Thread.sleep(sleepRetry);
        }

        private void updateIterationState(RundeckOutput rundeckOutput) {
            offset = rundeckOutput.getOffset();
            lastmod = defaultLong(rundeckOutput.getLastModified(), 0L);
            completed = Boolean.TRUE.equals(rundeckOutput.isCompleted());
            log.log(Level.FINE, "Offset is now set to [{0}], lastmod is now set to [{1}], completed is now set to [{2}]", new Object[] { offset, lastmod,
                    completed });
        }

        private long defaultLong(Long value, long defaultLong) {
            if (value == null) {
                return defaultLong;
            }
            return value;
        }

        private void addRunDeckOutputEntriesToResults(RundeckOutput rundeckOutput) {

            List<RundeckOutputEntry> runDeckOutputEntries = rundeckOutput.getLogEntries();
            if (runDeckOutputEntries != null) {
                next.addAll(runDeckOutputEntries);
            }
        }

        private void handleSleep(boolean unmodified) throws InterruptedException {
            if (unmodified) {
                log.log(Level.FINE, "Results are unmodified, sleeping for [{0}] ms.", sleepUnmodified);
                Thread.sleep(sleepUnmodified);
            } else {
                log.log(Level.FINE, "Results are modified, sleeping for [{0}] ms.", sleepModified);
                Thread.sleep(sleepModified);
            }
        }

        /**
         * Returns the resultset which has been fetched using the hasNext method.
         */
        public List<RundeckOutputEntry> next() {
            return next;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

}
