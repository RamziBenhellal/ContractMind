package com.ramzi.backend.service;

import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageService(@Value("${contractmind.upload-dir}") String uploadDir) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to create directory: " + this.fileStorageLocation, ex);
        }

    }
public String storeFile(MultipartFile file) {
        if (!file.getContentType().equals("application/pdf")) {
            throw new RuntimeException("Invalid file type: " + file.getContentType() + ", expected only pdf file");
        }

        // Originalen Dateinamen bereinigen
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());

    // Damit keine Dateien überschrieben werden (falls 2 Nutzer "vertrag.pdf" hochladen),
    // setzen wir eine eindeutige ID (UUID) vor den Namen.
    String uniqueFileName = UUID.randomUUID().toString() + "_" + originalFileName;

    try {
        // Zielpfad zusammenbauen und Datei kopieren
        Path targetLocation = this.fileStorageLocation.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        // Wir geben den relativen Pfad zurück, um ihn in der Datenbank zu speichern
        return targetLocation.toString();
    } catch (IOException ex) {
        throw new RuntimeException("File cannot be stored: " + originalFileName, ex);
    }

}

}
