package org.jenkinsci.plugins.rundeck;

import java.io.InputStream;
import java.util.Properties;
import junit.framework.TestCase;
import org.jenkinsci.plugins.rundeck.RundeckInstance.RundeckJobSchedulingException;
import org.jenkinsci.plugins.rundeck.RundeckInstance.RundeckLoginException;

/**
 * Test the integration with a {@link RundeckInstance} : login, job scheduling, etc. Does nothing if the RunDeck
 * instance is not alive.
 * 
 * @author Vincent Behar
 */
public class RundeckInstanceTest extends TestCase {

    private RundeckInstance rundeck;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        rundeck = new RundeckInstance("http://localhost:4440", "user", "user");
    }

    public void testInvalidLogin() throws Exception {
        rundeck = new RundeckInstance(rundeck.getUrl(), "invalid", "invalidToo");
        if (rundeck.isAlive()) {
            try {
                rundeck.scheduleJobExecution("whateverGroup", "whateverJob", null);
                fail("login for an invalid user should have failed !");
            } catch (RundeckLoginException e) {
                assertNull("Login failure for invalid user should not have a cause ! cause : " + e.getCause(),
                           e.getCause());
            }
        } else {
            System.out.println("No live RunDeck instance at " + rundeck.getUrl() + " - doing nothing...");
        }
    }

    public void testInvalidJob() throws Exception {
        if (rundeck.isAlive()) {
            try {
                rundeck.scheduleJobExecution("whateverGroup", "whateverJob", null);
                fail("scheduling for an invalid job should have failed !");
            } catch (RundeckJobSchedulingException e) {
                assertNull("Scheduling failure for invalid job should not have a cause ! cause : " + e.getCause(),
                           e.getCause());
            }
        } else {
            System.out.println("No live RunDeck instance at " + rundeck.getUrl() + " - doing nothing...");
        }
    }

    public void testValidJob() throws Exception {
        if (rundeck.isAlive()) {
            Properties options = new Properties();
            options.setProperty("version", "LATEST");
            String url = rundeck.scheduleJobExecution("main-group/sub-group", "job-name", options);
            assertTrue(url.startsWith(rundeck.getUrl() + "/execution/follow/"));
        } else {
            System.out.println("No live RunDeck instance at " + rundeck.getUrl() + " - doing nothing...");
        }
    }

    public void testRundeckXmlResponseSuccess() throws Exception {
        InputStream input = getClass().getResourceAsStream("success.xml");
        assertEquals("/execution/follow/1", rundeck.parseExecutionUrl(input));
    }

    public void testRundeckXmlResponseError() throws Exception {
        InputStream input = getClass().getResourceAsStream("error.xml");
        try {
            rundeck.parseExecutionUrl(input);
            fail("error response should have failed !");
        } catch (RundeckJobSchedulingException e) {
            assertNotNull("Error response should have a message !", e.getMessage());
        }
    }
}
