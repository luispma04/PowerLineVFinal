package com.dji.sdk.sample.demo.missionoperator.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Utility class to manage storage of inspection photos
 */
public class PhotoStorageManager {
    private static final String TAG = "PhotoStorageManager";
    private static final String PHOTO_DIRECTORY = "structure_inspection";
    private static final String PHOTO_PREFIX = "inspection_";
    private static final String PHOTO_EXTENSION = ".jpg";

    private File storageDirectory;
    private List<PhotoInfo> savedPhotos = new ArrayList<>();

    public static class PhotoInfo {
        private File file;
        private String timestamp;
        private String structureId;
        private String photoId;

        public PhotoInfo(File file, String structureId, String photoId) {
            this.file = file;
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(file.lastModified()));
            this.structureId = structureId;
            this.photoId = photoId;
        }

        public File getFile() {
            return file;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getStructureId() {
            return structureId;
        }

        public String getPhotoId() {
            return photoId;
        }

        public String getFilename() {
            return file.getName();
        }
    }

    public PhotoStorageManager(Context context) {
        // Create storage directory if it doesn't exist
        storageDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), PHOTO_DIRECTORY);
        if (!storageDirectory.exists()) {
            storageDirectory.mkdirs();
        }

        // Load existing photos
        loadSavedPhotos();
    }

    /**
     * Save a photo to storage
     * @param photo The bitmap to save
     * @param structureId The ID of the structure
     * @param photoId The ID of the photo position
     * @return The newly created PhotoInfo if successful, null otherwise
     */
    public PhotoInfo savePhoto(Bitmap photo, int structureId, int photoId) {
        try {
            // Generate a unique filename
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = PHOTO_PREFIX + "s" + structureId + "_p" + photoId + "_" + timestamp + PHOTO_EXTENSION;
            File outputFile = new File(storageDirectory, filename);

            // Save the bitmap to a file
            FileOutputStream fos = new FileOutputStream(outputFile);
            photo.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();

            // Create a new PhotoInfo object
            PhotoInfo newPhoto = new PhotoInfo(outputFile, "S" + structureId, "P" + photoId);

            // Add to the list of saved photos
            savedPhotos.add(newPhoto);

            return newPhoto;
        } catch (IOException e) {
            Log.e(TAG, "Error saving photo: " + e.getMessage());
            return null;
        }
    }

    /**
     * Load saved photos from storage
     */
    private void loadSavedPhotos() {
        savedPhotos.clear();

        if (storageDirectory.exists()) {
            File[] files = storageDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().startsWith(PHOTO_PREFIX) && file.getName().endsWith(PHOTO_EXTENSION)) {
                        // Parse the filename to extract structure and photo IDs
                        String filename = file.getName();
                        String[] parts = filename.replace(PHOTO_PREFIX, "").split("_");

                        if (parts.length >= 2) {
                            String structureId = parts[0].startsWith("s") ? parts[0].substring(1) : parts[0];
                            String photoId = parts[1].startsWith("p") ? parts[1].substring(1) : parts[1];

                            PhotoInfo photoInfo = new PhotoInfo(file, "S" + structureId, "P" + photoId);
                            savedPhotos.add(photoInfo);
                        }
                    }
                }

                // Sort photos by date (newest first)
                Collections.sort(savedPhotos, new Comparator<PhotoInfo>() {
                    @Override
                    public int compare(PhotoInfo p1, PhotoInfo p2) {
                        return Long.compare(p2.getFile().lastModified(), p1.getFile().lastModified());
                    }
                });
            }
        }
    }

    /**
     * Get all saved photos
     * @return List of PhotoInfo objects
     */
    public List<PhotoInfo> getSavedPhotos() {
        return savedPhotos;
    }

    /**
     * Delete a photo from storage
     * @param photoInfo The PhotoInfo object to delete
     * @return true if successful, false otherwise
     */
    public boolean deletePhoto(PhotoInfo photoInfo) {
        if (photoInfo != null && photoInfo.getFile().exists()) {
            boolean success = photoInfo.getFile().delete();
            if (success) {
                savedPhotos.remove(photoInfo);
            }
            return success;
        }
        return false;
    }

    /**
     * Refresh the list of saved photos
     */
    public void refresh() {
        loadSavedPhotos();
    }

    /**
     * Get the storage directory
     * @return The storage directory as a File
     */
    public File getStorageDirectory() {
        return storageDirectory;
    }
}