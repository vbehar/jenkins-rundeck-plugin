package org.jenkinsci.plugins.rundeck;

import hudson.util.Secret;
import hudson.Util;
import hudson.Extension;
import jenkins.model.Jenkins;
import hudson.model.Descriptor;
import hudson.model.AbstractDescribableImpl;
import hudson.util.FormValidation;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.jenkinsci.plugins.rundeck.client.RundeckClientManager;

import org.rundeck.client.api.LoginFailed;

import java.io.IOException;

public class RundeckInstance extends AbstractDescribableImpl<RundeckInstance>{

    private String name;
    private String url;
    private Integer apiVersion = RundeckClientManager.API_VERSION;
    private String login;
    private Secret token;
    private Secret password;
    private boolean sslHostnameVerifyAllowAll;
    private boolean sslCertificateTrustAllowSelfSigned;
    private boolean systemProxyEnabled;
    private boolean useIntermediateStreamFile;

    @DataBoundConstructor
    public RundeckInstance(final String name, final String url) {
        this.name = name;
        this.url = url;
    }

    public RundeckInstance() {
        
    }

    public static RundeckInstanceBuilder builder() {
        return new RundeckInstanceBuilder();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getApiVersion() {
        return apiVersion;
    }

    @DataBoundSetter
    public void setApiVersion(int apiVersion) {
        if(apiVersion > 0)
            this.apiVersion = apiVersion; 
    }

    public String getLogin() {
        return login;
    }

    @DataBoundSetter
    public void setLogin(String login) {
        this.login = login;
    }

    public Secret getToken() {
        return token;
    }

    public Secret getPassword() {
        return password;
    }

    public String getTokenPlainText() {
        if(token != null){
            return token.getPlainText();
        }
        return null;
    }

    @DataBoundSetter
    public void setToken(Secret token) {
        this.token = token;
    }

    public String getPasswordPlainText() {
        if(password != null){
            return password.getPlainText();
        }
        return null;
    }

    @DataBoundSetter
    public void setPassword(Secret password) {
        this.password = password;
    }

    public boolean isSslHostnameVerifyAllowAll() {
        return sslHostnameVerifyAllowAll;
    }

    public void setSslHostnameVerifyAllowAll(boolean sslHostnameVerifyAllowAll) {
        this.sslHostnameVerifyAllowAll = sslHostnameVerifyAllowAll;
    }

    public boolean isSslCertificateTrustAllowSelfSigned() {
        return sslCertificateTrustAllowSelfSigned;
    }

    public void setSslCertificateTrustAllowSelfSigned(boolean sslCertificateTrustAllowSelfSigned) {
        this.sslCertificateTrustAllowSelfSigned = sslCertificateTrustAllowSelfSigned;
    }

    public boolean isSystemProxyEnabled() {
        return systemProxyEnabled;
    }

    public void setSystemProxyEnabled(boolean systemProxyEnabled) {
        this.systemProxyEnabled = systemProxyEnabled;
    }

    public boolean isUseIntermediateStreamFile() {
        return useIntermediateStreamFile;
    }

    public void setUseIntermediateStreamFile(boolean useIntermediateStreamFile) {
        this.useIntermediateStreamFile = useIntermediateStreamFile;
    }

    @Override
    public String toString() {
        return "RundeckInstance{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", apiVersion=" + apiVersion +
                ", login='" + login + '\'' +
                ", token=" + token +
                ", sslHostnameVerifyAllowAll=" + sslHostnameVerifyAllowAll +
                ", sslCertificateTrustAllowSelfSigned=" + sslCertificateTrustAllowSelfSigned +
                ", systemProxyEnabled=" + systemProxyEnabled +
                ", useIntermediateStreamFile=" + useIntermediateStreamFile +
                '}';
    }
    @Extension
    public static class DescriptorImpl extends Descriptor<RundeckInstance> {
        public String getDisplayName() { return ""; }

        @SuppressWarnings("unused")
        @RequirePOST
        public FormValidation doTestConnection(@QueryParameter("url") final String url,
                                               @QueryParameter("login") final String login,
                                               @QueryParameter("password") final Secret password,
                                               @QueryParameter("token") final Secret token,
                                               @QueryParameter(value = "apiVersion", fixEmpty = true) final Integer apiVersion) {


            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

            RundeckInstanceBuilder builder = new RundeckInstanceBuilder().url(url);

            if (null != apiVersion && apiVersion > 0) {
                builder.version(apiVersion);
            } else {
                builder.version(RundeckClientManager.API_VERSION);
            }
            try {
                if (!token.getPlainText().isEmpty()) {
                    builder.token(token);
                } else {
                    builder.login(login, password);
                }
            } catch (IllegalArgumentException e) {
                return FormValidation.error("Rundeck configuration is not valid ! %s", e.getMessage());
            }
            RundeckInstance instance = builder.build();
            RundeckClientManager rundeck = RundeckInstanceBuilder.createClient(instance);
            try {
                rundeck.ping();
            }catch (LoginFailed e) {
                return FormValidation.error("Error: %s", e.getMessage());
            }catch (Exception e) {
                return FormValidation.error("We couldn't find a live Rundeck instance at %s error: %s", rundeck.getRundeckInstance().getUrl(), e.getMessage());
            }
            try {
                rundeck.testAuth();
            } catch (Exception e) {
                return FormValidation.error("Error: " + e.getMessage() + " authenticating Rundeck !",  rundeck.getRundeckInstance().getUrl());
            }
            return FormValidation.ok("Your Rundeck instance is alive, and your credentials are valid !");
        }
    }
}
