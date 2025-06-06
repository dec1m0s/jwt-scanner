package ch.csnc.burp.jwtscanner.checks;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import ch.csnc.burp.jwtscanner.CommentHttpHandler;
import ch.csnc.burp.jwtscanner.Jwt;
import ch.csnc.burp.jwtscanner.JwtAuditIssues;
import ch.csnc.burp.jwtscanner.JwtScannerExtension;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CheckJkuPingback extends Check {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @Override
    public Optional<AuditIssue> perform(HttpRequestResponse baseRequestResponse, AuditInsertionPoint auditInsertionPoint) {
        var collaborator = JwtScannerExtension.api().collaborator().createClient();
        var jwt = Jwt.newBuilder(auditInsertionPoint.baseValue()).withHeader("jku", "http://%s".formatted(collaborator.generatePayload().toString())).build();
        var checkRequest = auditInsertionPoint.buildHttpRequestWithPayload(ByteArray.byteArray(jwt.encode())).withService(baseRequestResponse.httpService()).withHeader(CommentHttpHandler.COMMENT_HEADER, "jku pingback");
        var checkRequestResponse = JwtScannerExtension.api().http().sendRequest(checkRequest);
        executor.schedule(() -> {
            // Check later whether there was any interaction with the collaborator.
            // If so then create an issue.
            if (!collaborator.getAllInteractions().isEmpty()) {
                var markers = markersOf(baseRequestResponse, auditInsertionPoint);
                var auditIssue = JwtAuditIssues.jkuPingback(jwt, AuditIssueConfidence.FIRM, baseRequestResponse.withRequestMarkers(markers), checkRequestResponse);
                JwtScannerExtension.api().siteMap().add(auditIssue);
            }
        }, 30, TimeUnit.SECONDS);
        return Optional.empty();
    }

}
