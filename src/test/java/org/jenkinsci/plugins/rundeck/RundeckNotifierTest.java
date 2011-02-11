package org.jenkinsci.plugins.rundeck;

import hudson.FilePath;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.scm.SubversionSCM;
import java.io.File;
import java.io.InputStream;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.rundeck.RundeckNotifier.RundeckExecutionBuildBadgeAction;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.MockBuilder;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.SVNClientManager;

/**
 * Test the {@link RundeckNotifier}
 * 
 * @author Vincent Behar
 */
public class RundeckNotifierTest extends HudsonTestCase {

    public void testStandardCommit() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("group",
                                                       "job",
                                                       new String[] { "option1=value1" },
                                                       "#deploy",
                                                       false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying RunDeck"));

        addScmCommit(build.getWorkspace(), "commit message");

        // second build
        build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying RunDeck"));
    }

    public void testCommitWithoutTag() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("group", "job", new String[] { "option1=value1" }, "", false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("Notification succeeded !"));

        addScmCommit(build.getWorkspace(), "commit message");

        // second build
        build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testDeployCommitWithTagWontBreakTheBuild() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("group",
                                                       "job",
                                                       new String[] { "option1=value1" },
                                                       "#deploy",
                                                       false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying RunDeck"));

        addScmCommit(build.getWorkspace(), "commit message - #deploy");

        // second build
        build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("#deploy"));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testDeployCommitWithTagWillBreakTheBuild() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("group",
                                                       "job",
                                                       new String[] { "option1=value1" },
                                                       "#deploy",
                                                       true);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance() {

            private static final long serialVersionUID = 1L;

            @Override
            public String scheduleJobExecution(String groupPath, String jobName, String... options)
                    throws RundeckLoginException, RundeckJobSchedulingException {
                throw new RundeckJobSchedulingException("Fake error for testing");
            }

        });

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying RunDeck"));

        addScmCommit(build.getWorkspace(), "commit message - #deploy");

        // second build
        build = assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("#deploy"));
        assertTrue(s.contains("Scheduling failed"));
        assertTrue(s.contains("Fake error for testing"));
    }

    private SubversionSCM createScm() throws Exception {
        File emptyRepository = new CopyExisting(getClass().getResource("empty-svn-repository.zip")).allocate();
        return new SubversionSCM("file://" + emptyRepository.getPath());
    }

    private void addScmCommit(FilePath workspace, String commitMessage) throws Exception {
        SVNClientManager svnm = SubversionSCM.createSvnClientManager();

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

    /**
     * Just a mock {@link RundeckInstance} which is always successful
     */
    private static class MockRundeckInstance extends RundeckInstance {

        private static final long serialVersionUID = 1L;

        public MockRundeckInstance() {
            super("", "", "");
        }

        @Override
        public boolean isConfigurationValid() {
            return true;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public boolean isLoginValid() {
            return true;
        }

        @Override
        public String scheduleJobExecution(String groupPath, String jobName, String... options)
                throws RundeckLoginException, RundeckJobSchedulingException {
            return "http://localhost:4440/execution/follow/1";
        }

        @Override
        public String parseExecutionUrl(InputStream response) throws RundeckJobSchedulingException {
            return "/execution/follow/1";
        }

    }
}
