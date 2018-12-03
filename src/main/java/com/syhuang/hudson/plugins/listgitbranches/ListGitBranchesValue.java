package com.syhuang.hudson.plugins.listgitbranches;

import hudson.model.StringParameterValue;
import org.kohsuke.stapler.DataBoundConstructor;

public class ListGitBranchesValue extends StringParameterValue {
    @DataBoundConstructor
    public ListGitBranchesValue(String name, String value) {
        super(name, value);
    }
}
