package com.syhuang.hudson.plugins.listgitbranchesparameter;

import hudson.model.StringParameterValue;
import org.kohsuke.stapler.DataBoundConstructor;

public class ListGitBranchesParameterValue extends StringParameterValue {
    @DataBoundConstructor
    public ListGitBranchesParameterValue(String name, String value) {
        super(name, value);
    }
}
