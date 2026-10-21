package com.devfolio.controller;

import com.devfolio.model.Project;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping("/projects")
    public ResponseEntity<?> searchProjects(@RequestParam String q) {
        String jpql = "SELECT p FROM Project p WHERE p.isPublic = true AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')))";

        List<Project> results = entityManager.createQuery(jpql, Project.class).setParameter("q", q).getResultList();
        return ResponseEntity.ok(results);
    }
}
