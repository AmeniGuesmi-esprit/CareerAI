package com.careerai.interview.cv;

import com.careerai.interview.cv.CvService.CvSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/interview/cvs")
@RequiredArgsConstructor
public class CvController {

    static final String USER_HEADER = "X-User-Id";

    private final CvService service;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<CvSummary> upload(
            @RequestHeader(USER_HEADER) Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "label", required = false) String label) {
        CvSummary created = service.upload(userId, file, label);
        return ResponseEntity.created(URI.create("/api/interview/cvs/" + created.id())).body(created);
    }

    @GetMapping
    public Page<CvSummary> list(
            @RequestHeader(USER_HEADER) Long userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(userId, pageable);
    }

    @GetMapping("/{id}")
    public CvSummary get(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id) {
        return service.get(userId, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id) {
        service.delete(userId, id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
