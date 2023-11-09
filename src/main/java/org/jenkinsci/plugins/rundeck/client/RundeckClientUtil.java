package org.jenkinsci.plugins.rundeck.client;

import java.util.Properties;
import java.util.StringJoiner;

public class RundeckClientUtil {

    static String parseNodeFilters(Properties properties){
        StringJoiner filter = new StringJoiner(" ");
        for (final String name: properties.stringPropertyNames()){
            filter.add(name + ":" + properties.getProperty(name));
        }

        return filter.toString();

    }
}
