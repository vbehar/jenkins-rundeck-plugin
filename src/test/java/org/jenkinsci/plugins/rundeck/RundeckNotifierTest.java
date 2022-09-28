package org.jenkinsci.plugins.rundeck;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.FilePath;
import hudson.model.*;
import hudson.model.Cause.UpstreamCause;
import hudson.scm.CredentialsSVNAuthenticationProviderImpl;
import hudson.scm.SVNAuthenticationManager;
import hudson.scm.SubversionSCM;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Properties;

import hudson.util.Secret;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.rundeck.RundeckNotifier.RundeckExecutionBuildBadgeAction;
import org.jenkinsci.plugins.rundeck.client.RundeckManager;
import org.junit.Assert;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.MockBuilder;
import org.rundeck.api.*;
import org.rundeck.client.api.model.Execution;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import retrofit2.Response;

/**
 * Test the {@link RundeckNotifier}
 *
 * @author Vincent Behar
 */
public class RundeckNotifierTest extends HudsonTestCase {


    public void testCommitWithoutTag() throws Exception {

        RundeckManager client = new MockRundeckClientManager();
        RundeckInstanceBuilder instanceBuilder = new RundeckInstanceBuilder();
        instanceBuilder = instanceBuilder.name("Default");
        instanceBuilder.setClient(client);

        RundeckNotifier notifier = new RundeckNotifier("Default", "1", createOptions(), null, "", false, false, null, null, null);
        notifier.getDescriptor().setRundeckBuilder(instanceBuilder);
        notifier.getDescriptor().addRundeckInstance(instanceBuilder.build());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying Rundeck..."));
        assertTrue(s.contains("Notification succeeded !"));

        addScmCommit(build.getWorkspace(), "commit message");

        // second build
        build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying Rundeck..."));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testStandardCommitWithTag() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("Default", "1", null, null, "#deploy, #redeploy", false, false, null, null, null);
        RundeckInstance instance = new RundeckInstance();
        instance.setName("Default");
        notifier.getDescriptor().addRundeckInstance(instance);

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying Rundeck"));

        addScmCommit(build.getWorkspace(), "commit message");

        // second build
        build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying Rundeck"));
    }

    public void testDeployCommitWithTagWontBreakTheBuild() throws Exception {

        RundeckManager client = new MockRundeckClientManager();
        RundeckInstanceBuilder instanceBuilder = new RundeckInstanceBuilder();
        instanceBuilder.setClient(client);
        instanceBuilder.name("Default");

        RundeckNotifier notifier = new RundeckNotifier("Default", "1", null, null, "#deploy", false, false, null, null, null);
        notifier.getDescriptor().setRundeckBuilder(instanceBuilder);
        notifier.getDescriptor().addRundeckInstance(instanceBuilder.build());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying Rundeck"));

        addScmCommit(build.getWorkspace(), "commit message - #deploy");

        // second build
        build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying Rundeck..."));
        assertTrue(s.contains("#deploy"));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testDeployCommitWithTagWillBreakTheBuild() throws Exception {


        RundeckManager client = new MockRundeckClientManager() {
            private static final long serialVersionUID = 1L;

            @Override
            public Execution runExecution(String jobId, Properties options, Properties nodeFilters) throws IOException {
                throw new IOException("Fake error for testing");
            }

        };

        RundeckInstanceBuilder instanceBuilder = new RundeckInstanceBuilder();
        instanceBuilder.setClient(client);
        instanceBuilder.name("Default");

        RundeckNotifier notifier = new RundeckNotifier("Default", "1", null, null, "#deploy", false, true, null, null, null);

        notifier.getDescriptor().setRundeckBuilder(instanceBuilder);
        notifier.getDescriptor().addRundeckInstance(RundeckInstance.builder().name("Default").client(client).build());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        FreeStyleBuild checkBuild = project.scheduleBuild2(0).get();
        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(checkBuild);
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying Rundeck"));

        addScmCommit(build.getWorkspace(), "commit message - #deploy");

        // second build
        build = assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying Rundeck..."));
        assertTrue(s.contains("#deploy"));
        assertTrue(s.contains("Error while talking to Rundeck's API"));
        assertTrue(s.contains("Fake error for testing"));
    }

    public void testExpandEnvVarsInOptions() throws Exception {

        RundeckManager client = new MockRundeckClientManager() {
            private static final long serialVersionUID = 1L;

            @Override
            public Execution runExecution(String jobId, Properties options, Properties nodeFilters) throws IOException {
                Assert.assertEquals(4, options.size());
                Assert.assertEquals("value 1", options.getProperty("option1"));
                Assert.assertEquals("1", options.getProperty("buildNumber"));
                Assert.assertEquals("my project name", options.getProperty("jobName"));
                return super.runExecution(jobId, options, nodeFilters);

            }

        };

        RundeckInstanceBuilder instanceBuilder = new RundeckInstanceBuilder();
        instanceBuilder.setClient(client);
        instanceBuilder.name("Default");


        RundeckNotifier notifier = new RundeckNotifier("Default", "1", createOptions(), null, null, false, true, null, null, null);

        notifier.getDescriptor().setRundeckBuilder(instanceBuilder);
        notifier.getDescriptor().addRundeckInstance(RundeckInstance.builder().name("Default").client(client).build());

        FreeStyleProject project = createFreeStyleProject("my project name");
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying Rundeck..."));
        assertTrue(s.contains("Notification succeeded !"));
    }
    public void testMultivalueOptions() throws Exception {

        RundeckManager client = new MockRundeckClientManager() {
            private static final long serialVersionUID = 1L;

            @Override
            public Execution runExecution(String jobId, Properties options, Properties nodeFilters) throws IOException {
                Assert.assertEquals(2, options.size());
                Assert.assertEquals("value 1", options.getProperty("option1"));
                Assert.assertEquals("nodename1,nodename2", options.getProperty("nodes"));
                return super.runExecution(jobId, options, nodeFilters);

            }

        };

        RundeckInstanceBuilder instanceBuilder = new RundeckInstanceBuilder();
        instanceBuilder.setClient(client);
        instanceBuilder.name("Default");

        String optionString = "option1=value 1\n" +
                "nodes=nodename1,nodename2";
        RundeckNotifier notifier = new RundeckNotifier("Default", "1", optionString, null, null, false, true, null, null, null);
        notifier.getDescriptor().setRundeckBuilder(instanceBuilder);
        notifier.getDescriptor().addRundeckInstance(RundeckInstance.builder().name("Default").client(client).build());

        FreeStyleProject project = createFreeStyleProject("my project name");
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying Rundeck..."));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testUpstreamBuildWithTag() throws Exception {

        RundeckManager client = new MockRundeckClientManager();
        RundeckInstanceBuilder instanceBuilder = new RundeckInstanceBuilder();
        instanceBuilder.setClient(client);
        instanceBuilder.name("Default");

        RundeckNotifier notifier = new RundeckNotifier("Default", "1", null, null, "#deploy", false, false, null, null, null);
        notifier.getDescriptor().setRundeckBuilder(instanceBuilder);
        notifier.getDescriptor().addRundeckInstance(instanceBuilder.build());

        FreeStyleProject upstream = createFreeStyleProject("upstream");
        upstream.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        upstream.setScm(createScm());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild upstreamBuild = assertBuildStatusSuccess(upstream.scheduleBuild2(0).get());
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0,
                new UpstreamCause((Run<?, ?>) upstreamBuild))
                .get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying Rundeck"));

        addScmCommit(upstreamBuild.getWorkspace(), "commit message - #deploy");

        // second build
        upstreamBuild = assertBuildStatusSuccess(upstream.scheduleBuild2(0).get());
        build = assertBuildStatusSuccess(project.scheduleBuild2(0, new UpstreamCause((Run<?, ?>) upstreamBuild)).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying Rundeck..."));
        assertTrue(s.contains("#deploy"));
        assertTrue(s.contains("in upstream build (" + upstreamBuild.getFullDisplayName() + ")"));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testFailedBuild() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("Default", "1", createOptions(), null, "", false, false, null, null, null);
        RundeckInstance instance = new RundeckInstance();
        instance.setName("Default");
        notifier.getDescriptor().addRundeckInstance(instance);

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.FAILURE));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying Rundeck"));

        addScmCommit(build.getWorkspace(), "commit message");

        // second build
        build = assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying Rundeck"));
    }

    public void testWaitForRundeckJob() throws Exception {

        RundeckManager client = new MockRundeckClientManager();
        RundeckInstanceBuilder instanceBuilder = new RundeckInstanceBuilder();
        instanceBuilder.setClient(client);
        instanceBuilder.name("Default");

        RundeckNotifier notifier = new RundeckNotifier("Default", "1", createOptions(), null, "", true, false, null, null, null);
        notifier.getDescriptor().setRundeckBuilder(instanceBuilder);
        notifier.getDescriptor().addRundeckInstance(instanceBuilder.build());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying Rundeck..."));
        assertTrue(s.contains("Notification succeeded !"));
        assertTrue(s.contains("Waiting for Rundeck execution to finish..."));
        assertTrue(s.contains("Rundeck execution #1 finished in 3 minutes 27 seconds, with status : SUCCEEDED"));
    }


    public void testGetTags(){

        RundeckNotifier notifier;
        String[] tags;

        notifier = new RundeckNotifier("Default", "1", null, null, "#deploy", false, true, null, null, null);
        tags= new String[] {"#deploy"};
        assertTrue(Arrays.equals(tags, notifier.getTagsList()));

        notifier = new RundeckNotifier("Default", "1", null, null, null, false, true, null, null, null);
        tags= new String[0];
        assertTrue(Arrays.equals(tags, notifier.getTagsList()));

        notifier = new RundeckNotifier("Default", "1", null, null, "", false, true, null, null, null);
        tags= new String[0];
        assertTrue(Arrays.equals(tags, notifier.getTagsList()));

        notifier = new RundeckNotifier("Default", "1", null, null, "  ", false, true, null, null, null);
        tags= new String[0];
        assertTrue(Arrays.equals(tags, notifier.getTagsList()));

        notifier = new RundeckNotifier("Default", "1", null, null, "#tag1, #tag2", false, true, null, null, null);
        tags= new String[] {"#tag1", "#tag2"};
        assertTrue(Arrays.equals(tags, notifier.getTagsList()));

    }


    private String createOptions() {
        Properties options = new Properties();
        options.setProperty("option1", "value 1");
        options.setProperty("workspace", "$WORKSPACE");
        options.setProperty("jobName", "$JOB_NAME");
        options.setProperty("buildNumber", "$BUILD_NUMBER");

        return createOptions(options);
    }

    private String createOptions(final Properties options) {
        StringWriter writer = new StringWriter();
        try {
            options.store(writer, "this is a comment line");
            return writer.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private SubversionSCM createScm() throws Exception {
        File emptyRepository = new CopyExisting(getClass().getResource("empty-svn-repository.zip")).allocate();
        return new SubversionSCM("file://" + emptyRepository.toURI().getPath());
    }

    private void addScmCommit(FilePath workspace, String commitMessage) throws Exception {
        SVNClientManager svnm = SubversionSCM.createSvnClientManager((ISVNAuthenticationProvider)null);
        SVNAuthenticationManager mgr = new SVNAuthenticationManager(new File(jenkins.root, ".svn"), "user", "password");
        mgr.setAuthenticationProvider(new CredentialsSVNAuthenticationProviderImpl(
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
                        "test-svn-creds", "Test SVN Credentials",
                        "user", "password")));
        svnm.setAuthenticationManager(mgr);

        FilePath newFilePath = workspace.child("new-file");
        File newFile = new File(newFilePath.getRemote());
        newFilePath.touch(System.currentTimeMillis());
        svnm.getWCClient().doAdd(newFile, false, false, false, SVNDepth.INFINITY, false, false);
        svnm.getCommitClient().doCommit(new File[] { newFile },
                false,
                commitMessage,
                null,
                null,
                false,
                false,
                SVNDepth.EMPTY);
    }

    /**
     * @param build
     * @param actionClass
     * @return true if the given build contains an action of the given actionClass
     */
    private boolean buildContainsAction(Build<?, ?> build, Class<?> actionClass) {
        for (Action action : build.getActions()) {
            if (actionClass.isInstance(action)) {
                return true;
            }
        }
        return false;
    }

    public void testJobWithNonDefaultLogin() throws Exception {
        String login = "myUser";
        String password = "myPassword";

        RundeckManager client = new MockRundeckClientManager(login, password);

        RundeckInstanceBuilder instanceBuilder = new RundeckInstanceBuilder();
        instanceBuilder.setClient(client);
        instanceBuilder.name("Default");

        RundeckNotifier notifier = new RundeckNotifier("Default", "1", createOptions(), null, "", true, false, login, Secret.fromString(password), null);
        notifier.getDescriptor().setRundeckBuilder(instanceBuilder);
        notifier.getDescriptor().addRundeckInstance(instanceBuilder.build());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // check config
        assertTrue(login.equals(notifier.getJobUser()));
        assertTrue(password.equals(notifier.getPassword()));

        // build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying Rundeck..."));
        assertTrue(s.contains("Notification succeeded !"));

    }

}
