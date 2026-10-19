package com.careerai.skillgap.service;

import com.careerai.skillgap.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Stockage des fichiers CV sur disque, sous un nom généré (UUID). */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${storage.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de créer le dossier de stockage : " + root, e);
        }
    }

    public String save(byte[] data, String extension) {
        String name = UUID.randomUUID() + "." + extension;
        try {
            Files.write(root.resolve(name), data);
        } catch (IOException e) {
            throw ApiException.internal("Impossible d'enregistrer le fichier");
        }
        return name;
    }

    public void delete(String storedFileName) {
        try {
            Files.deleteIfExists(root.resolve(storedFileName).normalize());
        } catch (IOException ignored) {
            // suppression au mieux : la ligne en base est déjà supprimée
        }
    }
}
