package org.jenkinsci.plugins.rundeck;

import hudson.util.Secret;
import org.jenkinsci.plugins.rundeck.client.RundeckClientManager;

public class RundeckInstanceBuilder {
    private String url;
    private int apiVersion=RundeckClientManager.API_VERSION;
    private String login;
    private Secret token;
    private Secret password;
    RundeckClientManager client;

    public RundeckInstanceBuilder() {
    }

    void setClient(RundeckClientManager client){
        this.client = client;
    }

    RundeckClientManager getClient(){
        return client;
    }

    RundeckInstanceBuilder client(RundeckClientManager client){
        this.url = client.getRundeckInstance().getUrl();
         if(client.getRundeckInstance().getPassword()!=null){
            this.password = client.getRundeckInstance().getPassword();
        }
        if(client.getRundeckInstance().getToken()!=null){
            this.token = client.getRundeckInstance().getToken();
        }
        this.login = client.getRundeckInstance().getLogin();

        if(client.getRundeckInstance().getApiVersion()!=null){
            this.apiVersion = client.getRundeckInstance().getApiVersion();
        }else{
            this.apiVersion = RundeckClientManager.API_VERSION;
        }

        return this;
    }

    RundeckInstanceBuilder url(String url){
        this.url = url;
        return this;
    }

    RundeckInstanceBuilder version(int apiVersion){
        this.apiVersion = apiVersion;
        return this;
    }

    RundeckInstanceBuilder login(String login, Secret password){
        this.login = login;
        this.password = password;
        return this;
    }

    RundeckInstanceBuilder token(Secret token){
        this.token = token;
        return this;
    }

    public RundeckInstance build() {
        RundeckInstance client = new RundeckInstance();
        client.setUrl(this.url);
        client.setApiVersion(this.apiVersion);
        client.setLogin(this.login);
        client.setPassword(this.password);
        client.setToken(this.token);

        return client;
    }


    static RundeckClientManager createClient(RundeckInstance instance){
        RundeckClientManager clientManager = new RundeckClientManager(instance);
        return clientManager;
    }

    /*
    public String getApiVersion(RundeckClient instance) {
        if (instance != null) {
            try {
                Method method = instance.getClass().getDeclaredMethod("getApiVersion");
                method.setAccessible(true);

                return method.invoke(instance).toString();
            } catch (SecurityException | NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
                return "";
            }
        }

        return "";
    }

     */

    @Override
    public String toString() {
        return "RundeckInstanceBuilder{" +
                "url='" + url + '\'' +
                ", apiVersion=" + apiVersion +
                ", login='" + login + '\'' +
                ", token=" + token +
                ", password=" + password +
                ", client=" + client +
                '}';
    }
}
