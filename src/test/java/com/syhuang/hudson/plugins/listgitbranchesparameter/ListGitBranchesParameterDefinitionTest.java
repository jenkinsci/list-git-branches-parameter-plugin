package com.syhuang.hudson.plugins.listgitbranchesparameter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ListGitBranchesParameterDefinitionTest {

    @Test
    public void stripsBranchRefPrefix() {
        assertEquals("devel", ListGitBranchesParameterDefinition.stripBranchRefPrefix("/refs/heads/devel"));
        assertEquals("somefeature", ListGitBranchesParameterDefinition.stripBranchRefPrefix("refs/heads/somefeature"));
        assertEquals("feature/nested", ListGitBranchesParameterDefinition.stripBranchRefPrefix("refs/heads/feature/nested"));
        assertEquals("master", ListGitBranchesParameterDefinition.stripBranchRefPrefix("master"));
    }
}
