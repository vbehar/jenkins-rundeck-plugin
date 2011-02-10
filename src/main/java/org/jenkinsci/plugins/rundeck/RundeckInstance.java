package org.jenkinsci.plugins.rundeck;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
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
 * Represents a rundeck instance, and allows to communicate with it in order to schedule jobs. Uses rundeck 1.1 HTTP API
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
     * Build a new rundeck instance
     * 
     * @param url of the rundeck server (http://localhost:4440)
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
     * @return true if this rundeck instance is alive, false otherwise
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
     * @param groupPath of the job (eg "main-group/sub-group")
     * @param jobName
     * @param options varargs of options for the execution, in the form "key=value" (optional)
     * @throws RundeckLoginException if the login failed
     * @throws RundeckJobSchedulingException if the scheduling failed
     */
    public void scheduleJobExecution(String groupPath, String jobName, String... options) throws RundeckLoginException,
            RundeckJobSchedulingException {
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

        // check response
        InputStream response = null;
        try {
            response = method.getResponseBodyAsStream();
            checkXmlResponse(response);
        } catch (IOException e) {
            throw new RundeckJobSchedulingException("Failed to get rundeck result", e);
        } finally {
            IOUtils.closeQuietly(response);
        }
    }

    /**
     * Rundeck current (1.1) API does not support HTTP BASIC auth, so we need to make multiple HTTP requests...
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
                throw new RundeckLoginException("Failed to get rundeck result", e);
            }
            break;
        }
    }

    /**
     * prepare the {@link HttpClient}'s queryString containing the group/job and the options.
     * 
     * @param groupPath of the job (eg "main-group/sub-group")
     * @param jobName
     * @param options varargs of options for the execution, in the form "key=value" (optional)
     * @return an array of {@link NameValuePair}, won't be null or empty (at least 2 entries : group and job)
     */
    private NameValuePair[] prepareQueryString(String groupPath, String jobName, String... options) {
        List<NameValuePair> queryString = new ArrayList<NameValuePair>();

        queryString.add(new NameValuePair("groupPath", groupPath));
        queryString.add(new NameValuePair("jobName", jobName));

        if (options != null) {
            for (String option : options) {
                if (StringUtils.isNotBlank(option)) {
                    String[] keyAndValue = StringUtils.split(option, "=", 2);
                    if (keyAndValue != null && keyAndValue.length == 2) {
                        queryString.add(new NameValuePair("extra.command.option." + keyAndValue[0], keyAndValue[1]));
                    }
                }
            }
        }

        return queryString.toArray(new NameValuePair[queryString.size()]);
    }

    /**
     * Check that the xml response from rundeck is successful
     * 
     * @param response
     * @throws RundeckJobSchedulingException if the response in an error
     */
    public void checkXmlResponse(InputStream response) throws RundeckJobSchedulingException {
        SAXReader reader = new SAXReader();
        reader.setEncoding("UTF-8");
        Document document;
        try {
            document = reader.read(response);
        } catch (DocumentException e) {
            throw new RundeckJobSchedulingException("Failed to read rundeck reponse", e);
        }
        document.setXMLEncoding("UTF-8");

        Boolean success = Boolean.valueOf(document.valueOf("result/@success"));
        if (!success) {
            throw new RundeckJobSchedulingException(document.valueOf("result/error/message"));
        }
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
     * Exception used when the login failed on a rundeck instance
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
     * Exception used when a job scheduling failed on a rundeck instance
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
