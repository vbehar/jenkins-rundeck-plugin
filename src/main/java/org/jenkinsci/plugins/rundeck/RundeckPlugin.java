package org.jenkinsci.plugins.rundeck;

import hudson.Extension;
import hudson.Plugin;

/**
 * Entry point for the RunDeck plugin
 * 
 * @author Vincent Behar
 */
@Extension
public class RundeckPlugin extends Plugin {

    private OptionProvider optionProvider;

    @Override
    public void start() throws Exception {
        super.start();
        optionProvider = new OptionProvider();
    }

    public OptionProvider getOptions() {
        return optionProvider;
    }
}
