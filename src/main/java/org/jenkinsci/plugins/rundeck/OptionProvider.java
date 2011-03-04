package org.jenkinsci.plugins.rundeck;

import hudson.model.TopLevelItem;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.model.Run.Artifact;
import hudson.util.RunList;
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
     * Provider for artifacts of a specific build, with the name and absolute url of the artifact.<br>
     * Mandatory parameter : "project"<br>
     * Optional parameters : "build" (either a build number, or "lastStable", "lastSuccessful", "last")
     */
    public void doArtifact(StaplerRequest request, StaplerResponse response) throws IOException {
        AbstractProject<?, ?> project = findProject(request.getParameter("project"));
        if (project == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "You must provide a valid 'project' parameter !");
            return;
        }

        Run<?, ?> build = findBuild(request.getParameter("build"), project);
        if (build == null) {
            return;
        }

        List<Option> options = new ArrayList<OptionProvider.Option>();
        for (Artifact artifact : build.getArtifacts()) {
            options.add(new Option(artifact.getFileName(), buildArtifactUrl(build, artifact)));
        }

        writeJson(options, response);
    }

    /**
     * Provider for builds of a specific artifact, with the version/date of the build and absolute url of the artifact.<br>
     * Mandatory parameters : "project" and "artifact" (filename of the artifact)<br>
     * Optional parameters : "limit" (int), "includeLastStableBuild" (boolean), "includeLastSuccessfulBuild" (boolean),
     * "includeLastBuild" (boolean)
     */
    public void doBuild(StaplerRequest request, StaplerResponse response) throws IOException {
        // mandatory parameters
        AbstractProject<?, ?> project = findProject(request.getParameter("project"));
        if (project == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "You must provide a valid 'project' parameter !");
            return;
        }
        String artifactName = request.getParameter("artifact");
        if (StringUtils.isBlank(artifactName)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "You must provide a valid 'artifact' parameter !");
            return;
        }

        // optional parameters
        Integer limit;
        try {
            limit = Integer.parseInt(request.getParameter("limit"));
        } catch (NumberFormatException e) {
            limit = null;
        }

        // build options
        List<Option> options = new ArrayList<OptionProvider.Option>();
        RunList<?> builds = project.getBuilds();
        for (Run<?, ?> build : builds) {
            Artifact artifact = findArtifact(artifactName, build);
            if (artifact != null) {
                String buildName = "#" + build.getNumber() + " - " + build.getTimestampString2();
                options.add(new Option(buildName, buildArtifactUrl(build, artifact)));
            }

            if (limit != null && options.size() >= limit) {
                break;
            }
        }

        // add optional references to last / lastStable / lastSuccessful builds
        if (Boolean.valueOf(request.getParameter("includeLastStableBuild"))) {
            Run<?, ?> build = project.getLastStableBuild();
            Artifact artifact = findArtifact(artifactName, build);
            if (build != null && artifact != null) {
                options.add(0, new Option("lastStableBuild", buildArtifactUrl(build, artifact)));
            }
        }
        if (Boolean.valueOf(request.getParameter("includeLastSuccessfulBuild"))) {
            Run<?, ?> build = project.getLastSuccessfulBuild();
            Artifact artifact = findArtifact(artifactName, build);
            if (build != null && artifact != null) {
                options.add(0, new Option("lastSuccessfulBuild", buildArtifactUrl(build, artifact)));
            }
        }
        if (Boolean.valueOf(request.getParameter("includeLastBuild"))) {
            Run<?, ?> build = project.getLastBuild();
            Artifact artifact = findArtifact(artifactName, build);
            if (build != null && artifact != null) {
                options.add(0, new Option("lastBuild", buildArtifactUrl(build, artifact)));
            }
        }

        writeJson(options, response);
    }

    /**
     * Find the Jenkins project matching the given name.
     * 
     * @param projectName
     * @return an {@link AbstractProject} instance, or null if not found
     */
    private AbstractProject<?, ?> findProject(String projectName) {
        if (StringUtils.isBlank(projectName)) {
            return null;
        }

        TopLevelItem item = Hudson.getInstance().getItem(projectName);
        if (AbstractProject.class.isInstance(item)) {
            return (AbstractProject<?, ?>) item;
        }

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
     * Find an artifact of the given build, matching the artifactName (filename). If not found, return null.
     * 
     * @param artifactName filename of the artifact
     * @param build
     * @return an {@link Artifact} instance, or null if not found
     */
    private Artifact findArtifact(String artifactName, Run<?, ?> build) {
        if (build == null) {
            return null;
        }

        for (Artifact artifact : build.getArtifacts()) {
            if (StringUtils.equals(artifactName, artifact.getFileName())) {
                return artifact;
            }
        }

        return null;
    }

    /**
     * Build the absolute url of the given artifact
     * 
     * @param build
     * @param artifact
     * @return absolute url
     */
    private String buildArtifactUrl(Run<?, ?> build, Artifact artifact) {
        StringBuilder url = new StringBuilder();
        url.append(Hudson.getInstance().getRootUrlFromRequest());
        url.append(build.getUrl()).append("artifact/").append(artifact.getHref());
        return url.toString();
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
