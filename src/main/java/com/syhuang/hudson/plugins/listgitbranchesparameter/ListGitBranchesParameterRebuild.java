package com.syhuang.hudson.plugins.listgitbranchesparameter;

import com.sonyericsson.rebuild.RebuildParameterPage;
import com.sonyericsson.rebuild.RebuildParameterProvider;
import hudson.Extension;
import hudson.model.ParameterValue;

@Extension(optional = true)
public class ListGitBranchesParameterRebuild extends RebuildParameterProvider {
    @Override
    public RebuildParameterPage getRebuildPage(ParameterValue parameterValue) {

        if (parameterValue instanceof ListGitBranchesParameterValue) {
            return new RebuildParameterPage(parameterValue.getClass(),"value.jelly");
        } else {
            return null;
        }
    }
}
