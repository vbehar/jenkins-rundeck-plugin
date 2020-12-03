package org.jenkinsci.plugins.rundeck;

import hudson.util.Secret;
import org.jenkinsci.plugins.rundeck.client.RundeckClientManager;

public class RundeckInstance {

    private String url;
    private Integer apiVersion = RundeckClientManager.API_VERSION;
    private String login;
    private Secret token;
    private Secret password;
    private boolean sslHostnameVerifyAllowAll;
    private boolean sslCertificateTrustAllowSelfSigned;
    private boolean systemProxyEnabled;
    private boolean useIntermediateStreamFile;


    public static RundeckInstanceBuilder builder() {
        return new RundeckInstanceBuilder();
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

    public void setApiVersion(int apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getLogin() {
        return login;
    }

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

    public void setToken(Secret token) {
        this.token = token;
    }

    public String getPasswordPlainText() {
        if(password != null){
            return password.getPlainText();
        }
        return null;
    }

    public void setPassword(Secret password) {
        this.password = password;
    }

    public void setApiVersion(Integer apiVersion) {
        this.apiVersion = apiVersion;
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
                "url='" + url + '\'' +
                ", apiVersion=" + apiVersion +
                ", login='" + login + '\'' +
                ", token=" + token +
                ", sslHostnameVerifyAllowAll=" + sslHostnameVerifyAllowAll +
                ", sslCertificateTrustAllowSelfSigned=" + sslCertificateTrustAllowSelfSigned +
                ", systemProxyEnabled=" + systemProxyEnabled +
                ", useIntermediateStreamFile=" + useIntermediateStreamFile +
                '}';
    }
}
