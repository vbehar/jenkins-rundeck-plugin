package org.jenkinsci.plugins.rundeck.domain;

/**
 * A generic exception when using the RunDeck API
 * 
 * @author Vincent Behar
 */
public class RundeckApiException extends Exception {

    private static final long serialVersionUID = 1L;

    public RundeckApiException(String message) {
        super(message);
    }

    public RundeckApiException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Specific login-related error
     */
    public static class RundeckApiLoginException extends RundeckApiException {

        private static final long serialVersionUID = 1L;

        public RundeckApiLoginException(String message) {
            super(message);
        }

        public RundeckApiLoginException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    /**
     * Specific error when running a job failed
     */
    public static class RundeckApiJobRunException extends RundeckApiException {

        private static final long serialVersionUID = 1L;

        public RundeckApiJobRunException(String message) {
            super(message);
        }

        public RundeckApiJobRunException(String message, Throwable cause) {
            super(message, cause);
        }

    }

}
