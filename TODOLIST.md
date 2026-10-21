# Todolist de Remediation DevFolio (Par Ordre de Priorite)

> [!IMPORTANT] **Application obligatoire des modifications (Rebuild Docker)**  
> Modifier un fichier de configuration ou de code source ne met pas a jour automatiquement un conteneur actif.  
> - **Pour toute modification dans `backend/`** (`Dockerfile`, `pom.xml`, Java) :  
>   Toujours executer : `sudo docker compose up -d --build backend`  
> - **Pour toute modification dans `frontend/`** (`.vue`, `.js`) :  
>   Toujours executer : `sudo docker compose up -d --build frontend`  
> - **Pour `docker-compose.yml` ou `database/init.sql`** :  
>   Toujours executer : `sudo docker compose down -v && sudo docker compose up -d`

---

## 1. Priorite Critique (Immediat - Risque RCE, vol de donnees et acces total)

### [x] 1. [SEC-021] Fermer le port de debogage JDWP 5005
- **Fichier & Lignes** : [`backend/Dockerfile#L17`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/Dockerfile#L17) et [`#L19`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/Dockerfile#L19)
- **Code actuel** :
  ```dockerfile
  EXPOSE 8080 5005
  CMD ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "app.jar"]
  ```
- **Nouveau code** :
  ```dockerfile
  EXPOSE 8080
  CMD ["java", "-jar", "app.jar"]
  ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : `nc -zv 172.23.0.3 5005` ou `jdb -attach 172.23.0.3:5005`
- **Resultat attendu** : `Connection refused`.

---

### [x] 2. [SEC-022] Supprimer la dependance Log4Shell (CVE-2021-44228)
- **Fichier & Lignes** : [`backend/pom.xml#L45-L48`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/pom.xml#L45-L48)
- **Code actuel** :
  ```xml
  <dependency>
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j-core</artifactId>
      <version>2.14.1</version>
  </dependency>
  ```
- **Nouveau code** : Supprimer integralement ce bloc de dependance pour utiliser Logback (defaut Spring Boot).
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** :
  ```bash
  curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"${jndi:ldap://test}","password":"x"}'
  ```
- **Resultat attendu** : Aucune exception Log4j ni lookup JNDI dans les logs.

---

### [x] 3. [SEC-019] Corriger l'injection SQL dans la recherche de projets
- **Fichier & Lignes** : [`SearchController.java#L20-L24`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/SearchController.java#L20-L24)
- **Code actuel** :
  ```java
  String sql = "SELECT * FROM projects WHERE title LIKE '%" + q + "%' OR description LIKE '%" + q + "%'";
  List<Project> results = entityManager.createNativeQuery(sql, Project.class).getResultList();
  ```
- **Nouveau code** :
  ```java
  String jpql = "SELECT p FROM Project p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))";
  List<Project> results = entityManager.createQuery(jpql, Project.class).setParameter("q", q).getResultList();
  ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : `curl "http://localhost:8080/api/search/projects?q='%20OR%201=1%23"`
- **Resultat attendu** : `[]` (aucun projet secret n'est retourne).

---

### [x] 4. [SEC-023] Bloquer les attaques SSRF et lecture de fichiers locaux (LFI)
- **Fichier 1** : [`AvatarController.java#L19-L23`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/AvatarController.java#L19-L23)
  - *Constat* : Vérifier uniquement le schéma `https` ne protège pas contre le SSRF vers des adresses privées (127.0.0.1, 169.254.169.254, plages RFC 1918) ni contre le DoS (`readAllBytes()` sans quota ni timeout).
  - *Nouveau code* : Résoudre l'adresse IP de l'hôte et bloquer les plages privées/bouclage, imposer un timeout de connexion (ex. 3s) et une limite de taille maximale (ex. 2 Mo).
- **Fichier 2** : [`ProjectController.java#L60-L68`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/ProjectController.java#L60-L68)
  - *Constat* : La validation d'hôte restreint à `github.com` bloque bien l'accès local mais `readAllBytes()` sans timeout ni quota mémoire reste vulnérable au DoS.
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : `curl -i -X POST "http://localhost:8080/api/users/avatar?url=https://127.0.0.1:8443"`
- **Resultat attendu** : `HTTP 400 Bad Request` ("Adresse cible non autorisée").

---

### [x] 5. [SEC-004] Verrouiller Spring Security et ajouter le filtre JWT
- **Fichier 1 (Creation)** : [`backend/src/main/java/com/devfolio/config/JwtFilter.java`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/config/JwtFilter.java)
  - *Code a creer* :
    ```java
    package com.devfolio.config;

    import com.devfolio.service.JwtService;
    import io.jsonwebtoken.Claims;
    import jakarta.servlet.FilterChain;
    import jakarta.servlet.ServletException;
    import jakarta.servlet.http.HttpServletRequest;
    import jakarta.servlet.http.HttpServletResponse;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
    import org.springframework.security.core.authority.SimpleGrantedAuthority;
    import org.springframework.security.core.context.SecurityContextHolder;
    import org.springframework.stereotype.Component;
    import org.springframework.web.filter.OncePerRequestFilter;

    import java.io.IOException;
    import java.util.List;

    @Component
    public class JwtFilter extends OncePerRequestFilter {

        @Autowired
        private JwtService jwtService;

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    Claims claims = jwtService.validateToken(token);
                    String role = claims.get("role", String.class);
                    if (role != null) {
                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(claims.getSubject(), null, List.of(authority));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception e) {
                    // Token invalide : ignore
                }
            }

            filterChain.doFilter(request, response);
        }
    }
    ```
- **Fichier 2** : [`backend/src/main/java/com/devfolio/config/SecurityConfig.java`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/config/SecurityConfig.java)
  - *Injection du filtre* :
    ```java
    @Autowired
    private JwtFilter jwtFilter;
    ```
  - *Ajout dans filterChain* :
    ```java
    .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/projects/**").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
    .addFilterBefore(jwtFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
    ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** :
  1. Sans jeton : `curl -i http://localhost:8080/api/admin/users` -> `HTTP 401` ou `403`.
  2. Avec jeton Admin : `curl -i http://localhost:8080/api/admin/users -H "Authorization: Bearer <TOKEN_ADMIN>"` -> `HTTP 200` (les utilisateurs s'affichent enfin sur la page `/admin`).

---

### [x] 6. [SEC-002] Changer la cle secrete JWT codee en dur
- **Fichier** : [`backend/src/main/resources/application.properties#L12`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/resources/application.properties#L12)
- **Code actuel** :
  ```properties
  jwt.secret=hardcoded-jwt-secret-do-not-use
  ```
- **Nouveau code** :
  ```properties
  jwt.secret=${JWT_SECRET}
  ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** :
  1. Generer un jeton de test signe avec l'ancienne cle :
     ```bash
     TOKEN=$(python3 -c "import jwt; print(jwt.encode({'sub': 'alex@devfolio.com', 'role': 'USER', 'userId': 1}, 'hardcoded-jwt-secret-do-not-use', algorithm='HS256'))")
     ```
  2. Envoyer la requete avec ce jeton :
     ```bash
     curl -i http://localhost:8080/api/projects -H "Authorization: Bearer $TOKEN"
     ```
- **Resultat attendu** : `HTTP 401 Unauthorized` (la signature avec l'ancienne cle n'est plus acceptee).

---

### [x] 7. [SEC-008] Corriger la faille BOLA / IDOR sur la modification des projets
- **Fichier & Lignes** : [`ProjectController.java#L43-L45`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/ProjectController.java#L43-L45)
- **Constat** : Le code compare `project.getOwnerId()` avec `updated.getOwnerId()` reçu dans le JSON client. L'attaquant peut contourner le contrôle en envoyant l'ID réel du propriétaire.
- **Nouveau code** :
  Extraire l'utilisateur authentifié (via `SecurityContextHolder` ou `JwtService`) et valider :
  ```java
  String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
  User currentUser = userRepository.findByEmail(currentUserEmail).orElseThrow();
  if (!project.getOwnerId().equals(currentUser.getId()) && !"ADMIN".equals(currentUser.getRole())) {
      return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).<Project>build();
  }
  ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : Tenter de modifier le projet 1 avec un token appartenant à un autre utilisateur (`userId != 2`).
- **Resultat attendu** : `HTTP 403 Forbidden`.

---

### [x] 8. [SEC-010] Securiser la reinitialisation de mot de passe
- **Fichier & Lignes** : [`AuthController.java#L77-L79`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/AuthController.java#L77-L79)
- **Code actuel** :
  ```java
  String resetUrl = "http://localhost:5173/reset-password?token=" + token + "&email=" + email;
  return ResponseEntity.ok(Map.of("resetUrl", resetUrl));
  ```
- **Nouveau code** :
  ```java
  return ResponseEntity.ok(Map.of("message", "Si l'adresse existe, un lien a ete envoye par email."));
  ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : `curl -i -X POST http://localhost:8080/api/auth/reset-password/request -d "email=admin@devfolio.com"`
- **Resultat attendu** : `HTTP 200` avec `{"message":"Si l'adresse existe, un lien a été envoyé par email."}` (aucun lien ni token dans la réponse JSON).

---

### [x] 9. [SEC-012] Migrer le hachage des mots de passe vers BCrypt
- **Fichier 1** : [`SecurityConfig.java#L44-L63`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/config/SecurityConfig.java#L44-L63)
  - *Code actuel* :
    ```java
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                try {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    byte[] hash = md.digest(raw.toString().getBytes());
                    return HexFormat.of().formatHex(hash);
                } catch (Exception e) {
                    return raw.toString();
                }
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                return encode(raw).equals(encoded);
            }
        };
    }
    ```
  - *Nouveau code* :
    ```java
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12);
    }
    ```
- **Fichier 2** : [`AuthService.java#L20-L33`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/service/AuthService.java#L20-L33)
  - *Code actuel* :
    ```java
    @Autowired
    private UserRepository userRepository;

    public User register(String email, String password) {
        String hashedPassword = md5Hash(password);

        log.debug("Registering user: " + email + " with password: " + password);

        User user = new User();
        user.setEmail(email);
        user.setPassword(hashedPassword);
        user.setRole("USER");
        return userRepository.save(user);
    }
    ```
  - *Nouveau code* :
    ```java
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public User register(String email, String password) {
        String hashedPassword = passwordEncoder.encode(password);

        log.debug("Registering user: " + email);

        User user = new User();
        user.setEmail(email);
        user.setPassword(hashedPassword);
        user.setRole("USER");
        return userRepository.save(user);
    }
    ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : Inscrire un compte et verifier en base avec :
  ```bash
  sudo docker exec devfolio-audit-mariadb-1 mysql -u root -proot devfolio -e "SELECT email, password FROM users ORDER BY id DESC LIMIT 1;"
  ```
- **Resultat attendu** : Chaine de 60 caracteres debutant par `$2a$` (format BCrypt).

---

### [x] 10. [SEC-016] Securiser le controle d'acces admin Frontend (Defense en profondeur)
- **Fichier & Lignes** : [`frontend/src/router/index.js`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/frontend/src/router/index.js)
- **Code actuel** :
  ```javascript
  {
    path: '/admin',
    component: () => import('@/views/AdminView.vue'),
    beforeEnter: (to, from, next) => {
      const user = JSON.parse(localStorage.getItem('devfolio_user') || 'null')
      if (user && user.role === 'ADMIN') {
        next()
      } else {
        next('/login')
      }
    }
  }
  ```
- **Nouveau code** :
  ```javascript
  function isAdmin() {
    const token = localStorage.getItem('devfolio_token')
    if (!token) return false
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      return payload.role === 'ADMIN'
    } catch {
      return false
    }
  }

  // Dans la route /admin :
  {
    path: '/admin',
    component: () => import('@/views/AdminView.vue'),
    beforeEnter: (to, from, next) => {
      if (!isAdmin()) {
        next('/')
      } else {
        next()
      }
    }
  }
  ```
- **Application** : `sudo docker compose up -d --build frontend`
- **Methode de test** :
  1. Se connecter avec un compte utilisateur normal (`alice@student.com`) et tenter d'ouvrir `http://localhost/admin`.
  2. Tenter d'injecter `localStorage.setItem('devfolio_user', JSON.stringify({role: 'ADMIN'}))` dans la console F12.
- **Resultat attendu** : Redirection immediate vers `/` (accueil). La vue admin reste masquee aux non-admins, et modifier `devfolio_user` dans `localStorage` ne permet plus d'ouvrir la page.

---

### [x] 11. [SEC-005] Supprimer les logs de mots de passe en clair
- **Fichier 1** : [`AuthController.java#L43`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/AuthController.java#L43)
  - *Code actuel* : `log.debug("Password received: " + password);`
  - *Nouveau code* : Supprimer completement cette ligne.
- **Fichier 2** : [`AuthService.java#L26`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/service/AuthService.java#L26)
  - *Code actuel* : `log.debug("Registering user: " + email + " with password: " + password);`
  - *Nouveau code* : Supprimer completement cette ligne.
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** :
  ```bash
  sudo docker logs devfolio-audit-backend-1 | grep -i "Password received"
  ```
- **Resultat attendu** : Aucun mot de passe dans les logs.

---

### [x] 12. [SEC-009] Masquer les mots de passe dans la route d'administration
- **Fichier** : [`backend/src/main/java/com/devfolio/model/User.java#L16`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/model/User.java#L16)
- **Code actuel** :
  ```java
  private String password;
  ```
- **Nouveau code** :
  ```java
  @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
  private String password;
  ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : `curl http://localhost:8080/api/admin/users`
- **Resultat attendu** : Le champ `"password"` n'apparait plus dans le JSON.

---

## 2. Priorite Elevee (Important - Fuite de donnees et configuration faible)

### [x] 13. [SEC-001] Fermer le port MariaDB 3307 et changer les identifiants root
- **Fichier 1** : [`docker-compose.yml#L5`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/docker-compose.yml#L5)
  - *Code actuel* :
    ```yaml
    ports:
      - "0.0.0.0:3307:3307"
    ```
  - *Nouveau code* : Supprimer ces 2 lignes.
- **Fichier 2** : [`backend/src/main/resources/application.properties#L2-L5`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/resources/application.properties#L2-L5)
  - *Code actuel* :
    ```properties
    spring.datasource.username=root
    spring.datasource.password=root
    ```
  - *Nouveau code* :
    ```properties
    spring.datasource.username=${DB_USER:devfolio_user}
    spring.datasource.password=${DB_PASSWORD:Secret123!}
    ```
- **Application** : `sudo docker compose down && sudo docker compose up -d`
- **Methode de test** : `nc -zv localhost 3307`
- **Resultat attendu** : `Connection refused`.

---

### [x] 14. [SEC-003] Desactiver les endpoints sensibles de Spring Boot Actuator
- **Fichier & Lignes** : [`backend/src/main/resources/application.properties#L16-L20`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/resources/application.properties#L16-L20)
- **Code actuel** :
  ```properties
  management.endpoints.web.exposure.include=*
  management.security.enabled=false
  ```
- **Nouveau code** :
  ```properties
  management.endpoints.web.exposure.include=health,info
  management.endpoint.shutdown.enabled=false
  management.endpoint.heapdump.enabled=false
  management.endpoint.env.enabled=false
  ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : `curl -i http://localhost:8080/actuator/env`
- **Resultat attendu** : `HTTP 404 Not Found`.

---

### [x] 15. [SEC-011] Creer un fichier `.gitignore` et renouveler les secrets
- **Fichier** : `.gitignore` (a creer a la racine du projet `devfolio-audit`)
- **Code a ajouter** :
  ```gitignore
  .env
  .env.*.local
  target/
  node_modules/
  dist/
  *.log
  ```
- **Application** : Aucun rebuild necessaire.
- **Methode de test** : `git status --ignored`
- **Resultat attendu** : Le fichier `.env` apparait dans les fichiers ignores.

---

### [x] 16. [SEC-013] Configurer une date d'expiration sur les jetons JWT
- **Fichier 1** : [`application.properties#L13`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/resources/application.properties#L13)
  - *Nouveau code* : `jwt.expiration=900000` (15 minutes)
- **Fichier 2** : [`JwtService.java#L27`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/service/JwtService.java#L27)
  - *Code actuel* : `// .setExpiration(new Date(System.currentTimeMillis() + expirationMs))`
  - *Nouveau code* : `.setExpiration(new Date(System.currentTimeMillis() + expirationMs))`
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : Decoder un jeton genere sur [jwt.io](https://jwt.io).
- **Resultat attendu** : Le champ `exp` est present.

---

### [x] 17. [SEC-014] Purger les donnees sous accord de confidentialite (NDA) des seeds
- **Fichier & Lignes** : [`database/init.sql#L38-L39`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/database/init.sql#L38-L39)
- **Code actuel** : `('Projet Secret', 'Données confidentielles du client XYZ — NDA signé', ...)`
- **Nouveau code** : `('Projet Public', 'Description factice de demonstration', ...)`
- **Application** : `sudo docker compose down -v && sudo docker compose up -d`
- **Methode de test** : `curl http://localhost:8080/api/projects/3`
- **Resultat attendu** : Plus aucune mention de NDA.

---

### [x] 18. [SEC-017] Supprimer la colonne des hashes MD5 dans la vue Admin
- **Fichier & Lignes** : [`frontend/src/views/AdminView.vue#L23`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/frontend/src/views/AdminView.vue#L23) et [`#L36`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/frontend/src/views/AdminView.vue#L36)
- **Code actuel** :
  ```html
  <th>Mot de passe</th>
  ...
  <td><code>{{ user.password }}</code></td>
  ```
- **Nouveau code** : Supprimer ces deux lignes du template HTML.
- **Application** : `sudo docker compose up -d --build frontend`
- **Methode de test** : Ouvrir `/admin` dans le navigateur.
- **Resultat attendu** : Aucune colonne mot de passe dans le tableau.

---

### [x] 19. [SEC-018] Restreindre la politique CORS et reactiver CSRF
- **Fichier & Lignes** : [`SecurityConfig.java#L23-L31`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/config/SecurityConfig.java#L23-L31)
- **Code actuel** : `config.setAllowedOrigins(List.of("*"));`
- **Nouveau code** : `config.setAllowedOrigins(List.of("http://localhost"));`
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : `curl -i -H "Origin: http://site-malveillant.com" http://localhost:8080/api/projects`
- **Resultat attendu** : Absence de l'en-tete `Access-Control-Allow-Origin: *`.

---

### [x] 20. [SEC-020] Ajouter un sel unique par mot de passe
- **Fichier & Lignes** : [`database/init.sql#L26-L32`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/database/init.sql#L26-L32)
- **Code actuel** : Mot de passe MD5 identique `0192023a7bbd73250516f069df18b500` pour tous les comptes.
- **Nouveau code** : L'activation de BCrypt (SEC-012) applique automatiquement un sel aleatoire unique par compte.
- **Application** : `sudo docker compose down -v && sudo docker compose up -d`
- **Methode de test** : Comparer les colonnes `password` en base de donnees.
- **Resultat attendu** : Aucun hash identique partage entre deux comptes.

---

### [x] 21. [SEC-024] Bloquer l'elevation de privileges via Mass Assignment
- **Fichier & Lignes** : [`UserController.java#L31-L39`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/UserController.java#L31-L39)
- **Code actuel** :
  ```java
  user.setRole(updated.getRole()); // Faille : assigne le rôle reçu
  ```
- **Nouveau code** : Supprimer la ligne `user.setRole(updated.getRole());`.
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : `curl -X PUT http://localhost:8080/api/users/2 -H "Content-Type: application/json" -d '{"role":"ADMIN"}'`
- **Resultat attendu** : Le role reste `USER` en base de donnees.

---

## 3. Priorite Moyenne (Modere - Injection XSS et messages bavards)

### [x] 22. [SEC-015] Corriger la faille XSS Stockee dans la biographie
- **Fichier & Ligne** : [`frontend/src/views/ProfileView.vue#L33`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/frontend/src/views/ProfileView.vue#L33)
- **Code actuel** :
  ```html
  <div class="bio" v-html="user.bio"></div>
  ```
- **Nouveau code** :
  ```html
  <div class="bio">{{ user.bio }}</div>
  ```
- **Application** : `sudo docker compose up -d --build frontend`
- **Methode de test** : Mettre `<img src=x onerror=alert(1)>` dans la bio et ouvrir `/profile/2`.
- **Resultat attendu** : Le texte apparait en brut, pas d'alerte JavaScript.

---

### [x] 23. [SEC-006] Uniformiser le message d'erreur a la connexion
- **Fichier & Lignes** : [`AuthController.java#L48-L54`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/AuthController.java#L48-L54)
- **Code actuel** :
  ```java
  if (userOpt.isEmpty()) {
      return ResponseEntity.status(401).body(Map.of("error", "Utilisateur inconnu"));
  }
  if (!authService.verifyPassword(password, userOpt.get().getPassword())) {
      return ResponseEntity.status(401).body(Map.of("error", "Mot de passe incorrect"));
  }
  ```
- **Nouveau code** :
  ```java
  if (userOpt.isEmpty() || !authService.verifyPassword(password, userOpt.get().getPassword())) {
      return ResponseEntity.status(401).body(Map.of("error", "Identifiants invalides"));
  }
  ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : Tester un mauvais email puis un mauvais mot de passe.
- **Resultat attendu** : `401 Unauthorized {"error":"Identifiants invalides"}` dans les deux cas.

---

## 4. Priorite Faible (Mineur - Enumeration a l'inscription)

### [x] 24. [SEC-007] Masquer la confirmation d'adresse email a l'inscription
- **Fichier & Lignes** : [`AuthController.java#L65-L67`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/AuthController.java#L65-L67)
- **Code actuel** :
  ```java
  if (userRepository.findByEmail(email).isPresent()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Email déjà utilisé"));
  }
  ```
- **Nouveau code** :
  ```java
  if (userRepository.findByEmail(email).isPresent()) {
      return ResponseEntity.ok(Map.of("message", "Demande d'inscription enregistree"));
  }
  ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : `curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"email":"admin@devfolio.com","password":"test"}'`
- **Resultat attendu** : Reponse generique ne revelant pas si le compte existe deja.

---

## 5. Découvertes Complémentaires & Faux Correctifs (SEC-025 à SEC-035)

### [x] 25. [SEC-025] Corriger la faille XSS DOM dans la vue projet
- **Fichier & Lignes** : [`frontend/src/views/ProjectView.vue#L17`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/frontend/src/views/ProjectView.vue#L17)
- **Code actuel** : `banner.innerHTML = \`Vous venez de : ${refParam}\``
- **Nouveau code** : `banner.textContent = \`Vous venez de : ${refParam}\``
- **Application** : `sudo docker compose up -d --build frontend`
- **Methode de test** : Ouvrir `http://localhost/project/1?ref=<img%20src=x%20onerror=alert(1)>` dans le navigateur.
- **Resultat attendu** : Le code s'affiche sous forme de texte brut sans aucune exécution de script.

---

### [x] 26. [SEC-026] Sécuriser la suppression de projet contre BOLA / IDOR
- **Fichier & Lignes** : [`ProjectController.java#L54-L58`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/ProjectController.java#L54-L58)
- **Code actuel** :
  ```java
  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteProject(@PathVariable Long id) {
      projectRepository.deleteById(id);
      return ResponseEntity.ok().build();
  }
  ```
- **Nouveau code** :
  ```java
  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteProject(@PathVariable Long id) {
      return projectRepository.findById(id).map(project -> {
          String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
          com.devfolio.model.User user = userRepository.findByEmail(email).orElse(null);
          if (user == null || (!project.getOwnerId().equals(user.getId()) && !"ADMIN".equals(user.getRole()))) {
              return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
          }
          projectRepository.delete(project);
          return ResponseEntity.ok().build();
      }).orElse(ResponseEntity.notFound().build());
  }
  ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : Supprimer un projet appartenant à un autre compte avec un token non admin.
- **Resultat attendu** : `HTTP 403 Forbidden`.

---

### [x] 27. [SEC-027] Sécuriser la modification de profil utilisateur (BOLA / Account Takeover)
- **Fichier & Lignes** : [`UserController.java#L31-L38`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/UserController.java#L31-L38)
- **Code actuel** :
  ```java
  @PutMapping("/users/{id}")
  public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User updated) {
      return userRepository.findById(id).map(user -> {
          user.setEmail(updated.getEmail());
          user.setBio(updated.getBio());
          return ResponseEntity.ok(userRepository.save(user));
      }).orElse(ResponseEntity.notFound().build());
  }
  ```
- **Nouveau code** :
  ```java
  @PutMapping("/users/{id}")
  public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User updated) {
      String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
      com.devfolio.model.User currentUser = userRepository.findByEmail(email).orElse(null);
      if (currentUser == null || (!currentUser.getId().equals(id) && !"ADMIN".equals(currentUser.getRole()))) {
          return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
      }
      return userRepository.findById(id).map(user -> {
          user.setBio(updated.getBio());
          return ResponseEntity.ok(userRepository.save(user));
      }).orElse(ResponseEntity.notFound().build());
  }
  ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : Tenter de modifier le profil ID 1 avec les identifiants d'Alice (ID 2).
- **Resultat attendu** : `HTTP 403 Forbidden`.

---

### [x] 28. [SEC-028] Restreindre l'accès aux projets privés (consultation et recherche)
- **Fichier 1** : [`ProjectController.java#L26-L29`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/ProjectController.java#L26-L29)
  - *Nouveau code* : Vérifier que si `!project.getIsPublic()`, l'utilisateur doit être authentifié et propriétaire (ou admin), sinon retourner 404/403.
- **Fichier 2** : [`SearchController.java#L20-L24`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/SearchController.java#L20-L24)
  - *Nouveau code* : Ajouter `AND p.isPublic = true` dans la requête JPQL.
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : `curl http://localhost:8080/api/projects/3` sans token, et `curl http://localhost:8080/api/search/projects?q=Client`.
- **Resultat attendu** : Projet privé masqué ou non retourné aux utilisateurs non autorisés.

---

### [x] 29. [SEC-029] Durcir la récupération d'avatar contre SSRF et DoS
- **Fichier & Lignes** : [`AvatarController.java#L19-L23`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/AvatarController.java#L19-L23)
- **Nouveau code** :
  - Résoudre l'adresse IP via `InetAddress.getByName(uri.getHost())`.
  - Rejeter si l'adresse est de type `isLoopbackAddress()`, `isSiteLocalAddress()` ou `isLinkLocalAddress()`.
  - Configurer `connectTimeout(3000)` et `readTimeout(3000)`.
  - Limiter la lecture à 2 Mo max avec un flux borné (`InputStream.readNBytes`).
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : Tenter de charger un avatar pointant sur `https://127.0.0.1` ou `https://169.254.169.254`.
- **Resultat attendu** : `HTTP 400 Bad Request`.

---

### [x] 30. [SEC-030] Corriger la gestion de session et du token à l'inscription
- **Fichier 1** : [`AuthController.java#L58-L60`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/controller/AuthController.java#L58-L60)
  - *Nouveau code* : Retourner un statut distinct (ex. HTTP 400 avec `{"error":"Email déjà utilisé"}`) ou une réponse claire pour que le client sache qu'aucun token n'a été émis.
- **Fichier 2** : [`RegisterView.vue#L19-L21`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/frontend/src/views/RegisterView.vue#L19-L21)
  - *Nouveau code* :
    ```javascript
    if (data.token && data.user) {
      auth.setToken(data.token, data.user)
      router.push('/')
    } else {
      error.value = data.message || 'Impossible de finaliser l\'inscription'
    }
    ```
- **Application** : `sudo docker compose up -d --build backend && sudo docker compose up -d --build frontend`
- **Methode de test** : Tenter de s'inscrire avec `admin@devfolio.com`.
- **Resultat attendu** : Le message d'erreur s'affiche sans redirection ni écriture de `undefined` dans `localStorage`.

---

### [x] 31. [SEC-031] Conformer la clé de signature JWT de secours (minimum 256 bits)
- **Fichier & Lignes** : [`JwtService.java#L18`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/service/JwtService.java#L18)
- **Code actuel** : `@Value("${jwt.secret:hardcoded-jwt-secret-do-not-use}")` (31 octets)
- **Nouveau code** : `@Value("${jwt.secret:devfolio-secure-default-jwt-secret-key-32b!}")` (>= 32 octets)
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : Démarrer le backend sans la variable d'environnement `JWT_SECRET`.
- **Resultat attendu** : Pas de crash `WeakKeyException` à l'initialisation des clés JJWT.

---

### [x] 32. [SEC-032] Désactiver l'exposition des stack traces et réduire le log en production
- **Fichier & Lignes** : [`backend/src/main/resources/application.properties#L22-L28`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/resources/application.properties#L22-L28)
- **Code actuel** :
  ```properties
  server.error.include-stacktrace=always
  logging.level.root=DEBUG
  ```
- **Nouveau code** :
  ```properties
  server.error.include-stacktrace=never
  logging.level.root=INFO
  logging.level.com.devfolio=INFO
  ```
- **Application** : `sudo docker compose up -d --build backend`
- **Methode de test** : Déclencher une erreur 500 sur l'API.
- **Resultat attendu** : Aucune trace d'exception interne dans le corps de la réponse JSON.

---

### [x] 33. [SEC-033] Ajouter une contrainte d'unicité (UNIQUE) sur la colonne email en base
- **Fichier 1** : [`database/init.sql#L8`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/database/init.sql#L8)
  - *Nouveau code* : `email VARCHAR(255) NOT NULL UNIQUE,`
- **Fichier 2** : [`User.java#L13-L14`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/backend/src/main/java/com/devfolio/model/User.java#L13-L14)
  - *Nouveau code* : `@Column(nullable = false, unique = true)`
- **Application** : `sudo docker compose down -v && sudo docker compose up -d`
- **Methode de test** : Exécuter `INSERT INTO users (email) VALUES ('admin@devfolio.com');` dans MariaDB.
- **Resultat attendu** : Erreur `Duplicate entry 'admin@devfolio.com' for key 'email'`.

---

### [x] 34. [SEC-034] Mettre à jour la dépendance Axios vulnérable vers une version sécurisée
- **Fichier & Lignes** : [`frontend/package.json#L15`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/frontend/package.json#L15)
- **Code actuel** : `"axios": "0.21.1"`
- **Nouveau code** : `"axios": "^1.7.9"`
- **Application** : `sudo docker compose up -d --build frontend`
- **Methode de test** : Vérification dans le conteneur `npm list axios`.
- **Resultat attendu** : Version Axios >= 1.7.x installée sans vulnérabilités CVE connues.

---

### [x] 35. [SEC-035] Corriger la baseURL frontend vers le reverse proxy et aligner CORS
- **Fichier & Lignes** : [`frontend/src/services/api.js#L4`](file:///home/user/Documents/Cours/CyberSecuritée/Demo/devfolio-audit/frontend/src/services/api.js#L4)
- **Code actuel** : `baseURL: 'http://localhost:8080/api'`
- **Nouveau code** : `baseURL: '/api'`
- **Application** : `sudo docker compose up -d --build frontend`
- **Methode de test** : Accéder à l'application depuis `http://127.0.0.1` ou l'IP locale de la machine.
- **Resultat attendu** : Les requêtes passent par le port 80 via Nginx sans blocage CORS ni dépendance au port 8080.
