package org.jenkinsci.plugins.rundeck;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
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
        HttpClient httpClient = new DefaultHttpClient();
        try {
            HttpResponse response = httpClient.execute(new HttpGet(url));
            return response.getStatusLine().getStatusCode() / 100 == 2;
        } catch (IOException e) {
            return false;
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
    }

    /**
     * @return true if the credentials (login/password) are valid, false otherwise
     */
    public boolean isLoginValid() {
        HttpClient httpClient = new DefaultHttpClient();
        try {
            doLogin(httpClient);
            return true;
        } catch (RundeckLoginException e) {
            return false;
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
    }

    /**
     * Schedule a job execution, identified by the given groupPath and jobName.
     * 
     * @param groupPath of the job (eg "main-group/sub-group") - mandatory
     * @param jobName - mandatory
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

        HttpClient httpClient = new DefaultHttpClient();
        try {
            doLogin(httpClient);

            String scheduleUrl = url + "/scheduledExecution/runJobByName.xml?"
                                 + prepareQueryString(groupPath, jobName, options);
            HttpResponse response = null;
            try {
                response = httpClient.execute(new HttpGet(scheduleUrl));
            } catch (IOException e) {
                throw new RundeckJobSchedulingException("Failed to schedule job", e);
            }
            if (response.getStatusLine().getStatusCode() / 100 != 2) {
                throw new RundeckJobSchedulingException("Invalid HTTP response '" + response.getStatusLine() + "' for "
                                                        + scheduleUrl);
            }

            // retrieve execution url
            if (response.getEntity() == null) {
                throw new RundeckJobSchedulingException("Empty RunDeck response ! HTTP status line is : "
                                                        + response.getStatusLine());
            }
            try {
                String executionUrl = url + parseExecutionUrl(response.getEntity().getContent());
                EntityUtils.consume(response.getEntity());
                return executionUrl;
            } catch (IOException e) {
                throw new RundeckJobSchedulingException("Failed to read RunDeck response", e);
            }
        } finally {
            httpClient.getConnectionManager().shutdown();
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
            HttpPost postLogin = new HttpPost(location);
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("j_username", login));
            params.add(new BasicNameValuePair("j_password", password));
            params.add(new BasicNameValuePair("action", "login"));

            HttpResponse response = null;
            try {
                postLogin.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
                response = httpClient.execute(postLogin);
            } catch (IOException e) {
                throw new RundeckLoginException("Failed to post login form on " + location, e);
            }

            if (response.getStatusLine().getStatusCode() / 100 == 3) {
                // HTTP client refuses to handle redirects (code 3xx) for POST, so we have to do it manually...
                location = response.getFirstHeader("Location").getValue();
                try {
                    EntityUtils.consume(response.getEntity());
                } catch (IOException e) {
                    throw new RundeckLoginException("Failed to consume entity (release connection)", e);
                }
                continue;
            }
            if (response.getStatusLine().getStatusCode() / 100 != 2) {
                throw new RundeckLoginException("Invalid HTTP response '" + response.getStatusLine() + "' for "
                                                + location);
            }
            try {
                String content = EntityUtils.toString(response.getEntity(), HTTP.UTF_8);
                if (StringUtils.contains(content, "j_security_check")) {
                    throw new RundeckLoginException("Login failed for user " + login);
                }
                try {
                    EntityUtils.consume(response.getEntity());
                } catch (IOException e) {
                    throw new RundeckLoginException("Failed to consume entity (release connection)", e);
                }
            } catch (IOException io) {
                throw new RundeckLoginException("Failed to read RunDeck result", io);
            } catch (ParseException p) {
                throw new RundeckLoginException("Failed to parse RunDeck response", p);
            }
            break;
        }
    }

    /**
     * prepares an url-encoded HTTP queryString containing the group/job and the options.
     * 
     * @param groupPath of the job (eg "main-group/sub-group")
     * @param jobName
     * @param options (may be null or empty, as it is optional)
     * @return an url-encoded string
     */
    private String prepareQueryString(String groupPath, String jobName, Properties options) {
        List<NameValuePair> parameters = new ArrayList<NameValuePair>();

        parameters.add(new BasicNameValuePair("groupPath", groupPath));
        parameters.add(new BasicNameValuePair("jobName", jobName));

        if (options != null) {
            for (Entry<Object, Object> option : options.entrySet()) {
                parameters.add(new BasicNameValuePair("extra.command.option." + option.getKey(),
                                                      String.valueOf(option.getValue())));
            }
        }

        return URLEncodedUtils.format(parameters, HTTP.UTF_8);
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
