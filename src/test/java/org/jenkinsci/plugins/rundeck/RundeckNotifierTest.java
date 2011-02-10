package org.jenkinsci.plugins.rundeck;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.scm.SubversionSCM;
import java.io.File;
import java.io.InputStream;
import org.apache.commons.io.FileUtils;
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
        SubversionSCM scm = createScm();

        RundeckNotifier notifier = new RundeckNotifier("group",
                                                       "job",
                                                       new String[] { "option1=value1" },
                                                       "#deploy",
                                                       false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(scm);

        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());

        addScmCommit(build.getWorkspace(), "commit message");
        build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());

        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying rundeck"));
    }

    public void testCommitWithoutTag() throws Exception {
        SubversionSCM scm = createScm();

        RundeckNotifier notifier = new RundeckNotifier("group", "job", new String[] { "option1=value1" }, "", false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(scm);

        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());

        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying rundeck..."));
        assertTrue(s.contains("Notification succeeded !"));

        addScmCommit(build.getWorkspace(), "commit message");
        build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());

        s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying rundeck..."));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testDeployCommitWithTagWontBreakTheBuild() throws Exception {
        SubversionSCM scm = createScm();

        RundeckNotifier notifier = new RundeckNotifier("group",
                                                       "job",
                                                       new String[] { "option1=value1" },
                                                       "#deploy",
                                                       false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(scm);

        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());

        addScmCommit(build.getWorkspace(), "commit message - #deploy");
        build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());

        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying rundeck..."));
        assertTrue(s.contains("#deploy"));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testDeployCommitWithTagWillBreakTheBuild() throws Exception {
        SubversionSCM scm = createScm();

        RundeckNotifier notifier = new RundeckNotifier("group",
                                                       "job",
                                                       new String[] { "option1=value1" },
                                                       "#deploy",
                                                       true);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance() {

            private static final long serialVersionUID = 1L;

            @Override
            public void scheduleJobExecution(String groupPath, String jobName, String... options)
                    throws RundeckLoginException, RundeckJobSchedulingException {
                throw new RundeckJobSchedulingException("Fake error for testing");
            }

        });

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(scm);

        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());

        addScmCommit(build.getWorkspace(), "commit message - #deploy");
        build = assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());

        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying rundeck..."));
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
        public void scheduleJobExecution(String groupPath, String jobName, String... options)
                throws RundeckLoginException, RundeckJobSchedulingException {
        }

        @Override
        public void checkXmlResponse(InputStream response) throws RundeckJobSchedulingException {
        }

    }
}
