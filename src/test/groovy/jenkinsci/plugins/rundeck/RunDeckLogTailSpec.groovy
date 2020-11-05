package jenkinsci.plugins.rundeck

import org.jenkinsci.plugins.rundeck.RunDeckLogTail
import org.jenkinsci.plugins.rundeck.client.RundeckManager
import org.rundeck.client.api.model.ExecLog
import org.rundeck.client.api.model.ExecOutput
import spock.lang.Specification
import spock.lang.Unroll


class RunDeckLogTailSpec extends Specification {

    ExecOutput createOutput(List<String> logs, boolean completed, boolean execCompleted, long offset){
        List<ExecLog> entries = []
        logs.forEach{log->
            entries.add(new ExecLog(log))
        }

        ExecOutput output = new ExecOutput()
        output.execCompleted = execCompleted
        output.completed = completed
        output.offset = offset
        output.entries = entries

        return output
    }

    List<ExecLog> createLog(int size){
        List<String> log = []
        def logList = ["sum", "lorem", "ipsum", "dolar", "sit", "amet"]
        size.times {
            Collections.shuffle logList
            log << logList.first()
        }

        return log
    }



    @Unroll
    def "iterator Returns Results test"(){
        given:
        Long executionId = 1L;
        int maxLines = 2;
        int maxRetries = 3;
        long sleepRetry = 100L;
        long sleepUnmodified = 100L;
        long sleepModified = 100L;

        ExecOutput output = createOutput(["lorem", "ipsum"], false, false, 0)
        ExecOutput output1 = createOutput(["dolar", "sit"], false, false, 50)
        ExecOutput output2 = createOutput(["amet"], true, true, 100)

        RundeckManager rundeckClient = Mock(RundeckManager){
            getOutput(executionId, _, _, _ ) >> output >> output1 >> output2
        }

        when:
        def runDeckLogTail = new RunDeckLogTail(rundeckClient, executionId, maxLines, maxRetries, sleepRetry, sleepUnmodified, sleepModified);

        RunDeckLogTail.RunDeckLogTailIterator iterator = runDeckLogTail.iterator();

        then:
        iterator.hasNext()
        iterator.next().size()==2
        iterator.hasNext()
        iterator.next().size()==2
        iterator.hasNext()
        iterator.next().size()==1
    }

    @Unroll
    def "api Exception Will Be Caught Three Times And ThenThrown"(){
        given:
        Long executionId = 1L;
        int maxLines = 2;
        int maxRetries = 3;
        long sleepRetry = 100L;
        long sleepUnmodified = 100L;
        long sleepModified = 100L;

        RundeckManager rundeckClient = Mock(RundeckManager){
            getOutput(executionId, 0L, 0, maxLines ) >> {throw new RuntimeException ("Sth wrong")}
        }

        when:
        def runDeckLogTail = new RunDeckLogTail(rundeckClient, executionId, maxLines, maxRetries, sleepRetry, sleepUnmodified, sleepModified);
        RunDeckLogTail.RunDeckLogTailIterator iterator = runDeckLogTail.iterator()
        iterator.hasNext()
        iterator.hasNext()
        iterator.hasNext()
        iterator.hasNext()

        then:
        RuntimeException e = thrown()
        e.message == "Sth wrong"

    }


    @Unroll
    def "auto Boxing Is Handled Correctly"(){

        given:
        Long executionId = 1L;
        int maxLines = 2;
        int maxRetries = 3;
        long sleepRetry = 100L;
        long sleepUnmodified = 100L;
        long sleepModified = 100L;

        ExecOutput output = createOutput( [], false, false, 0)
        ExecOutput output1 = createOutput( [], false, false, 50)
        ExecOutput output2 = createOutput(["lorem", "sum"], true, true, 100)

        RundeckManager rundeckClient = Mock(RundeckManager){
            getOutput(executionId, _, _, maxLines ) >> output >> output1 >> output2
        }

        when:
        def runDeckLogTail = new RunDeckLogTail(rundeckClient, executionId, maxLines, maxRetries, sleepRetry, sleepUnmodified, sleepModified);
        RunDeckLogTail.RunDeckLogTailIterator iterator = runDeckLogTail.iterator()

        then:
        iterator.hasNext()
        iterator.next().size()==0
        iterator.hasNext()
        iterator.next().size()==0
        iterator.hasNext()
        iterator.next().size()==2

    }


    @Unroll
    def "interator Unmodified Results But Not Done"(){

        given:
        Long executionId = 1L;
        int maxLines = 2;
        int maxRetries = 3;
        long sleepRetry = 100L;
        long sleepUnmodified = 100L;
        long sleepModified = 100L;

        ExecOutput output = createOutput( ["lorem", "ipsum"], false, false, 50)
        ExecOutput output1 = createOutput( [], false, false, 50)
        ExecOutput output2 = createOutput(["dolar", "sit", "amet"], true, true, 150)

        RundeckManager rundeckClient = Mock(RundeckManager){
            getOutput(executionId, _, _, _ ) >> output >> output1 >> output2
        }

        when:
        def runDeckLogTail = new RunDeckLogTail(rundeckClient, executionId, maxLines, maxRetries, sleepRetry, sleepUnmodified, sleepModified);
        RunDeckLogTail.RunDeckLogTailIterator iterator = runDeckLogTail.iterator()

        then:
        iterator.hasNext()
        iterator.next().size()==2
        iterator.hasNext()
        iterator.next().size()==0
        iterator.hasNext()
        iterator.next().size()==3

    }

    @Unroll
    def "verbose Logging Output"(){

        given:
        Long executionId = 1L;
        int maxLines = 2;
        int maxRetries = 3;
        long sleepRetry = 100L;
        long sleepUnmodified = 100L;
        long sleepModified = 100L;

        List<String> log10 = createLog(10)
        List<String> log20 = createLog(20)
        List<String> log30 = createLog(30)
        List<String> log50 = createLog(40)
        List<String> log150 = createLog(150)
        List<String> log350 = createLog(350)
        List<String> log500 = createLog(500)

        ExecOutput outputEmpty = createOutput( [], false, false, 0)
        ExecOutput output10 = createOutput( log10, false, false, 50)
        ExecOutput output20 = createOutput( log20, false, false, 50)
        ExecOutput output30 = createOutput( log30, false, false, 50)
        ExecOutput output50 = createOutput( log50, false, false, 50)
        ExecOutput output150 = createOutput( log150, false, false, 50)
        ExecOutput output350 = createOutput( log350, false, false, 50)
        ExecOutput output500 = createOutput( log500, false, false, 50)
        ExecOutput outputEmptyEnd = createOutput( [], true, true, 0)

        RundeckManager rundeckClient = Mock(RundeckManager){
            getOutput(executionId, _, _, _ ) >> outputEmpty >> outputEmpty >> output10 >> output20 >> output30 >>
                    output50 >> output150 >> output350 >> output500 >> output350 >> output150 >>
                    output10 >> outputEmpty >> outputEmptyEnd
        }

        when:
        def runDeckLogTail = new RunDeckLogTail(rundeckClient, executionId, maxLines, maxRetries, sleepRetry, sleepUnmodified, sleepModified);
        RunDeckLogTail.RunDeckLogTailIterator iterator = runDeckLogTail.iterator()

        then:
        iterator.hasNext()
        iterator.next().size()==0
        iterator.hasNext()
        iterator.next().size()==0
        iterator.hasNext()
        iterator.next().size()==log10.size()
        iterator.hasNext()
        iterator.next().size()==log20.size()
        iterator.hasNext()
        iterator.next().size()==log30.size()
        iterator.hasNext()
        iterator.next().size()==log50.size()
        iterator.hasNext()
        iterator.next().size()==log150.size()
        iterator.hasNext()
        iterator.next().size()==log350.size()
        iterator.hasNext()
        iterator.next().size()==log500.size()
        iterator.hasNext()
        iterator.next().size()==log350.size()
        iterator.hasNext()
        iterator.next().size()==log150.size()
        iterator.hasNext()
        iterator.next().size()==log10.size()
        iterator.hasNext()
        iterator.next().size()==0
        iterator.hasNext()
        iterator.next().size()==0
        !iterator.hasNext()
    }
}
