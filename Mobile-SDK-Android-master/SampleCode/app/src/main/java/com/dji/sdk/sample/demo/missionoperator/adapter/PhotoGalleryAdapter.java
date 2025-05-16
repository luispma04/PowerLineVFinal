package com.dji.sdk.sample.demo.missionoperator.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.demo.missionoperator.util.PhotoStorageManager;

import java.util.List;

/**
 * Adapter for displaying the saved photos in a RecyclerView
 */
public class PhotoGalleryAdapter extends RecyclerView.Adapter<PhotoGalleryAdapter.PhotoViewHolder> {

    private List<PhotoStorageManager.PhotoInfo> photoList;
    private Context context;
    private OnPhotoClickListener photoClickListener;

    public interface OnPhotoClickListener {
        void onPhotoClick(PhotoStorageManager.PhotoInfo photoInfo);
        void onDownloadClick(PhotoStorageManager.PhotoInfo photoInfo);
        void onDeleteClick(PhotoStorageManager.PhotoInfo photoInfo);
    }

    public PhotoGalleryAdapter(Context context, List<PhotoStorageManager.PhotoInfo> photoList, OnPhotoClickListener listener) {
        this.context = context;
        this.photoList = photoList;
        this.photoClickListener = listener;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_photo_gallery, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        PhotoStorageManager.PhotoInfo photoInfo = photoList.get(position);

        // Load thumbnail
        Bitmap thumbnail = BitmapFactory.decodeFile(photoInfo.getFile().getAbsolutePath());
        holder.photoImageView.setImageBitmap(thumbnail);

        // Set photo info
        holder.structureIdText.setText("Estrutura: " + photoInfo.getStructureId());
        holder.photoIdText.setText("Foto: " + photoInfo.getPhotoId());
        holder.timestampText.setText(photoInfo.getTimestamp());

        // Set click listeners
        holder.photoImageView.setOnClickListener(v -> {
            if (photoClickListener != null) {
                photoClickListener.onPhotoClick(photoInfo);
            }
        });

        holder.downloadButton.setOnClickListener(v -> {
            if (photoClickListener != null) {
                photoClickListener.onDownloadClick(photoInfo);
            }
        });

        holder.deleteButton.setOnClickListener(v -> {
            if (photoClickListener != null) {
                photoClickListener.onDeleteClick(photoInfo);
            }
        });
    }

    @Override
    public int getItemCount() {
        return photoList != null ? photoList.size() : 0;
    }

    /**
     * Update the photo list and refresh the adapter
     * @param photos The new list of photos
     */
    public void updatePhotoList(List<PhotoStorageManager.PhotoInfo> photos) {
        this.photoList = photos;
        notifyDataSetChanged();
    }

    public static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView photoImageView;
        TextView structureIdText;
        TextView photoIdText;
        TextView timestampText;
        Button downloadButton;
        Button deleteButton;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            photoImageView = itemView.findViewById(R.id.img_photo_thumbnail);
            structureIdText = itemView.findViewById(R.id.text_structure_id);
            photoIdText = itemView.findViewById(R.id.text_photo_id);
            timestampText = itemView.findViewById(R.id.text_timestamp);
            downloadButton = itemView.findViewById(R.id.btn_download_photo);
            deleteButton = itemView.findViewById(R.id.btn_delete_photo);
        }
    }
}