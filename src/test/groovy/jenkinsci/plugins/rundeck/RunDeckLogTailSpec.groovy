package jenkinsci.plugins.rundeck

import org.jenkinsci.plugins.rundeck.RunDeckLogTail
import org.jenkinsci.plugins.rundeck.client.RundeckManager
import org.rundeck.client.api.model.ExecLog
import org.rundeck.client.api.model.ExecOutput
import spock.lang.Specification
import spock.lang.Unroll


class RunDeckLogTailSpec extends Specification {

    @Unroll
    def "iterator Returns Results test"(){
        given:
        Long executionId = 1L;
        int maxLines = 2;
        int maxRetries = 3;
        long sleepRetry = 100L;
        long sleepUnmodified = 100L;
        long sleepModified = 100L;

        def logs =  [new ExecLog("lorem"), new ExecLog("sum")]

        ExecOutput output = new ExecOutput()
        output.execCompleted = true
        output.entries =logs

        RundeckManager rundeckClient = Mock(RundeckManager){
            getOutput(executionId, 0L, 0, maxLines ) >> output
        }

        when:
        def runDeckLogTail = new RunDeckLogTail(rundeckClient, executionId, maxLines, maxRetries, sleepRetry, sleepUnmodified, sleepModified);

        RunDeckLogTail.RunDeckLogTailIterator iterator = runDeckLogTail.iterator();
        then:
        iterator.hasNext()
        iterator.next().size()==2
        iterator.next() == logs
    }
}
