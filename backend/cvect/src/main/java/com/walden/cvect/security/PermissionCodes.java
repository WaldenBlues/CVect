package com.walden.cvect.security;

import java.util.List;

public final class PermissionCodes {

    public static final String JD_READ = "JD_READ";
    public static final String JD_WRITE = "JD_WRITE";
    public static final String JD_DELETE = "JD_DELETE";
    public static final String RESUME_UPLOAD = "RESUME_UPLOAD";
    public static final String CANDIDATE_READ = "CANDIDATE_READ";
    public static final String CANDIDATE_UPDATE_STATUS = "CANDIDATE_UPDATE_STATUS";
    public static final String SEARCH_RUN = "SEARCH_RUN";
    public static final String USER_MANAGE = "USER_MANAGE";
    public static final String ROLE_MANAGE = "ROLE_MANAGE";
    public static final String AUDIT_READ = "AUDIT_READ";
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    public static final List<String> ALL = List.of(
            JD_READ,
            JD_WRITE,
            JD_DELETE,
            RESUME_UPLOAD,
            CANDIDATE_READ,
            CANDIDATE_UPDATE_STATUS,
            SEARCH_RUN,
            USER_MANAGE,
            ROLE_MANAGE,
            AUDIT_READ,
            SYSTEM_ADMIN);

    public static final List<String> HR_MANAGER = List.of(
            JD_READ,
            JD_WRITE,
            JD_DELETE,
            RESUME_UPLOAD,
            CANDIDATE_READ,
            CANDIDATE_UPDATE_STATUS,
            SEARCH_RUN);

    public static final List<String> RECRUITER = List.of(
            JD_READ,
            RESUME_UPLOAD,
            CANDIDATE_READ,
            CANDIDATE_UPDATE_STATUS,
            SEARCH_RUN);

    private PermissionCodes() {
    }
}
