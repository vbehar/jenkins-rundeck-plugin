package org.jenkinsci.plugins.rundeck;

import hudson.model.TopLevelItem;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.model.Run.Artifact;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import net.sf.json.JSONArray;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Option provider for RunDeck - see http://rundeck.org/docs/RunDeck-Guide.html#option-model-provider
 * 
 * @author Vincent Behar
 */
public class OptionProvider {

    /**
     * Provider for artifacts of a specific build, with the name and absolute url of the artifact.
     */
    public void doArtifact(StaplerRequest request, StaplerResponse response) throws IOException {
        AbstractProject<?, ?> project = findProject(request.getParameter("project"), response);
        if (project == null) {
            return;
        }

        Run<?, ?> build = findBuild(request.getParameter("build"), project);
        if (build == null) {
            return;
        }

        List<Option> options = new ArrayList<OptionProvider.Option>();
        for (Artifact artifact : build.getArtifacts()) {
            StringBuilder url = new StringBuilder();
            url.append(Hudson.getInstance().getRootUrlFromRequest());
            url.append(build.getUrl()).append("artifact/").append(artifact.getHref());
            options.add(new Option(artifact.getFileName(), url.toString()));
        }

        writeJson(options, response);
    }

    /**
     * Find the Jenkins project matching the given name.
     * 
     * @param projectName
     * @param response
     * @return an {@link AbstractProject} instance, or null if not found
     */
    private AbstractProject<?, ?> findProject(String projectName, StaplerResponse response) throws IOException {
        if (StringUtils.isBlank(projectName)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parameter 'project' is mandatory !");
            return null;
        }

        TopLevelItem item = Hudson.getInstance().getItem(projectName);
        if (item != null && item instanceof AbstractProject<?, ?>) {
            return (AbstractProject<?, ?>) item;
        }

        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No project with name : " + projectName);
        return null;
    }

    /**
     * Find a build for the given project, using the provided buildNumber as a hint. If not found, will fallback to the
     * last build.
     * 
     * @param buildNumber either a build number, or a reference ('lastStable', 'lastSuccessful', or 'last')
     * @param project
     * @return a {@link Run} instance representing the build, or null if we couldn't event find the last build.
     */
    private Run<?, ?> findBuild(String buildNumber, AbstractProject<?, ?> project) {
        // first, try with a direct build number reference
        try {
            Integer buildNb = Integer.parseInt(buildNumber);
            Run<?, ?> build = project.getBuildByNumber(buildNb);
            if (build != null) {
                return build;
            }
        } catch (NumberFormatException e) {
        }

        // try a string-reference
        if (StringUtils.equalsIgnoreCase("lastStable", buildNumber)) {
            Run<?, ?> build = project.getLastStableBuild();
            if (build != null) {
                return build;
            }
        } else if (StringUtils.equalsIgnoreCase("lastSuccessful", buildNumber)) {
            Run<?, ?> build = project.getLastSuccessfulBuild();
            if (build != null) {
                return build;
            }
        }

        // fallback to the last build
        return project.getLastBuild();
    }

    /**
     * Outputs the given list of options as a JSON. See format at
     * http://rundeck.org/docs/RunDeck-Guide.html#option-model-provider
     * 
     * @param options
     * @param response
     */
    private void writeJson(List<Option> options, StaplerResponse response) throws IOException {
        JSONArray array = new JSONArray();
        array.addAll(options);
        String json = array.toString();

        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().append(json);
    }

    /**
     * Javabean representation of an option
     */
    public static class Option implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String name;

        private final String value;

        public Option(String name, String value) {
            super();
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Option other = (Option) obj;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            if (value == null) {
                if (other.value != null)
                    return false;
            } else if (!value.equals(other.value))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "Option [name=" + name + ", value=" + value + "]";
        }
    }
}
