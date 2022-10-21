package jenkinsci.plugins.rundeck

import hudson.model.FreeStyleBuild
import hudson.model.Hudson
import hudson.model.Job
import hudson.model.Run
import hudson.security.AccessDeniedException2
import hudson.util.RunList
import jenkins.model.Jenkins
import org.jenkinsci.plugins.rundeck.OptionProvider
import org.kohsuke.stapler.StaplerRequest
import org.kohsuke.stapler.StaplerResponse
import spock.lang.Specification

import javax.servlet.http.HttpServletResponse

import static hudson.model.Run.ARTIFACTS

class OptionProviderSpec extends Specification {


    def "test option artifact without permissions"(){

        given:

        final Hudson jenkins = Mock()

        jenkins.getInstanceOrNull() >> jenkins
        jenkins.getInstance() >> jenkins

        Jenkins.HOLDER = new Jenkins.JenkinsHolder() {
            Jenkins getInstance() {
                return jenkins
            }
        }

        jenkins.getItemByFullName("test", _)>>Mock(Job) {
            getLastSuccessfulBuild() >> Mock(FreeStyleBuild) {
                checkPermission(ARTIFACTS) >> { throw new AccessDeniedException2(Jenkins.getAuthentication(), ARTIFACTS) }
            }
        }

        def response = Mock(StaplerResponse)

        when:
        OptionProvider optionProvider = new OptionProvider()
        def request = Mock(StaplerRequest){
            getParameter("project")>>"test"
            getParameter("build")>>"lastSuccessful"
        }
        def result = optionProvider.doArtifact(request,response )

        then:
        1 * response.sendError(HttpServletResponse.SC_BAD_REQUEST, {message-> message == "anonymous is missing the Run/Artifacts permission" });

    }

    def "test option artifact with permissions"(){

        given:

        final Hudson jenkins = Mock(){
            getRootUrl()>>"http://localhost:8080"
        }

        jenkins.getInstanceOrNull() >> jenkins
        jenkins.getInstance() >> jenkins

        Jenkins.HOLDER = new Jenkins.JenkinsHolder() {
            Jenkins getInstance() {
                return jenkins
            }
        }

        jenkins.getItemByFullName("test", _)>>Mock(Job) {
            getLastSuccessfulBuild() >> Mock(FreeStyleBuild) {
                getArtifacts()>> [
                        Mock(Run.Artifact){
                            getFileName()>>"test-1.0.jar"
                            getHref()>>"artifact/test-1.0.jar"
                        }
                ]
                getUrl()>>"/rundeck/"
            }
        }

        def writer = Mock(PrintWriter)
        def response = Mock(StaplerResponse){
            getWriter()>>writer
        }

        when:
        OptionProvider optionProvider = new OptionProvider()
        def request = Mock(StaplerRequest){
            getParameter("project")>>"test"
            getParameter("build")>>"lastSuccessful"
        }
        optionProvider.doArtifact(request,response )

        then:
        1*writer.append({json-> json == "[{\"name\":\"test-1.0.jar\",\"value\":\"http://localhost:8080/rundeck/artifact/artifact/test-1.0.jar\"}]"})

    }

    def "test option build without permissions"(){

        given:

        final Hudson jenkins = Mock()

        jenkins.getInstanceOrNull() >> jenkins
        jenkins.getInstance() >> jenkins

        Jenkins.HOLDER = new Jenkins.JenkinsHolder() {
            Jenkins getInstance() {
                return jenkins
            }
        }

        List builds = [
                Mock(FreeStyleBuild) {
                    getArtifacts()>> [
                            Mock(Run.Artifact){
                                getFileName()>>"test-1.0.jar"
                                getHref()>>"artifact/test-1.0.jar"
                            }
                    ]
                    getUrl()>>"/rundeck/"
                    checkPermission(ARTIFACTS) >> { throw new AccessDeniedException2(Jenkins.getAuthentication(), ARTIFACTS) }

                }
        ]

        jenkins.getItemByFullName("test", _)>>Mock(Job) {
            getBuilds()>> Mock(RunList){
                iterator()>>builds.iterator()
            }
        }

        def writer = Mock(PrintWriter)
        def response = Mock(StaplerResponse){
            getWriter()>>writer
        }

        when:
        OptionProvider optionProvider = new OptionProvider()
        def request = Mock(StaplerRequest){
            getParameter("project")>>"test"
            getParameter("artifactRegex")>>".jar"
            getParameter("build")>>"lastSuccessful"
        }
        def result = optionProvider.doBuild(request,response )

        then:
        1*writer.append({json-> json == "[]"})

    }
}