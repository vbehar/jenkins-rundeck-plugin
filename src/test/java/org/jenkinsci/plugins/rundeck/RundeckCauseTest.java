package org.jenkinsci.plugins.rundeck;

import hudson.EnvVars;
import org.junit.Assert;
import org.jvnet.hudson.test.HudsonTestCase;
import org.rundeck.client.api.model.DateInfo;
import org.rundeck.client.api.model.Execution;

import java.util.Date;

/**
 * Tests for {@link RundeckCause}
 *
 * @author Roshan Revankar
 */
public class RundeckCauseTest extends HudsonTestCase {

    public void testBuildEnvVarsArgstring(){
        Execution execution = new Execution();
        execution.setId("1");
        execution.setHref("http://localhost:4440/execution/follow/1");
        execution.setStatus( "success");
        execution.setDateStarted(new DateInfo());
        execution.setArgstring("-simpleOption simpleValue -optionWithOneHyphen value-value -optionWithTwoHyphens value-value-value -optionWithTrailingHyphen value-value-");

        RundeckCause.RundeckExecutionEnvironmentContributingAction rundeckExecutionEnvironmentContributingAction
                = new RundeckCause.RundeckExecutionEnvironmentContributingAction(execution);

        EnvVars envVars = new EnvVars();
        rundeckExecutionEnvironmentContributingAction.buildEnvVars(null,envVars);

        Assert.assertEquals("simpleValue", envVars.expand("$RDECK_EXEC_ARG_simpleOption"));
        Assert.assertEquals("value-value", envVars.expand("$RDECK_EXEC_ARG_optionWithOneHyphen"));
        Assert.assertEquals("value-value-value", envVars.expand("$RDECK_EXEC_ARG_optionWithTwoHyphens"));
        Assert.assertEquals("value-value-", envVars.expand("$RDECK_EXEC_ARG_optionWithTrailingHyphen"));

    }
}
