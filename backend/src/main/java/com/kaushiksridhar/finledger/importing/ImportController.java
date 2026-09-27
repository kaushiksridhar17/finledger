package com.kaushiksridhar.finledger.importing;

import java.util.List;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.security.CurrentUser;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private static final String BUSY = "The server is busy with other imports. Please try again in a minute.";

    private final ImportService importService;
    private final ImportProcessor importProcessor;

    public ImportController(ImportService importService, ImportProcessor importProcessor) {
        this.importService = importService;
        this.importProcessor = importProcessor;
    }

    /**
     * Upload a statement. Returns 202 Accepted straight away with status QUEUED;
     * the file is processed in the background. Poll GET /api/imports/{id} to follow it.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportBatchResponse upload(@AuthenticationPrincipal Jwt jwt,
            @RequestParam("accountId") long accountId,
            @RequestParam("file") MultipartFile file) {

        long userId = CurrentUser.id(jwt);
        byte[] content = importService.readUpload(file);
        ImportBatchResponse batch = importService.createBatch(userId, accountId, file.getOriginalFilename());

        try {
            importProcessor.process(batch.id(), content);
        } catch (TaskRejectedException e) {
            importService.markFailed(batch.id(), BUSY);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, BUSY);
        }
        return batch;
    }

    @GetMapping
    public List<ImportBatchResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return importService.list(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public ImportBatchResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") long id) {
        return importService.get(CurrentUser.id(jwt), id);
    }
}
