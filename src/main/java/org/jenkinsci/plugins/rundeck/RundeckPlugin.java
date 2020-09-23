package org.jenkinsci.plugins.rundeck;

import hudson.Extension;
import hudson.Plugin;

/**
 * Entry point for the Rundeck plugin
 *
 * @author Vincent Behar
 */
@Extension
public class  RundeckPlugin extends Plugin {

    private OptionProvider optionProvider;

    private WebHookListener webHookListener;

    @Override
    public void start() throws Exception {
        super.start();
        optionProvider = new OptionProvider();
        webHookListener = new WebHookListener();
    }

    public OptionProvider getOptions() {
        return optionProvider;
    }

    public WebHookListener getWebhook() {
        return webHookListener;
    }
}
