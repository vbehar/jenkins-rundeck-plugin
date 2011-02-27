package org.jenkinsci.plugins.rundeck;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;

/**
 * Represents a RunDeck instance, and allows to communicate with it in order to schedule jobs. Uses RunDeck 1.1 HTTP API
 * to schedule jobs.
 * 
 * @author Vincent Behar
 */
public class RundeckInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String url;

    private final String login;

    private final String password;

    /**
     * Build a new RunDeck instance
     * 
     * @param url of the RunDeck server (http://localhost:4440)
     * @param login to use
     * @param password associated with the login
     */
    public RundeckInstance(String url, String login, String password) {
        super();
        this.url = url;
        this.login = login;
        this.password = password;
    }

    /**
     * @return true if the configuration is valid, false otherwise
     */
    public boolean isConfigurationValid() {
        return StringUtils.isNotBlank(url) && StringUtils.isNotBlank(login) && StringUtils.isNotBlank(password);
    }

    /**
     * @return true if this RunDeck instance is alive, false otherwise
     */
    public boolean isAlive() {
        try {
            int responseCode = new HttpClient().executeMethod(new GetMethod(url));
            return (responseCode / 100 == 2) ? true : false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @return true if the credentials (login/password) are valid, false otherwise
     */
    public boolean isLoginValid() {
        try {
            doLogin(new HttpClient());
            return true;
        } catch (RundeckLoginException e) {
            return false;
        }
    }

    /**
     * Schedule a job execution, identified by the given groupPath and jobName.
     * 
     * @param groupPath of the job (eg "main-group/sub-group") - mandatory
     * @param jobName - mandatoru
     * @param options for the job, optional
     * @return the url of the RunDeck execution page
     * @throws RundeckLoginException if the login failed
     * @throws RundeckJobSchedulingException if the scheduling failed
     */
    public String scheduleJobExecution(String groupPath, String jobName, Properties options)
            throws RundeckLoginException, RundeckJobSchedulingException {
        if (StringUtils.isBlank(groupPath) || StringUtils.isBlank(jobName)) {
            throw new IllegalArgumentException("groupPath and jobName are mandatory");
        }

        HttpClient httpClient = new HttpClient();

        doLogin(httpClient);

        String scheduleUrl = url + "/scheduledExecution/runJobByName.xml";
        GetMethod method = new GetMethod(scheduleUrl);
        method.setQueryString(prepareQueryString(groupPath, jobName, options));
        try {
            httpClient.executeMethod(method);
        } catch (Exception e) {
            throw new RundeckJobSchedulingException("Failed to schedule job", e);
        }
        if (method.getStatusCode() / 100 != 2) {
            throw new RundeckJobSchedulingException("Invalid HTTP code result : " + method.getStatusCode() + " for "
                                                    + scheduleUrl);
        }

        // retrieve execution url
        InputStream response = null;
        try {
            response = method.getResponseBodyAsStream();
            return url + parseExecutionUrl(response);
        } catch (IOException e) {
            throw new RundeckJobSchedulingException("Failed to get RunDeck result", e);
        } finally {
            IOUtils.closeQuietly(response);
        }
    }

    /**
     * RunDeck current (1.1) API does not support HTTP BASIC auth, so we need to make multiple HTTP requests...
     * 
     * @param httpClient
     * @throws RundeckLoginException if the login failed
     */
    private void doLogin(HttpClient httpClient) throws RundeckLoginException {
        String location = url + "/j_security_check";
        while (true) {
            PostMethod loginMethod = new PostMethod(location);
            loginMethod.addParameter("j_username", login);
            loginMethod.addParameter("j_password", password);
            loginMethod.addParameter("action", "login");
            try {
                httpClient.executeMethod(loginMethod);
            } catch (Exception e) {
                throw new RundeckLoginException("Failed to post login form on " + location, e);
            }
            if (loginMethod.getStatusCode() / 100 == 3) {
                // Commons HTTP client refuses to handle redirects (code 3xx) for POST
                // so we have to do it manually...
                location = loginMethod.getResponseHeader("Location").getValue();
                continue;
            }
            if (loginMethod.getStatusCode() / 100 != 2) {
                // either a 4xx or 5xx, not good !
                throw new RundeckLoginException("Invalid HTTP code result : " + loginMethod.getStatusCode() + " for "
                                                + location);
            }
            try {
                String content = loginMethod.getResponseBodyAsString();
                if (StringUtils.contains(content, "j_security_check")) {
                    throw new RundeckLoginException("Login failed for user " + login);
                }
            } catch (IOException e) {
                throw new RundeckLoginException("Failed to get RunDeck result", e);
            }
            break;
        }
    }

    /**
     * prepare the {@link HttpClient}'s queryString containing the group/job and the options.
     * 
     * @param groupPath of the job (eg "main-group/sub-group")
     * @param jobName
     * @param options (may be null or empty, as it is optional)
     * @return an array of {@link NameValuePair}, won't be null or empty (at least 2 entries : group and job)
     */
    private NameValuePair[] prepareQueryString(String groupPath, String jobName, Properties options) {
        List<NameValuePair> queryString = new ArrayList<NameValuePair>();

        queryString.add(new NameValuePair("groupPath", groupPath));
        queryString.add(new NameValuePair("jobName", jobName));

        if (options != null) {
            for (Entry<Object, Object> option : options.entrySet()) {
                queryString.add(new NameValuePair("extra.command.option." + option.getKey(),
                                                  String.valueOf(option.getValue())));
            }
        }

        return queryString.toArray(new NameValuePair[queryString.size()]);
    }

    /**
     * Parse the xml response from RunDeck, and if it is successful, return the execution url.
     * 
     * @param response
     * @return the RunDeck job execution relative url
     * @throws RundeckJobSchedulingException if the response in an error
     */
    public String parseExecutionUrl(InputStream response) throws RundeckJobSchedulingException {
        SAXReader reader = new SAXReader();
        reader.setEncoding("UTF-8");
        Document document;
        try {
            document = reader.read(response);
        } catch (DocumentException e) {
            throw new RundeckJobSchedulingException("Failed to read RunDeck reponse", e);
        }
        document.setXMLEncoding("UTF-8");

        Boolean success = Boolean.valueOf(document.valueOf("result/@success"));
        if (!success) {
            throw new RundeckJobSchedulingException(document.valueOf("result/error/message"));
        }

        return document.valueOf("result/succeeded/execution[@index='0']/url");
    }

    public String getUrl() {
        return url;
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return "RundeckInstance [url=" + url + ", login=" + login + ", password=" + password + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((login == null) ? 0 : login.hashCode());
        result = prime * result + ((password == null) ? 0 : password.hashCode());
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
        RundeckInstance other = (RundeckInstance) obj;
        if (login == null) {
            if (other.login != null)
                return false;
        } else if (!login.equals(other.login))
            return false;
        if (password == null) {
            if (other.password != null)
                return false;
        } else if (!password.equals(other.password))
            return false;
        if (url == null) {
            if (other.url != null)
                return false;
        } else if (!url.equals(other.url))
            return false;
        return true;
    }

    /**
     * Exception used when the login failed on a RunDeck instance
     */
    public static class RundeckLoginException extends Exception {

        private static final long serialVersionUID = 1L;

        public RundeckLoginException(String message) {
            super(message);
        }

        public RundeckLoginException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    /**
     * Exception used when a job scheduling failed on a RunDeck instance
     */
    public static class RundeckJobSchedulingException extends Exception {

        private static final long serialVersionUID = 1L;

        public RundeckJobSchedulingException(String message) {
            super(message);
        }

        public RundeckJobSchedulingException(String message, Throwable cause) {
            super(message, cause);
        }

    }

}
