package com.devfolio.controller;

import com.devfolio.model.Project;
import com.devfolio.model.User;
import com.devfolio.repository.ProjectRepository;
import com.devfolio.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    @GetMapping
    public ResponseEntity<List<Project>> getAllProjects() {
        return ResponseEntity.ok(projectRepository.findByIsPublicTrue());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Project>> getProjectsByUser(@PathVariable Long userId) {
        User currentUser = getCurrentUser();
        if (currentUser != null && (userId.equals(currentUser.getId()) || "ADMIN".equalsIgnoreCase(currentUser.getRole()))) {
            return ResponseEntity.ok(projectRepository.findByOwnerId(userId));
        }
        return ResponseEntity.ok(projectRepository.findByOwnerIdAndIsPublicTrue(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> getProject(@PathVariable Long id) {
        return projectRepository.findById(id).map(project -> {
            if (Boolean.TRUE.equals(project.getIsPublic())) {
                return ResponseEntity.ok(project);
            }
            User currentUser = getCurrentUser();
            if (currentUser != null && (project.getOwnerId().equals(currentUser.getId()) || "ADMIN".equalsIgnoreCase(currentUser.getRole()))) {
                return ResponseEntity.ok(project);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).<Project>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody Project project) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        project.setOwnerId(currentUser.getId());
        return ResponseEntity.ok(projectRepository.save(project));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Project> updateProject(
            @PathVariable Long id,
            @RequestBody Project updated) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return projectRepository.findById(id).map(project -> {
            if (!project.getOwnerId().equals(currentUser.getId()) && !"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).<Project>build();
            }
            project.setTitle(updated.getTitle());
            project.setDescription(updated.getDescription());
            project.setGithubUrl(updated.getGithubUrl());
            project.setImageUrl(updated.getImageUrl());
            if (updated.getIsPublic() != null) {
                project.setIsPublic(updated.getIsPublic());
            }
            return ResponseEntity.ok(projectRepository.save(project));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return projectRepository.findById(id).map(project -> {
            if (!project.getOwnerId().equals(currentUser.getId()) && !"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            projectRepository.delete(project);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/import")
    public ResponseEntity<?> importFromGithub(@RequestParam String githubUrl) throws Exception {
        URI uri = URI.create(githubUrl);
        URL url = uri.toURL();
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost() == null
                || (!url.getHost().equals("github.com") && !url.getHost().endsWith(".github.com"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL GitHub non valide"));
        }
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        try (InputStream in = conn.getInputStream()) {
            byte[] data = in.readNBytes(2 * 1024 * 1024);
            String content = new String(data, StandardCharsets.UTF_8);
            return ResponseEntity.ok(Map.of("content", content));
        }
    }
}
