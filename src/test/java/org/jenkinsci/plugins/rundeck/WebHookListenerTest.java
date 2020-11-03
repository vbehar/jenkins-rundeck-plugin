package org.jenkinsci.plugins.rundeck;


import mockit.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;


public class WebHookListenerTest {
    @Rule public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testInvalidEntities(){

        WebHookListener listener = new WebHookListener();

        final String payload = "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE ANY[\n" +
                "<!ENTITY % remote SYSTEM \"http://127.0.0.1:8000/test.dtd\">  \n" +
                "%remote;\n" +
                "%all;\n" +
                "]>\n" +
                "{}";

        InputStream data = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
        final ServletInputStream servletInputStream=new DelegatingServletInputStream(data);

        listener.doIndex(
                new MockUp<StaplerRequest>() {
                    @Mock
                    public ServletInputStream getInputStream(){
                        return servletInputStream;
                    }
                }.getMockInstance(),
                new MockUp<StaplerResponse>() {
                    @Mock
                    public void setStatus(int num){
                        assertEquals(num, 400);
                    }

                    @Mock
                    public PrintWriter getWriter(){
                        return new PrintWriter(System.out);
                    }

                }.getMockInstance()
        );
    }

    @Test
    public void testValidData() {

        WebHookListener listener = new WebHookListener();
        final String payload = "{executionId: 123, status: 'success',  jobId: '6fa68fe1-6894-477c-a997-ba1004b4ae83', jobName: 'Demo', jobGroup: 'Test', project: 'Jenkins', permalink: 'http://rundeck/execution/123' }";

        InputStream data = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
        final ServletInputStream servletInputStream=new DelegatingServletInputStream(data);

        listener.doIndex(
                new MockUp<StaplerRequest>() {
                    @Mock
                    public ServletInputStream getInputStream(){
                        return servletInputStream;
                    }
                }.getMockInstance(),
                new MockUp<StaplerResponse>() {
                    @Mock
                    public void setStatus(int num){
                        assertEquals(num, 200);
                    }

                    @Mock
                    public PrintWriter getWriter(){
                        return new PrintWriter(System.out);
                    }

                }.getMockInstance()
        );
    }

}

class DelegatingServletInputStream extends ServletInputStream {

    private final InputStream sourceStream;


    /**
     * Create a DelegatingServletInputStream for the given source stream.
     * @param sourceStream the source stream (never <code>null</code>)
     */
    public DelegatingServletInputStream(InputStream sourceStream) {
        this.sourceStream = sourceStream;
    }

    /**
     * Return the underlying source stream (never <code>null</code>).
     */
    public final InputStream getSourceStream() {
        return this.sourceStream;
    }


    public int read() throws IOException {
        return this.sourceStream.read();
    }

    public void close() throws IOException {
        super.close();
        this.sourceStream.close();
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void setReadListener(ReadListener readListener) {

    }
}

