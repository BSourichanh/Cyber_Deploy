CREATE DATABASE IF NOT EXISTS devfolio;
USE devfolio;

GRANT ALL PRIVILEGES ON devfolio.* TO 'root'@'%' IDENTIFIED BY 'root';

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255),
    bio TEXT,
    role VARCHAR(50) DEFAULT 'USER',
    avatar_url VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255),
    description TEXT,
    github_url VARCHAR(500),
    image_url VARCHAR(500),
    owner_id BIGINT,
    is_public BOOLEAN DEFAULT true,
    FOREIGN KEY (owner_id) REFERENCES users(id)
);

INSERT INTO users (email, password, role, bio) VALUES
    ('admin@devfolio.com', '$2a$12$sVfxhMLz85NCzCFjgH/7VuaNkDYXDgcvtvfEfRejLlaDs3PultIGi', 'ADMIN',
     'Admin'),
    ('alice@student.com', '$2a$12$rrJ8.QeiSz1hwlJHh74bq.61waivVvqSgbf5ZtQ3kiDqbMlm/HYrC', 'USER',
     'Développeuse passionnée'),
    ('bob@student.com', '$2a$12$R1CpJciF96Xkvpxpsu2A5O9jb.7snHsM.KOjbs1lW/hNrJJmtGiWG', 'USER',
     'Étudiant en alternance');


INSERT INTO projects (title, description, github_url, owner_id, is_public) VALUES
    ('Mon Portfolio', 'Mon premier projet Vue.js', 'https://github.com/alice/portfolio', 2, true),
    ('API REST Spring', 'Backend pour mon app', 'https://github.com/bob/api', 3, false),
    ('Projet Client', 'Application de gestion de commandes',
     'https://github.com/bob/client-app', 3, false);

