package com.devfolio.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class AvatarController {

    @PostMapping("/avatar")
    public ResponseEntity<?> setAvatarFromUrl(
            @RequestParam String url,
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Seul le protocole HTTPS est autorisé"));
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Hôte invalide"));
            }

            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Adresse de destination non autorisée"));
            }

            URL avatarUrl = uri.toURL();
            URLConnection conn = avatarUrl.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            try (InputStream in = conn.getInputStream()) {
                byte[] imageData = in.readNBytes(2 * 1024 * 1024);
                String base64 = Base64.getEncoder().encodeToString(imageData);
                return ResponseEntity.ok(Map.of("avatar", "data:image/png;base64," + base64));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Impossible de charger l'avatar depuis cette URL"));
        }
    }
}
