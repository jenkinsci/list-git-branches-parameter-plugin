package com.syhuang.hudson.plugins.listgitbranches;

enum SortMode {
    NONE, ASCENDING_SMART, DESCENDING_SMART, ASCENDING, DESCENDING;

    public boolean getIsUsingSmartSort() {
        return this == ASCENDING_SMART || this == DESCENDING_SMART;
    }

    public boolean getIsDescending() {
        return this == DESCENDING_SMART || this == DESCENDING;
    }

    public boolean getIsSorting() {
        return this != NONE;
    }
}
