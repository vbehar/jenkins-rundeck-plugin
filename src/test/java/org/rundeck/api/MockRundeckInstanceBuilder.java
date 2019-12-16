package org.rundeck.api;

import org.jenkinsci.plugins.rundeck.RundeckInstanceBuilder;

import java.io.Serializable;

public class MockRundeckInstanceBuilder extends RundeckInstanceBuilder {

    private MockRundeckClient client;

    public MockRundeckClient getClient() {
        return client;
    }

    public void setClient(MockRundeckClient client) {
        this.client = client;
    }
}
