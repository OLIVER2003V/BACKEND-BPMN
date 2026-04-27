package com.bpms.core.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 👇 NUEVO: Service de archivos. Antes el ArchivoController guardaba en filesystem
 * de EC2 directamente. Ahora delegamos al S3 client de AWS para tener
 * almacenamiento desacoplado, persistente y servible vía CDN.
 */
@Service
public class ArchivoService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    public ArchivoService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Sube un archivo al bucket S3 organizándolo por trámite.
     * Estructura final en S3: bpms-core-archivos-oliver/<tramiteId>/<uuid>.<ext>
     * Si tramiteId es null, se guarda en raíz: bpms-core-archivos-oliver/<uuid>.<ext>
     *
     * @return Map con metadata (nombreOriginal, url, tamano, etc.)
     */
    public Map<String, Object> subirArchivo(MultipartFile archivo, String tramiteId) throws IOException {
        if (archivo.isEmpty()) {
            throw new IOException("Archivo vacío");
        }

        // 1. Construir el "key" (path dentro del bucket)
        String nombreOriginal = archivo.getOriginalFilename();
        String extension = "";
        if (nombreOriginal != null && nombreOriginal.contains(".")) {
            extension = nombreOriginal.substring(nombreOriginal.lastIndexOf("."));
        }
        String nombreUnico = UUID.randomUUID() + extension;

        String key = (tramiteId != null && !tramiteId.isBlank())
                ? tramiteId + "/" + nombreUnico
                : nombreUnico;

        // 2. Subir al bucket
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(archivo.getContentType())
                .contentDisposition("inline; filename=\"" + nombreOriginal + "\"")
                .build();

        s3Client.putObject(putRequest,
                RequestBody.fromInputStream(archivo.getInputStream(), archivo.getSize()));

        // 3. Construir URL pública (el bucket ya tiene política de lectura pública)
        String urlPublica = String.format("https://%s.s3.%s.amazonaws.com/%s",
                bucketName, region, key);

        // 4. Retornar metadata
        Map<String, Object> resp = new HashMap<>();
        resp.put("nombreOriginal", nombreOriginal);
        resp.put("nombreAlmacenado", nombreUnico);
        resp.put("url", urlPublica);
        resp.put("tamano", archivo.getSize());
        resp.put("fechaSubida", LocalDateTime.now().toString());
        return resp;
    }

    /**
     * Elimina un archivo del bucket S3 dada su URL pública o su key.
     * Útil cuando el usuario quita un campo de archivo del formulario.
     */
    public void eliminarArchivo(String urlOKey) {
        if (urlOKey == null || urlOKey.isBlank()) return;

        // Si es URL completa, extraer el key
        String key = urlOKey;
        String prefix = String.format("https://%s.s3.%s.amazonaws.com/", bucketName, region);
        if (urlOKey.startsWith(prefix)) {
            key = urlOKey.substring(prefix.length());
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteRequest);
        } catch (Exception e) {
            // Log silencioso — no es crítico si falla un delete
            System.err.println("[ArchivoService] No se pudo eliminar de S3: " + key + " — " + e.getMessage());
        }
    }
}