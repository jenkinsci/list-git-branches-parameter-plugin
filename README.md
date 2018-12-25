This plugin is highly motivated by [Git Parameter Plugin](https://wiki.jenkins.io/display/JENKINS/Git+Parameter+Plugin) but But unlike [Git Parameter Plugin](https://wiki.jenkins.io/display/JENKINS/Git+Parameter+Plugin), this plugin will not change working space at all at build-time


## Background

When using jenkins pipeline style job and defining pipeline through "Pipeline script" (not "Pipeline script from SCM"), it becomes difficult to use [Git Parameter Plugin](https://wiki.jenkins.io/display/JENKINS/Git+Parameter+Plugin) since the plugin uses SCM in the job definition.

Sometimes we want to specify a git branch or tag before as a parameter, for "Pipeline script" jobs that use SCM in the script, it is impossible with [Git Parameter Plugin](https://wiki.jenkins.io/display/JENKINS/Git+Parameter+Plugin). In this particular case, a plugin that can list remote git branches or tags without defining scm in the job is needed.

## HowToUse

Please refer to wiki [List Git Branches Parameter Plugin](https://wiki.jenkins.io/display/JENKINS/List+Git+Branches+Parameter+Plugin)