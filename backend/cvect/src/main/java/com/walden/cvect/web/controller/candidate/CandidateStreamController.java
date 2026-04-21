package com.walden.cvect.web.controller.candidate;

import com.walden.cvect.security.CurrentUserService;
import com.walden.cvect.security.DataScopeService;
import com.walden.cvect.web.stream.CandidateStreamService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 候选人实时入库事件流
 */
@RestController
@RequestMapping("/api/candidates")
public class CandidateStreamController {

    private final CandidateStreamService streamService;
    private final CurrentUserService currentUserService;
    private final DataScopeService dataScopeService;

    public CandidateStreamController(
            CandidateStreamService streamService,
            CurrentUserService currentUserService,
            DataScopeService dataScopeService) {
        this.streamService = streamService;
        this.currentUserService = currentUserService;
        this.dataScopeService = dataScopeService;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).CANDIDATE_READ)")
    public SseEmitter stream() {
        return streamService.register(
                currentUserService.currentTenantId(),
                dataScopeService.hasTenantWideScope() ? null : currentUserService.currentUserIdOrNull());
    }
}
