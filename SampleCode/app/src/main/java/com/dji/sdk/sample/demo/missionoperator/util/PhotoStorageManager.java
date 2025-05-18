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
 * Utility class to manage storage of inspection photos organized by structure
 */
public class PhotoStorageManager {
    private static final String TAG = "PhotoStorageManager";
    private static final String BASE_DIRECTORY = "structure_inspection";
    private static final String STRUCTURE_FOLDER_PREFIX = "S";
    private static final String PHOTO_PREFIX = "inspection_";
    private static final String PHOTO_EXTENSION = ".jpg";

    private File baseDirectory;
    private List<PhotoInfo> photoCache = new ArrayList<>();
    private Context context;

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

        public int getStructureIdAsInt() {
            try {
                return Integer.parseInt(structureId.replace("S", "").trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        public int getPhotoIdAsInt() {
            try {
                return Integer.parseInt(photoId.replace("P", "").trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    public PhotoStorageManager(Context context) {
        this.context = context;

        // Create base directory if it doesn't exist
        baseDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), BASE_DIRECTORY);
        if (!baseDirectory.exists()) {
            if (!baseDirectory.mkdirs()) {
                Log.e(TAG, "Failed to create base directory: " + baseDirectory.getAbsolutePath());

                // Fallback to app-specific directory
                baseDirectory = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), BASE_DIRECTORY);
                if (!baseDirectory.exists()) {
                    baseDirectory.mkdirs();
                }
            }
        }

        Log.d(TAG, "Storage initialized at: " + baseDirectory.getAbsolutePath());

        // Load existing photos
        refresh();
    }

    /**
     * Get or create structure-specific folder
     * @param structureId ID of the structure
     * @return File object representing the structure folder
     */
    private File getStructureFolder(int structureId) {
        File structureFolder = new File(baseDirectory, STRUCTURE_FOLDER_PREFIX + structureId);
        if (!structureFolder.exists()) {
            if (!structureFolder.mkdirs()) {
                Log.e(TAG, "Failed to create structure folder: " + structureFolder.getAbsolutePath());
                return baseDirectory; // Fallback to base folder
            }
        }
        return structureFolder;
    }

    /**
     * Save a photo to storage in the appropriate structure folder
     * @param photo Bitmap to save
     * @param structureId ID of the structure
     * @param photoId ID of the photo position
     * @return PhotoInfo object with file details, or null if save failed
     */
    public PhotoInfo savePhoto(Bitmap photo, int structureId, int photoId) {
        if (photo == null) {
            Log.e(TAG, "Cannot save null photo");
            return null;
        }

        try {
            // Get structure-specific folder
            File structureFolder = getStructureFolder(structureId);

            // Generate a unique filename
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = PHOTO_PREFIX + "s" + structureId + "_p" + photoId + "_" + timestamp + PHOTO_EXTENSION;
            File outputFile = new File(structureFolder, filename);

            // Save the bitmap to a file
            FileOutputStream fos = new FileOutputStream(outputFile);
            photo.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();

            // Create a new PhotoInfo object
            PhotoInfo newPhoto = new PhotoInfo(outputFile, "S" + structureId, "P" + photoId);

            // Add to the list of saved photos
            photoCache.add(newPhoto);

            Log.d(TAG, "Photo saved: " + outputFile.getAbsolutePath());
            return newPhoto;

        } catch (IOException e) {
            Log.e(TAG, "Error saving photo: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Delete a photo from storage
     * @param photoInfo PhotoInfo object of the photo to delete
     * @return true if deletion was successful
     */
    public boolean deletePhoto(PhotoInfo photoInfo) {
        if (photoInfo == null || photoInfo.getFile() == null || !photoInfo.getFile().exists()) {
            return false;
        }

        boolean deleted = photoInfo.getFile().delete();
        if (deleted) {
            photoCache.remove(photoInfo);

            // If this was the last photo in the structure folder, consider deleting the empty folder
            File parentFolder = photoInfo.getFile().getParentFile();
            if (parentFolder != null && parentFolder.isDirectory() &&
                    !parentFolder.equals(baseDirectory) &&
                    parentFolder.list() != null &&
                    parentFolder.list().length == 0) {
                parentFolder.delete(); // Delete empty structure folder
            }
        }
        return deleted;
    }

    /**
     * Refresh the cache of saved photos by scanning the storage
     */
    public void refresh() {
        photoCache.clear();

        if (baseDirectory == null || !baseDirectory.exists()) {
            return;
        }

        try {
            // First scan the base directory for old-style photos
            scanDirectoryForPhotos(baseDirectory, "");

            // Then scan each structure folder
            File[] structureFolders = baseDirectory.listFiles(new java.io.FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.isDirectory() && file.getName().startsWith(STRUCTURE_FOLDER_PREFIX);
                }
            });

            if (structureFolders != null) {
                for (File structureFolder : structureFolders) {
                    // Get structure ID from folder name
                    String folderName = structureFolder.getName();
                    String structurePrefix = folderName.startsWith(STRUCTURE_FOLDER_PREFIX) ? "" : STRUCTURE_FOLDER_PREFIX;

                    scanDirectoryForPhotos(structureFolder, structurePrefix);
                }
            }

            // Sort photos by date (newest first)
            Collections.sort(photoCache, new Comparator<PhotoInfo>() {
                @Override
                public int compare(PhotoInfo p1, PhotoInfo p2) {
                    return Long.compare(p2.getFile().lastModified(), p1.getFile().lastModified());
                }
            });

            Log.d(TAG, "Refreshed photo cache with " + photoCache.size() + " photos");

        } catch (Exception e) {
            Log.e(TAG, "Error refreshing photo cache: " + e.getMessage(), e);
        }
    }

    /**
     * Scan a directory for photos and add them to the cache
     * @param directory Directory to scan
     * @param structurePrefixOverride Override for structure ID prefix
     */
    private void scanDirectoryForPhotos(File directory, String structurePrefixOverride) {
        File[] files = directory.listFiles(new java.io.FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isFile() && file.getName().startsWith(PHOTO_PREFIX) &&
                        file.getName().endsWith(PHOTO_EXTENSION);
            }
        });

        if (files == null) {
            return;
        }

        for (File file : files) {
            // Parse the filename to extract structure and photo IDs
            String filename = file.getName();
            try {
                String[] parts = filename.replace(PHOTO_PREFIX, "").split("_");

                if (parts.length >= 2) {
                    String structureIdPart = parts[0];
                    String photoIdPart = parts[1];

                    String structureId = structureIdPart.startsWith("s") ?
                            structureIdPart.substring(1) : structureIdPart;
                    String photoId = photoIdPart.startsWith("p") ?
                            photoIdPart.substring(1) : photoIdPart;

                    // Apply structure prefix override if provided
                    String finalStructurePrefix = structurePrefixOverride.isEmpty() ? "S" : structurePrefixOverride;

                    PhotoInfo photoInfo = new PhotoInfo(file, finalStructurePrefix + structureId, "P" + photoId);
                    photoCache.add(photoInfo);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing filename: " + filename, e);
            }
        }
    }

    /**
     * Get all saved photos
     * @return List of PhotoInfo objects
     */
    public List<PhotoInfo> getSavedPhotos() {
        return new ArrayList<>(photoCache);
    }

    /**
     * Get photos for a specific structure
     * @param structureId ID of the structure
     * @return List of PhotoInfo objects for the given structure
     */
    public List<PhotoInfo> getPhotosForStructure(int structureId) {
        String structurePrefix = "S" + structureId;
        List<PhotoInfo> result = new ArrayList<>();

        for (PhotoInfo photo : photoCache) {
            if (photo.getStructureId().equals(structurePrefix)) {
                result.add(photo);
            }
        }

        // Sort by photo ID
        Collections.sort(result, new Comparator<PhotoInfo>() {
            @Override
            public int compare(PhotoInfo p1, PhotoInfo p2) {
                return p1.getPhotoIdAsInt() - p2.getPhotoIdAsInt();
            }
        });

        return result;
    }

    /**
     * Get a list of all structure IDs that have photos
     * @return List of structure IDs
     */
    public List<Integer> getStructureIdsWithPhotos() {
        List<Integer> structureIds = new ArrayList<>();

        for (PhotoInfo photo : photoCache) {
            int structureId = photo.getStructureIdAsInt();
            if (structureId > 0 && !structureIds.contains(structureId)) {
                structureIds.add(structureId);
            }
        }

        Collections.sort(structureIds);
        return structureIds;
    }

    /**
     * Get the base storage directory
     * @return The storage directory as a File
     */
    public File getStorageDirectory() {
        return baseDirectory;
    }
}