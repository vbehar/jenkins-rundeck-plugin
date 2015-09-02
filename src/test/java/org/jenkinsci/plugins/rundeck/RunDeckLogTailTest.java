package org.jenkinsci.plugins.rundeck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import mockit.Expectations;
import mockit.Mocked;
import mockit.NonStrictExpectations;

import org.junit.Before;
import org.junit.Test;
import org.rundeck.api.RundeckApiException;
import org.rundeck.api.RundeckClient;
import org.rundeck.api.domain.RundeckOutput;
import org.rundeck.api.domain.RundeckOutputEntry;

public class RunDeckLogTailTest {

    private static final long EXECUTION_ID = 1L;

    @Mocked
    private RundeckClient rundeckClient;

    @Mocked
    private RundeckOutput rundeckOutput;

    RunDeckLogTail runDeckLogTail;

    @Before
    public void before() {
        long executionId = 1L;
        int maxLines = 2;
        int maxRetries = 3;
        long sleepRetry = 100L;
        long sleepUnmodified = 100L;
        long sleepModified = 100L;
        runDeckLogTail = new RunDeckLogTail(rundeckClient, executionId, maxLines, maxRetries, sleepRetry, sleepUnmodified, sleepModified);

    }

    @Test
    public void iteratorReturnsResults() {

        new NonStrictExpectations() {
            {
                //@formatter:off
                rundeckClient.getExecutionOutputState(EXECUTION_ID, false, 0, anyLong, 2); result = rundeckOutput;
                rundeckClient.getExecutionOutputState(EXECUTION_ID, false, 50, anyLong, 2); result = rundeckOutput;
                rundeckClient.getExecutionOutputState(EXECUTION_ID, false, 100, anyLong, 2); result = rundeckOutput;
                rundeckOutput.getOffset(); returns(50, 100, 150);
                rundeckOutput.isExecCompleted(); returns (false, false, true);
                rundeckOutput.getLogEntries(); returns(createLogEntries(new String[] {"lorem", "ipsum"}), createLogEntries(new String[] {"dolar", "sit"}), createLogEntries(new String[] {"amet"}));;
                rundeckOutput.isCompleted(); returns (false, false, true);
                //@formatter:on
            }
        };

        RunDeckLogTail.RunDeckLogTailIterator iterator = runDeckLogTail.iterator();

        assertTrue(iterator.hasNext());
        assertMessagesPresentInOrder(iterator.next(), "lorem", "ipsum");

        assertTrue(iterator.hasNext());
        assertMessagesPresentInOrder(iterator.next(), "dolar", "sit");

        assertTrue(iterator.hasNext());
        assertMessagesPresentInOrder(iterator.next(), "amet");

        assertFalse(iterator.hasNext());

    }

    @Test
    public void apiExceptionWillBeCaughtThreeTimesAndThenThrown() throws InterruptedException {
        new Expectations() {
            {
                //@formatter:off
                rundeckClient.getExecutionOutputState(EXECUTION_ID, false, 0, anyLong, 2); result = new RundeckApiException("fail!"); times = 4;
                //@formatter:on
            }
        };

        RunDeckLogTail.RunDeckLogTailIterator iterator = runDeckLogTail.iterator();

        assertTrue(iterator.hasNext());
        assertMessagesPresentInOrder(iterator.next());

        assertTrue(iterator.hasNext());
        assertMessagesPresentInOrder(iterator.next());

        assertTrue(iterator.hasNext());
        assertMessagesPresentInOrder(iterator.next());

        try {
            iterator.hasNext();
            fail("Expected exception!");
        } catch (RundeckApiException e) {
            assertEquals("fail!", e.getMessage());
        }
    }

    @Test
    public void iteratorIsInterrupted() throws InterruptedException {
        new NonStrictExpectations() {

            @Mocked({ "sleep", "interrupt" })
            final Thread unused = null;
            {
                //@formatter:off
                rundeckClient.getExecutionOutputState(EXECUTION_ID, false, 0, anyLong, 2); result = rundeckOutput;
                rundeckOutput.getOffset(); returns(50);
                rundeckOutput.getLogEntries(); returns(createLogEntries(new String[] {"lorem", "ipsum"}));
                rundeckOutput.isCompleted(); returns (false);
                Thread.sleep(anyLong); result= new InterruptedException();
                onInstance(Thread.currentThread()).interrupt();
                //@formatter:on
            }
        };

        RunDeckLogTail.RunDeckLogTailIterator iterator = runDeckLogTail.iterator();

        assertTrue(iterator.hasNext());
        assertMessagesPresentInOrder(iterator.next(), "lorem", "ipsum");

        assertFalse(iterator.hasNext());
    }

    @Test
    public void autoBoxingIsHandledCorrectly() {
        new NonStrictExpectations() {
            {
                //@formatter:off
                rundeckClient.getExecutionOutputState(EXECUTION_ID, false, 0, 0L, 2); result = rundeckOutput;
                rundeckClient.getExecutionOutputState(EXECUTION_ID, false, 0, anyLong, 2); result = rundeckOutput;
                rundeckClient.getExecutionOutputState(EXECUTION_ID, false, 50, anyLong, 2); result = rundeckOutput;
                rundeckOutput.getOffset(); returns(0, 50, 100);
                rundeckOutput.getLogEntries(); returns(createLogEntries(new String[] {}), createLogEntries(new String[] {}), createLogEntries(new String[] {"dolar", "sit", "amet"}));;
                rundeckOutput.isCompleted(); returns (null, false, true);
                rundeckOutput.isExecCompleted(); returns (null, false, true);
                //@formatter:on
            }
        };

        RunDeckLogTail.RunDeckLogTailIterator iterator = runDeckLogTail.iterator();

        assertTrue(iterator.hasNext());
        assertMessagesPresentInOrder(iterator.next());

        assertTrue(iterator.hasNext());
        assertMessagesPresentInOrder(iterator.next());

        assertTrue(iterator.hasNext());
        assertMessagesPresentInOrder(iterator.next(), "dolar", "sit", "amet");

        assertFalse(iterator.hasNext());

    }

    @Test
    public void interatorUnmodifiedResultsButNotDone() {
        new NonStrictExpectations() {
            {
                //@formatter:off
                rundeckClient.getExecutionOutputState(EXECUTION_ID, false, 0, anyLong, 2); result = rundeckOutput;
                rundeckClient.getExecutionOutputState(EXECUTION_ID, false, 50, anyLong, 2); result = rundeckOutput;
                rundeckClient.getExecutionOutputState(EXECUTION_ID, false, 50, anyLong, 2); result = rundeckOutput;
                rundeckOutput.getOffset(); returns(50, 50, 150);
                rundeckOutput.getLogEntries(); returns(createLogEntries(new String[] {"lorem", "ipsum"}), createLogEntries(new String[] {}), createLogEntries(new String[] {"dolar", "sit", "amet"}));;
                rundeckOutput.isCompleted(); returns (false, false, true);
                rundeckOutput.isExecCompleted(); returns (false, false, true);
                //@formatter:on
            }
        };

        RunDeckLogTail.RunDeckLogTailIterator iterator = runDeckLogTail.iterator();

        assertTrue(iterator.hasNext());
        assertMessagesPresentInOrder(iterator.next(), "lorem", "ipsum");

        assertTrue(iterator.hasNext());
        assertMessagesPresentInOrder(iterator.next());

        assertTrue(iterator.hasNext());
        assertMessagesPresentInOrder(iterator.next(), "dolar", "sit", "amet");

        assertFalse(iterator.hasNext());

    }

    public void assertMessagesPresentInOrder(List<RundeckOutputEntry> rundeckOutputEntries, String... messages) {
        assertEquals(rundeckOutputEntries.size(), messages.length);
        int i = 0;
        for (RundeckOutputEntry rundeckOutputEntry : rundeckOutputEntries) {
            assertEquals(rundeckOutputEntry.getMessage(), messages[i]);
            i++;
        }
    }

    public List<RundeckOutputEntry> createLogEntries(String... messages) {
        List<RundeckOutputEntry> results = new ArrayList<RundeckOutputEntry>();
        for (String message : messages) {
            RundeckOutputEntry rundeckOutputEntry = new RundeckOutputEntry();
            rundeckOutputEntry.setMessage(message);
            results.add(rundeckOutputEntry);
        }
        return results;
    }

}
