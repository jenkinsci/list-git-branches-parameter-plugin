package com.syhuang.hudson.plugins.listgitbranchesparameter;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.*;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.cli.CLICommand;
import hudson.model.*;
import hudson.plugins.git.GitException;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ListGitBranchesParameterDefinition extends ParameterDefinition implements Comparable<ListGitBranchesParameterDefinition> {
    private static final String PARAMETER_TYPE_TAG = "PT_TAG";
    private static final String PARAMETER_TYPE_BRANCH = "PT_BRANCH";
    private static final String PARAMETER_TYPE_TAG_BRANCH = "PT_BRANCH_TAG";
    private static final String EMPTY_JOB_NAME = "EMPTY_JOB_NAME";
    private static final String DEFAULT_LIST_SIZE = "5";
    private static final String REFS_TAGS_PATTERN = ".*refs/tags/";
    private static final String REFS_HEADS_PATTERN = ".*refs/heads/";
    private static final Logger LOGGER = Logger.getLogger(ListGitBranchesParameterDefinition.class.getName());
    private final UUID uuid;
    private String remoteURL;
    private String credentialsId;
    private String defaultValue;
    private String type;
    private String tagFilter;
    private String branchFilter;
    private SortMode sortMode;
    private SelectedValue selectedValue;
    private Boolean quickFilterEnabled;
    private String listSize;


    @DataBoundConstructor
    public ListGitBranchesParameterDefinition(String name, String description, String remoteURL, String credentialsId, String defaultValue,
                                              SortMode sortMode, SelectedValue selectedValue, Boolean quickFilterEnabled,
                                              String type, String tagFilter, String branchFilter) {
       super(name, description);
       this.remoteURL = remoteURL;
        this.credentialsId = credentialsId;
        this.defaultValue = defaultValue;
        this.uuid = UUID.randomUUID();
        this.sortMode = sortMode;
        this.selectedValue = selectedValue;
        this.quickFilterEnabled = quickFilterEnabled;
        this.listSize = DEFAULT_LIST_SIZE;
		
       setType(type);
        setTagFilter(tagFilter);
        setBranchFilter(branchFilter);
    }

    public ListGitBranchesParameterDefinition(String name, String description, String remoteURL, String credentialsId, String defaultValue,
                                              SortMode sortMode, SelectedValue selectedValue, Boolean quickFilterEnabled,
                                              String type, String tagFilter, String branchFilter, String listSize) {
        super(name, description);
        this.remoteURL = remoteURL;
        this.credentialsId = credentialsId;
        this.defaultValue = defaultValue;
        this.uuid = UUID.randomUUID();
        this.sortMode = sortMode;
        this.selectedValue = selectedValue;
        this.quickFilterEnabled = quickFilterEnabled;
        this.listSize = listSize;

        setType(type);
        setTagFilter(tagFilter);
        setBranchFilter(branchFilter);
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        Object value = jo.get("value");
        StringBuilder strValue = new StringBuilder();
        if (value instanceof String) {
            strValue.append(value);
        } else if (value instanceof JSONArray) {
            JSONArray jsonValues = (JSONArray) value;
            for (int i = 0; i < jsonValues.size(); i++) {
                strValue.append(jsonValues.getString(i));
                if (i < jsonValues.size() - 1) {
                    strValue.append(",");
                }
            }
        }

        if (strValue.length() == 0) {
            strValue.append(defaultValue);
        }

        return new ListGitBranchesParameterValue(jo.getString("name"), strValue.toString());
    }

    @Override
    public ParameterValue createValue(StaplerRequest req) {
        String value[] = req.getParameterValues(getName());
        if (value == null || value.length == 0 || StringUtils.isBlank(value[0])) {
            return this.getDefaultParameterValue();
        } else {
            return new ListGitBranchesParameterValue(getName(), value[0]);
        }
    }

    @Override
    public ParameterValue createValue(CLICommand command, String value) {
        if (StringUtils.isNotEmpty(value)) {
            return new ListGitBranchesParameterValue(getName(), value);
        }
        return getDefaultParameterValue();
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        String defValue = getDefaultValue();
        if (!StringUtils.isBlank(defaultValue)) {
            return new ListGitBranchesParameterValue(getName(), defValue);
        }

        switch (getSelectedValue()) {
            case TOP:
                try {
                    ListBoxModel valueItems = getDescriptor().doFillValueItems(getParentJob(), getName());
                    if (valueItems.size() > 0) {
                        return new ListGitBranchesParameterValue(getName(), valueItems.get(0).value);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, getCustomJobName() + " " + " ", e);
                }
                break;
            case DEFAULT:
            case NONE:
            default:
                return super.getDefaultParameterValue();
        }

        return super.getDefaultParameterValue();
    }

    public String getDivUUID() {
        return getName() + "-" + uuid;
    }

    private String getCustomJobName() {
        Job job = getParentJob();
        String fullName = job != null ? job.getFullName() : EMPTY_JOB_NAME;
        return "[ " + fullName + " ]";
    }

    private Job getParentJob() {
        Job context = null;
        List<Job> jobs = Jenkins.get().getAllItems(Job.class);

        for (Job job : jobs) {
            if (!(job instanceof TopLevelItem)) continue;

            ParametersDefinitionProperty property = (ParametersDefinitionProperty) job.getProperty(ParametersDefinitionProperty.class);

            if (property != null) {
                List<ParameterDefinition> parameterDefinitions = property.getParameterDefinitions();

                if (parameterDefinitions != null) {
                    for (ParameterDefinition pd : parameterDefinitions) {
                        if (pd instanceof ListGitBranchesParameterDefinition && ((ListGitBranchesParameterDefinition) pd).compareTo(this) == 0) {
                            context = job;
                            break;
                        }
                    }
                }
            }
        }

        return context;
    }

    public SelectedValue getSelectedValue() {
        return selectedValue == null ? SelectedValue.TOP : selectedValue;
    }

    public Boolean getQuickFilterEnabled() {
        return quickFilterEnabled;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getRemoteURL() {
        return remoteURL;
    }

    public void setRemoteURL(String remoteURL) {
        this.remoteURL = remoteURL;
    }

    public String getListSize() {
        return listSize == null ? DEFAULT_LIST_SIZE : listSize;
    }

    public void setListSize(String listSize) {
        this.listSize = listSize;
    }

    public SortMode getSortMode() {
        return this.sortMode == null ? SortMode.NONE : this.sortMode;
    }

    public void setSortMode(SortMode sortMode) {
        this.sortMode = sortMode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        if (isParameterTypeCorrect(type)) {
            this.type = type;
        } else {
            this.type = PARAMETER_TYPE_BRANCH;
        }
    }

    public String getTagFilter() {
        return tagFilter;
    }

    public void setTagFilter(String tagFilter) {
        if (StringUtils.isEmpty(StringUtils.trim(tagFilter))) {
            tagFilter = "*";
        }
        this.tagFilter = tagFilter;
    }

    public String getBranchFilter() {
        return branchFilter;
    }

    public void setBranchFilter(String branchFilter) {
        if (StringUtils.isEmpty(StringUtils.trim(branchFilter))) {
            branchFilter = ".*";
        }
        this.branchFilter = branchFilter;
    }

    private boolean isParameterTypeCorrect(String type) {
        return type.equals(PARAMETER_TYPE_TAG) || type.equals(PARAMETER_TYPE_BRANCH) || type.equals(PARAMETER_TYPE_TAG_BRANCH);
    }

    public ArrayList<String> sortByName(Set<String> set) {

        ArrayList<String> tags = new ArrayList<>(set);

        if (getSortMode().getIsSorting()) {
            Collections.sort(tags, new SmartNumberStringComparator());
        } else {
            Collections.sort(tags);
        }
        return tags;
    }

    private ArrayList<String> sort(Set<String> toSort) {
        ArrayList<String> sorted;

        if (this.getSortMode().getIsSorting()) {
            sorted = sortByName(toSort);
            if (this.getSortMode().getIsDescending()) {
                Collections.reverse(sorted);
            }
        } else {
            sorted = new ArrayList<>(toSort);
        }
        return sorted;
    }

    private void sortAndPutToParam(Set<String> setElement, Map<String, String> paramList) {
        List<String> sorted = sort(setElement);

        for (String element : sorted) {
            paramList.put(element, element);
        }
    }

    private Set<String> getTag(GitClient gitClient, String gitUrl) throws InterruptedException {
        Set<String> tagSet = new HashSet<>();
        try {
            Map<String, ObjectId> tags = gitClient.getRemoteReferences(gitUrl, tagFilter, false, true);
            for (String tagName : tags.keySet()) {
                tagSet.add(tagName.replaceFirst(REFS_TAGS_PATTERN, ""));
            }
        } catch (GitException e) {
            LOGGER.log(Level.WARNING, e.getMessage(), e);
        }
        return tagSet;
    }

    private Set<String> getBranch(GitClient gitClient, String gitUrl) throws InterruptedException {
        Set<String> branchSet = new HashSet<>();
        String remoteName = "origin";
        Pattern branchFilterPattern = compileBranchFilterPattern();

        Map<String, ObjectId> branches = gitClient.getRemoteReferences(gitUrl, null, true, false);
        Iterator<String> remoteBranchesName = branches.keySet().iterator();
        while (remoteBranchesName.hasNext()) {
            //String branchName = strip(remoteBranchesName.next(), remoteName);
            String branchName = remoteBranchesName.next();
            Matcher matcher = branchFilterPattern.matcher(branchName);
            if (matcher.matches()) {
                if (matcher.groupCount() == 1) {
                    branchSet.add(matcher.group(1));
                } else {
                    branchSet.add(branchName);
                }
            }
        }
        return branchSet;
    }

    // looks like this strip is required by git plugin
    private String strip(String name, String remote) {
        return remote + "/" + name.substring(name.indexOf('/', 5) + 1);
    }

    @Nonnull
    private Map<String, String> generateContents(Job job) throws IOException, InterruptedException {
        Map<String, String> paramList = new LinkedHashMap<String, String>();
        GitClient gitClient = createGitClient(job);
        try {
            if (isTagType()) {
                Set<String> tagSet = getTag(gitClient, remoteURL);
                sortAndPutToParam(tagSet, paramList);
            }
            if (isBranchType()) {
                Set<String> branchSet = getBranch(gitClient, remoteURL);
                sortAndPutToParam(branchSet, paramList);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            paramList.clear();
            paramList.put(e.getMessage(), e.getMessage());
        }
        return paramList;
    }

    private Boolean isTagType() {
        return type.equalsIgnoreCase(PARAMETER_TYPE_TAG) || type.equalsIgnoreCase(PARAMETER_TYPE_TAG_BRANCH);
    }

    private Boolean isBranchType() {
        return type.equalsIgnoreCase(PARAMETER_TYPE_BRANCH) || type.equalsIgnoreCase(PARAMETER_TYPE_TAG_BRANCH);
    }


    private GitClient createGitClient(Job job) throws IOException, InterruptedException {
        final Computer computer = Jenkins.get().toComputer();
        EnvVars env;
        if (computer != null) {
            env = computer.getEnvironment();
        } else {
          env = null;
        }

        Git git = Git.with(TaskListener.NULL, env);

        GitClient c = git.getClient();
        List<StandardUsernameCredentials> urlCredentials = CredentialsProvider.lookupCredentials(
                StandardUsernameCredentials.class, job, ACL.SYSTEM, URIRequirementBuilder.fromUri(remoteURL).build()
        );
        CredentialsMatcher ucMatcher = CredentialsMatchers.withId(credentialsId);
        CredentialsMatcher idMatcher = CredentialsMatchers.allOf(ucMatcher, GitClient.CREDENTIALS_MATCHER);
        StandardUsernameCredentials credentials = CredentialsMatchers.firstOrNull(urlCredentials, idMatcher);

        if (credentials != null) {
            c.addCredentials(remoteURL, credentials);
            if (job != null && job.getLastBuild() != null) {
                CredentialsProvider.track(job.getLastBuild(), credentials);
            }
        }
        return c;
    }

    private Pattern compileBranchFilterPattern() {
        Pattern branchFilterPattern;
        try {
            branchFilterPattern = Pattern.compile(branchFilter);
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "Invalid Branch Filter Pattern", e);
            branchFilterPattern = Pattern.compile(".*");
        }
        return branchFilterPattern;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public int compareTo(ListGitBranchesParameterDefinition o) {
        return o.uuid.equals(uuid) ? 0 : -1;
    }

    @Symbol("listGitBranches")
    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return ResourceBundleHolder.get(ListGitBranchesParameterDefinition.class).format("displayName");
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context, @QueryParameter String remote) {
            if (context == null || !context.hasPermission(Item.EXTENDED_READ)) {
                return new StandardListBoxModel();
            }
            return fillCredentialsIdItems(context, remote);
        }

        public ListBoxModel fillCredentialsIdItems(Item context, String remote) {
            List<DomainRequirement> domainRequirements;
            if (remote == null) {
                domainRequirements = Collections.emptyList();
            } else {
                domainRequirements = URIRequirementBuilder.fromUri(remote.trim()).build();
            }

            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .withMatching(
                            CredentialsMatchers.anyOf(
                                    CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                    CredentialsMatchers.instanceOf(StandardCertificateCredentials.class),
                                    CredentialsMatchers.instanceOf(SSHUserPrivateKey.class)
                            ),
                            CredentialsProvider.lookupCredentials(StandardCredentials.class,
                                    context,
                                    ACL.SYSTEM,
                                    domainRequirements)
                    );
        }

        public FormValidation doCheckRemoteURL(StaplerRequest req, @AncestorInPath Item context, @QueryParameter String value) {
            String url = Util.fixEmptyAndTrim(value);

            if (url == null) {
                return FormValidation.error("Repository URL is required");
            }

            if (url.indexOf('$') != -1) {
                return FormValidation.warning("This repository URL is parameterized, syntax validation skipped");
            }

            try {
                new URIish(value);
            } catch (URISyntaxException e) {
                return FormValidation.error("Repository URL is illegal");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillValueItems(@AncestorInPath Job<?, ?> context, @QueryParameter String param)
                throws IOException, InterruptedException {
            ListBoxModel items = new ListBoxModel();
            if (context != null && context.hasPermission(Item.BUILD)) {
                ParametersDefinitionProperty prop = context.getProperty(ParametersDefinitionProperty.class);
                if (prop != null) {
                    ParameterDefinition def = prop.getParameterDefinition(param);
                    if (def instanceof ListGitBranchesParameterDefinition) {
                        Map<String, String> paramList = ((ListGitBranchesParameterDefinition) def).generateContents(context);
                        for (Map.Entry<String, String> entry : paramList.entrySet()) {
                            items.add(entry.getValue(), entry.getKey());
                        }
                    }
                }
            }
            return items;
        }

        public FormValidation doCheckBranchFilter(@QueryParameter String value) {
            String errorMessage = "Invalid Branch Filter Pattern";
            return validateRegularExpression(value, errorMessage);
        }

        private FormValidation validateRegularExpression(String value, String errorMessage) {
            try {
                Pattern.compile(value);
            } catch (PatternSyntaxException e) {
                LOGGER.log(Level.WARNING, errorMessage, e);
                return FormValidation.error(errorMessage);
            }
            return FormValidation.ok();
        }
    }
}
